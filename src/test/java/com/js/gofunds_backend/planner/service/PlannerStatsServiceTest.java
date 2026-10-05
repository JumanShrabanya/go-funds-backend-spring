package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.entity.InvestmentPlan;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.repository.InvestmentPlanRepository;
import com.js.gofunds_backend.planner.dto.DashboardStatsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerStatsServiceTest {

	@Mock
	private InvestmentPlanRepository investmentPlanRepository;

	@InjectMocks
	private PlannerStatsService statsService;

	private final User user = new User();

	private static InvestmentPlan plan(InvestmentGoal goal, String monthly, String returns,
			PlanStatus status) {

		InvestmentPlan plan = new InvestmentPlan();
		plan.setId(UUID.randomUUID());
		plan.setGoal(goal);
		plan.setMonthlyAmount(new BigDecimal(monthly));
		plan.setProjectedReturns(new BigDecimal(returns));
		plan.setStatus(status);
		return plan;
	}

	private void givenPlans(InvestmentPlan... plans) {
		when(investmentPlanRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(any()))
				.thenReturn(List.of(plans));
	}

	@Test
	void returnsZeroedStatsForANewUser() {
		givenPlans();

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals(0, stats.totalPlans());
		assertEquals(0, stats.activePlans());
		assertEquals(0, stats.totalMonthlyInvestment().compareTo(BigDecimal.ZERO));
		assertTrue(stats.byGoal().isEmpty());
	}

	@Test
	void sumsMonthlyInvestmentAndReturnsAcrossPlans() {
		givenPlans(
				plan(InvestmentGoal.WEALTH_CREATION, "10000", "50000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.RETIREMENT, "20000", "120000", PlanStatus.ACTIVE));

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals(2, stats.totalPlans());
		assertEquals(2, stats.activePlans());
		assertEquals(0, stats.totalMonthlyInvestment().compareTo(new BigDecimal("30000")));
		assertEquals(0, stats.totalProjectedReturns().compareTo(new BigDecimal("170000")));
	}

	@Test
	void countsOnlyActivePlans() {
		givenPlans(
				plan(InvestmentGoal.WEALTH_CREATION, "10000", "50000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.RETIREMENT, "20000", "120000", PlanStatus.ARCHIVED),
				plan(InvestmentGoal.CHILD_EDUCATION, "5000", "10000", PlanStatus.COMPLETED));

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals(3, stats.totalPlans());
		assertEquals(1, stats.activePlans());
	}

	@Test
	void averagesReturnsAcrossPlansThatHaveAProjection() {
		givenPlans(
				plan(InvestmentGoal.WEALTH_CREATION, "10000", "50000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.RETIREMENT, "20000", "120000", PlanStatus.ACTIVE));

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals(0, stats.averageProjectedReturns().compareTo(new BigDecimal("85000.00")));
	}

	@Test
	void ignoresPlansWithNoProjectionWhenAveraging() {
		InvestmentPlan withoutProjection = plan(InvestmentGoal.WEALTH_CREATION, "10000", "0", PlanStatus.ACTIVE);
		withoutProjection.setProjectedReturns(null);

		givenPlans(plan(InvestmentGoal.RETIREMENT, "20000", "120000", PlanStatus.ACTIVE), withoutProjection);

		DashboardStatsResponse stats = statsService.statsFor(user);

		// Averaged over the one plan that has a figure, not over both.
		assertEquals(0, stats.averageProjectedReturns().compareTo(new BigDecimal("120000.00")));
	}

	@Test
	void groupsByGoalWithMonthlyTotals() {
		givenPlans(
				plan(InvestmentGoal.WEALTH_CREATION, "10000", "50000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.WEALTH_CREATION, "5000", "25000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.RETIREMENT, "20000", "120000", PlanStatus.ACTIVE));

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals(2, stats.byGoal().size());
		assertEquals("WEALTH_CREATION", stats.byGoal().get(0).goal());
		assertEquals(2, stats.byGoal().get(0).planCount());
		assertEquals(0, stats.byGoal().get(0).monthlyInvestment().compareTo(new BigDecimal("15000")));
		assertEquals("RETIREMENT", stats.byGoal().get(1).goal());
	}

	@Test
	void ordersGoalBreakdownByCountDescending() {
		givenPlans(
				plan(InvestmentGoal.RETIREMENT, "1000", "5000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.WEALTH_CREATION, "1000", "5000", PlanStatus.ACTIVE),
				plan(InvestmentGoal.WEALTH_CREATION, "1000", "5000", PlanStatus.ACTIVE));

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals("WEALTH_CREATION", stats.byGoal().get(0).goal());
		assertEquals("RETIREMENT", stats.byGoal().get(1).goal());
	}

	@Test
	void totalsAreAlwaysScaledToTwoDecimals() {
		givenPlans(plan(InvestmentGoal.WEALTH_CREATION, "1000.555", "5000", PlanStatus.ACTIVE));

		DashboardStatsResponse stats = statsService.statsFor(user);

		assertEquals(2, stats.totalMonthlyInvestment().scale());
		assertEquals(2, stats.totalProjectedReturns().scale());
	}
}