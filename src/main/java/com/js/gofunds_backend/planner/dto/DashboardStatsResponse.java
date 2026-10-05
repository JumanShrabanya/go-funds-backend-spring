package com.js.gofunds_backend.planner.dto;

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
public record DashboardStatsResponse(
		int totalPlans,
		int activePlans,
		BigDecimal totalMonthlyInvestment,
		BigDecimal totalProjectedReturns,
		BigDecimal averageProjectedReturns,
		List<GoalBreakdown> byGoal) {

	/** Per-goal totals, ordered by plan count descending, then goal name. */
	public record GoalBreakdown(
			String goal,
			int planCount,
			BigDecimal monthlyInvestment) {
	}
}
