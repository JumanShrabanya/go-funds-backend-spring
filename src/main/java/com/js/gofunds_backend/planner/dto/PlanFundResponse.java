package com.js.gofunds_backend.planner.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One fund inside a generated plan, with the money assigned to it. */
public record PlanFundResponse(
		UUID fundId,
		String schemeCode,
		String schemeName,
		String fundHouse,
		String mainCategory,
		String subCategory,
		String riskLevel,
		BigDecimal allocationPercentage,
		BigDecimal monthlyAmount) {
}