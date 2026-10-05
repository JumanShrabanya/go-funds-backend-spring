package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.planner.ai.GeminiClient;
import com.js.gofunds_backend.planner.ai.Recommendation;
import com.js.gofunds_backend.planner.ai.RecommendationParser;
import com.js.gofunds_backend.planner.ai.RecommendationValidator;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * Produces a validated {@link Recommendation} for a generate request.
 *
 * <p>The pipeline is linear and each stage is a separate testable class:
 *
 * <ol>
 *   <li>{@link FundCatalogService} shortlists the funds this investor may hold
 *   <li>the prompts are rendered here
 *   <li>{@link GeminiClient} sends them
 *   <li>{@link RecommendationParser} reads the reply as JSON
 *   <li>{@link RecommendationValidator} checks and repairs it
 * </ol>
 *
 * <p>Projection maths is deliberately not in this pipeline. The prompt never asks
 * the model for a projected value; {@link InvestmentCalculatorService} computes
 * every number the user sees, and {@link PlannerService} persists it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private static final String SYSTEM_PROMPT = """
			You are a mutual fund allocation advisor for an Indian retail investor.

			Respond with a single JSON object and nothing else - no prose, no markdown \
			fences, no commentary. Use exactly this shape:

			{
			  "riskProfile": "CONSERVATIVE" | "MODERATE" | "AGGRESSIVE",
			  "assetAllocation": { "equity": <int>, "debt": <int>, "hybrid": <int> },
			  "selectedFunds": [
			    { "fundId": "<uuid>", "fundName": "<name>", "allocationPercentage": <number> }
			  ],
			  "blendedReturnRate": <number>,
			  "explanation": "<2-4 sentences, plain text, no markdown>"
			}

			Hard rules:
			1. assetAllocation values are whole percentages and MUST total exactly 100.
			2. Choose between 3 and 6 funds. allocationPercentage values MUST total exactly 100.
			3. fundId MUST be copied verbatim from the catalogue you are given. Never invent,
			   guess or modify an id. fundName must match the catalogue name for that id.
			4. Every fundId MUST appear at most once.
			5. Never recommend the same fund house more than twice.
			6. Only recommend funds whose riskLevel is compatible with the investor's
			   risk profile. Prefer funds that support SIP.
			7. blendedReturnRate is an expected annual return percentage as a number, for
			   example 12.5 for 12.5%. It must be a realistic, non-negative figure. Do not
			   compute returns, growth or future value - the application does that itself.
			8. Match the asset allocation to the investment goal and horizon. Money needed
			   within 3 years must not sit in high-volatility equity funds.
			""";

	private final FundCatalogService fundCatalogService;
	private final GeminiClient geminiClient;
	private final RecommendationParser parser;
	private final RecommendationValidator validator;

	/**
	 * @throws ApiException 409 if the fund catalogue is empty, 503 if Gemini cannot
	 *         serve the request, 502 if the model's answer is unusable
	 */
	public Recommendation recommend(InvestmentPlanRequest request) {
		RiskProfile profile = request.riskProfile() == null
				? RiskProfile.MODERATE
				: request.riskProfile();

		List<Fund> catalogue = fundCatalogService.eligibleFunds(profile, request.investmentHorizon());
		if (catalogue.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT,
					"No funds are available to build a plan from. The fund catalogue has not been synced.");
		}

		String completion = geminiClient.complete(SYSTEM_PROMPT, userPrompt(request, profile, catalogue));
		Recommendation validated = validator.validate(parser.parse(completion), catalogue);

		log.debug("Recommendation accepted with {} funds for {}",
				validated.selectedFunds().size(), profile);
		return validated;
	}

	private String userPrompt(InvestmentPlanRequest request, RiskProfile profile, List<Fund> catalogue) {
		StringJoiner rows = new StringJoiner("\n");
		for (Fund fund : catalogue) {
			rows.add("  - fundId: " + fund.getId()
					+ " | name: " + fund.getSchemeName()
					+ " | house: " + nvl(fund.getFundHouse())
					+ " | category: " + fund.getMainCategory() + "/" + fund.getSubCategory()
					+ " | risk: " + fund.getRiskLevel()
					+ " | nav: " + fund.getCurrentNav()
					+ " | 1y return: " + nvl(fund.getReturnRate1Year())
					+ " | sip: " + fund.isSupportsSip());
		}

		return """
				INVESTOR PROFILE
				  monthly investment: %s INR
				  goal: %s
				  horizon: %s
				  risk profile: %s

				ELIGIBLE FUNDS (%d of the schemes available; choose only from this list)
				%s

				A null 1y return means no return history is on file for that scheme. Do not treat it as zero.

				Produce the JSON recommendation now."""
				.formatted(
						request.monthlyInvestment(),
						request.goal(),
						request.investmentHorizon(),
						profile,
						catalogue.size(),
						rows);
	}

	private static String nvl(Object value) {
		return value == null ? "n/a" : String.valueOf(value);
	}
}
