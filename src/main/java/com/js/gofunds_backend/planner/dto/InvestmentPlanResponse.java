package com.js.gofunds_backend.planner.dto;

import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.enums.RiskProfile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A plan as returned to the client.
 *
 * <p>{@code recommendedFunds} is a typed {@link PlanFundResponse} list rather than
 * the raw JSONB column, so clients never depend on the persistence shape.
 *
 * <p>{@code totalInvested} and {@code projectedValue} are derived on read and are
 * not stored, so the projection maths can change without rewriting existing rows.
 *
 * @see PlannerService
 */
public record InvestmentPlanResponse(
		UUID id,
		InvestmentGoal goal,
		InvestmentHorizon investmentHorizon,
		BigDecimal monthlyAmount,
		RiskProfile riskProfile,
		Map<String, Object> allocationBreakdown,
		List<PlanFundResponse> recommendedFunds,
		BigDecimal totalInvested,
		BigDecimal projectedValue,
		BigDecimal projectedReturns,
		String explanation,
		PlanStatus status,
		LocalDateTime createdAt) {
}
