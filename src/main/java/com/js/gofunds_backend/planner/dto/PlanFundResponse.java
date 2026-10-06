package com.js.gofunds_backend.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** One fund inside a generated plan, with the money assigned to it. */
@Schema(description = "A single recommended fund and its slice of the monthly amount")
public record PlanFundResponse(
		UUID fundId,
		String schemeCode,
		String schemeName,
		String fundHouse,
		String mainCategory,
		String subCategory,
		String riskLevel,

		@Schema(description = "Share of the monthly amount, 0-100. Allocations always sum to 100.",
				example = "35.00")
		BigDecimal allocationPercentage,

		@Schema(description = "monthlyInvestment x allocationPercentage, in rupees",
				example = "8750.00")
		BigDecimal monthlyAmount) {
}
