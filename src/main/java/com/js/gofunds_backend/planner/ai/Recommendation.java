package com.js.gofunds_backend.planner.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.js.gofunds_backend.domain.enums.RiskProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The exact JSON contract asked of the model.
 *
 * <p>Every level carries {@code @JsonIgnoreProperties(ignoreUnknown = true)} so a
 * model that invents an extra field is tolerated instead of failing the request.
 *
 * <p>Nothing here is trusted on arrival. {@link RecommendationValidator} re-checks
 * it against the catalogue that was sent in the prompt before a single value is
 * used or stored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Recommendation(
		RiskProfile riskProfile,
		AssetAllocation assetAllocation,
		List<SelectedFund> selectedFunds,
		BigDecimal blendedReturnRate,
		String explanation) {

	/** Percentage split across the three main categories. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AssetAllocation(
			Integer equity,
			Integer debt,
			Integer hybrid) {
	}

	/** One recommended fund and the share of the monthly SIP assigned to it. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record SelectedFund(
			UUID fundId,
			String fundName,
			BigDecimal allocationPercentage) {
	}
}
