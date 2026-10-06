package com.js.gofunds_backend.planner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate figures for the dashboard.
 *
 * <p>Every value covers the user's non-deleted plans only. All money figures are
 * in rupees.
 *
 * <p>Note {@code averageProjectedReturns} is an average rupee amount per plan, not
 * a percentage: it is the total projected returns divided by the number of plans
 * that carry a projection.
 */
@Schema(description = "Per-account totals across all non-deleted plans")
public record DashboardStatsResponse(
		@Schema(example = "4") int totalPlans,

		@Schema(description = "Plans still in `ACTIVE` status", example = "3")
		int activePlans,

		@Schema(description = "Sum of every plan's monthly amount, in rupees", example = "80000.00")
		BigDecimal totalMonthlyInvestment,

		@Schema(description = "Sum of projected returns, in rupees", example = "2340000.00")
		BigDecimal totalProjectedReturns,

		@Schema(description = "Rupees per plan, not a percentage. Plans without a projection are "
				+ "excluded from the average.", example = "780000.00")
		BigDecimal averageProjectedReturns,

		List<GoalBreakdown> byGoal) {

	/** Per-goal totals, ordered by plan count descending, then goal name. */
	@Schema(description = "Totals grouped by goal, largest group first")
	public record GoalBreakdown(
			String goal,

			@Schema(example = "2") int planCount,

			@Schema(description = "Sum of the group's monthly amounts, in rupees", example = "45000.00")
			BigDecimal monthlyInvestment) {
	}
}
