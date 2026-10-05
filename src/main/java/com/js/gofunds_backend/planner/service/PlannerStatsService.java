package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.entity.InvestmentPlan;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.repository.InvestmentPlanRepository;
import com.js.gofunds_backend.planner.dto.DashboardStatsResponse;
import com.js.gofunds_backend.planner.dto.DashboardStatsResponse.GoalBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Aggregates a user's plans for the dashboard.
 *
 * <p>Computed from the user's own plan list rather than with aggregate SQL
 * queries: a single user has a handful of plans, so one indexed fetch
 * ({@code idx_investment_plans_user_id}) is cheaper than the extra round trips a
 * {@code GROUP BY} per metric would need. Worth revisiting only if plans ever
 * become unbounded per user.
 */
@Service
@RequiredArgsConstructor
public class PlannerStatsService {

	private final InvestmentPlanRepository investmentPlanRepository;

	@Transactional(readOnly = true)
	public DashboardStatsResponse statsFor(User user) {
		List<InvestmentPlan> plans = investmentPlanRepository
				.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId());

		if (plans.isEmpty()) {
			return new DashboardStatsResponse(0, 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
					BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO, List.of());
		}

		int activePlans = (int) plans.stream()
				.filter(plan -> plan.getStatus() == PlanStatus.ACTIVE)
				.count();

		BigDecimal monthlyTotal = sum(plans, plan -> plan.getMonthlyAmount());
		BigDecimal returnsTotal = sum(plans, plan -> plan.getProjectedReturns());

		// Averaged over only the plans that actually carry a projection.
		long ratedPlans = plans.stream().filter(plan -> plan.getProjectedReturns() != null).count();
		BigDecimal averageReturns = ratedPlans == 0
				? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
				: returnsTotal.divide(BigDecimal.valueOf(ratedPlans), 2, RoundingMode.HALF_UP);

		return new DashboardStatsResponse(
				plans.size(),
				activePlans,
				monthlyTotal,
				returnsTotal,
				averageReturns,
				byGoal(plans));
	}

	private List<GoalBreakdown> byGoal(List<InvestmentPlan> plans) {
		Map<InvestmentGoal, List<InvestmentPlan>> grouped = new EnumMap<>(InvestmentGoal.class);
		for (InvestmentPlan plan : plans) {
			grouped.computeIfAbsent(plan.getGoal(), goal -> new ArrayList<>()).add(plan);
		}

		List<GoalBreakdown> breakdown = new ArrayList<>(grouped.size());
		grouped.forEach((goal, group) -> breakdown.add(new GoalBreakdown(
				goal.name(),
				group.size(),
				sum(group, plan -> plan.getMonthlyAmount()))));

		breakdown.sort(Comparator.comparingInt(GoalBreakdown::planCount).reversed()
				.thenComparing(GoalBreakdown::goal));
		return List.copyOf(breakdown);
	}

	private BigDecimal sum(List<InvestmentPlan> plans, Function<InvestmentPlan, BigDecimal> amount) {
		BigDecimal total = plans.stream()
				.map(amount)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return total.setScale(2, RoundingMode.HALF_UP);
	}
}