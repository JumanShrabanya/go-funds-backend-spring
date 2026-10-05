package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.entity.InvestmentPlan;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.repository.FundRepository;
import com.js.gofunds_backend.domain.repository.InvestmentPlanRepository;
import com.js.gofunds_backend.planner.ai.Recommendation;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import com.js.gofunds_backend.planner.dto.PlanFundResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the plans a user has: generating one and reading, listing and deleting
 * them back.
 *
 * <p>Split from {@link RecommendationService} so the AI call and the persistence
 * stay separately testable, and so a read never depends on Gemini being reachable.
 *
 * <p>Only {@code projectedReturns} is persisted. {@code totalInvested} and
 * {@code projectedValue} are recomputed from the monthly amount and horizon on
 * every read, which keeps the row minimal and means a change to the projection
 * maths applies to existing plans too, not only to new ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

	private final RecommendationService recommendationService;
	private final InvestmentCalculatorService calculatorService;
	private final InvestmentPlanRepository investmentPlanRepository;
	private final FundRepository fundRepository;

	/**
	 * Generates a recommendation, projects its returns and stores it.
	 *
	 * @throws ApiException 409 empty catalogue, 503 AI unavailable, 502 unusable
	 *         model answer
	 */
	@Transactional
	public InvestmentPlanResponse generate(User user, InvestmentPlanRequest request) {
		Recommendation recommendation = recommendationService.recommend(request);
		List<PlanFundResponse> funds = withMonthlyAmounts(recommendation, request);

		BigDecimal projectedReturns = calculatorService.projectedReturns(
				request.monthlyInvestment(),
				recommendation.blendedReturnRate(),
				request.investmentHorizon());

		InvestmentPlan plan = new InvestmentPlan();
		plan.setUser(user);
		plan.setGoal(request.goal());
		plan.setInvestmentHorizon(request.investmentHorizon());
		plan.setMonthlyAmount(request.monthlyInvestment());
		plan.setRiskProfile(recommendation.riskProfile());
		plan.setAllocationBreakdown(allocationBreakdown(recommendation));
		plan.setRecommendedFunds(PlanFundMapper.toStored(funds));
		plan.setProjectedReturns(projectedReturns);
		plan.setExplanation(recommendation.explanation());
		plan.setStatus(PlanStatus.ACTIVE);

		InvestmentPlan saved = investmentPlanRepository.save(plan);
		log.info("Generated plan {} for user {} with {} funds", saved.getId(), user.getId(), funds.size());

		return toResponse(saved, projectedValue(saved));
	}

	@Transactional(readOnly = true)
	public List<InvestmentPlanResponse> findAll(User user) {
		return investmentPlanRepository
				.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
				.stream()
				.map(plan -> toResponse(plan, projectedValue(plan)))
				.toList();
	}

	@Transactional(readOnly = true)
	public InvestmentPlanResponse findById(User user, UUID planId) {
		InvestmentPlan plan = requirePlan(user, planId);
		return toResponse(plan, projectedValue(plan));
	}

	/** Soft delete: stamps {@code deleted_at} so the row is hidden but retained. */
	@Transactional
	public void delete(User user, UUID planId) {
		InvestmentPlan plan = requirePlan(user, planId);
		plan.setDeletedAt(LocalDateTime.now());
		investmentPlanRepository.save(plan);
		log.info("Soft deleted plan {} for user {}", planId, user.getId());
	}

	/** The lookup is scoped to the user, so another user's id yields 404. */
	private InvestmentPlan requirePlan(User user, UUID planId) {
		return investmentPlanRepository
				.findByIdAndUserIdAndDeletedAtIsNull(planId, user.getId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"No investment plan found for id " + planId));
	}

	/**
	 * Resolves the catalogue details the model only gave ids for, and works out the
	 * monthly amount per fund.
	 */
	private List<PlanFundResponse> withMonthlyAmounts(Recommendation recommendation,
			InvestmentPlanRequest request) {

		List<UUID> ids = recommendation.selectedFunds().stream()
				.map(Recommendation.SelectedFund::fundId)
				.toList();

		Map<UUID, Fund> catalogue = new HashMap<>();
		fundRepository.findAllById(ids).forEach(fund -> catalogue.put(fund.getId(), fund));

		List<PlanFundResponse> funds = new ArrayList<>(ids.size());
		for (Recommendation.SelectedFund selected : recommendation.selectedFunds()) {
			Fund fund = catalogue.get(selected.fundId());
			if (fund == null) {
				// Validated against the prompt catalogue, so this only happens if the
				// row was deleted mid-request. Fall back to the model's own name.
				funds.add(new PlanFundResponse(
						selected.fundId(), null, selected.fundName(), null, null, null, null,
						selected.allocationPercentage(), monthlyAmount(request, selected)));
				continue;
			}

			funds.add(new PlanFundResponse(
					fund.getId(),
					fund.getSchemeCode(),
					fund.getSchemeName(),
					fund.getFundHouse(),
					fund.getMainCategory().name(),
					fund.getSubCategory().name(),
					fund.getRiskLevel().name(),
					selected.allocationPercentage(),
					monthlyAmount(request, selected)));
		}
		return funds;
	}

	private BigDecimal monthlyAmount(InvestmentPlanRequest request, Recommendation.SelectedFund selected) {
		return request.monthlyInvestment()
				.multiply(selected.allocationPercentage())
				.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
	}

	/** The category split plus the blended rate, as stored in the JSONB column. */
	private Map<String, Object> allocationBreakdown(Recommendation recommendation) {
		Map<String, Object> breakdown = new HashMap<>();
		breakdown.put("equity", recommendation.assetAllocation().equity());
		breakdown.put("debt", recommendation.assetAllocation().debt());
		breakdown.put("hybrid", recommendation.assetAllocation().hybrid());
		breakdown.put("blendedReturnRate", recommendation.blendedReturnRate());
		return breakdown;
	}

	/** What the plan is projected to be worth: everything paid in, plus returns. */
	private BigDecimal projectedValue(InvestmentPlan plan) {
		BigDecimal invested = calculatorService
				.totalInvested(plan.getMonthlyAmount(), plan.getInvestmentHorizon());
		return plan.getProjectedReturns() == null
				? invested
				: invested.add(plan.getProjectedReturns()).setScale(2, RoundingMode.HALF_UP);
	}

	private InvestmentPlanResponse toResponse(InvestmentPlan plan, BigDecimal projectedValue) {
		return new InvestmentPlanResponse(
				plan.getId(),
				plan.getGoal(),
				plan.getInvestmentHorizon(),
				plan.getMonthlyAmount(),
				plan.getRiskProfile(),
				plan.getAllocationBreakdown(),
				PlanFundMapper.toPlanFunds(plan.getRecommendedFunds()),
				calculatorService.totalInvested(plan.getMonthlyAmount(), plan.getInvestmentHorizon()),
				projectedValue,
				plan.getProjectedReturns(),
				plan.getExplanation(),
				plan.getStatus(),
				plan.getCreatedAt());
	}
}
