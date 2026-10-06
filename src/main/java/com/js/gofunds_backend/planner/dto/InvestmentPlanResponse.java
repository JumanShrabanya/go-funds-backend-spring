package com.js.gofunds_backend.planner.dto;

import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "A generated investment plan")
public record InvestmentPlanResponse(
		UUID id,
		InvestmentGoal goal,
		InvestmentHorizon investmentHorizon,

		@Schema(description = "The monthly amount this plan was built around, in rupees",
				example = "25000.00")
		BigDecimal monthlyAmount,

		RiskProfile riskProfile,

		@Schema(description = "Asset-class split plus the expected blended annual return, e.g. "
				+ "`equity`, `debt`, `hybrid`, `blendedReturnRate`",
				example = "{\"equity\": 70.0, \"debt\": 20.0, \"hybrid\": 10.0, "
						+ "\"blendedReturnRate\": 11.5}")
		Map<String, Object> allocationBreakdown,

		List<PlanFundResponse> recommendedFunds,

		@Schema(description = "monthlyAmount x months in the horizon. Recomputed on read, not stored.",
				example = "1500000.00")
		BigDecimal totalInvested,

		@Schema(description = "Future value of `totalInvested` at the blended rate. Recomputed on read.",
				example = "2280000.00")
		BigDecimal projectedValue,

		@Schema(description = "projectedValue - totalInvested, in rupees", example = "780000.00")
		BigDecimal projectedReturns,

		@Schema(description = "Why Gemini picked this mix, in plain English")
		String explanation,

		PlanStatus status,
		LocalDateTime createdAt) {
}
