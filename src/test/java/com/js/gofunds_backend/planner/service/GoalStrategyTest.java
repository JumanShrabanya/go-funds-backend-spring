package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.planner.service.GoalStrategy.PortfolioPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalStrategyTest {

	private final GoalStrategy strategy = new GoalStrategy();

	private static void assertTotalsTo100(PortfolioPolicy policy) {
		assertEquals(100, policy.equityPercent() + policy.debtPercent() + policy.hybridPercent(),
				"the target allocation must always total 100");
	}

	@Test
	void usesTheGoalBaseAllocationForAModerateLongHorizon() {
		PortfolioPolicy policy = strategy.policyFor(
				InvestmentGoal.WEALTH_CREATION, RiskProfile.MODERATE, InvestmentHorizon.MORE_THAN_10_YEARS);

		assertEquals(70, policy.equityPercent());
		assertEquals(10, policy.debtPercent());
		assertEquals(20, policy.hybridPercent());
		assertTotalsTo100(policy);
	}

	@Test
	void tiltsTowardsEquityForAnAggressiveInvestor() {
		PortfolioPolicy policy = strategy.policyFor(
				InvestmentGoal.WEALTH_CREATION, RiskProfile.AGGRESSIVE, InvestmentHorizon.MORE_THAN_10_YEARS);

		assertTrue(policy.equityPercent() > 70, "an aggressive appetite must raise the equity target");
		assertEquals(0, policy.debtPercent(), "aggressive plans hold no pure debt target");
		assertTotalsTo100(policy);
	}

	@Test
	void tiltsTowardsDebtForAConservativeInvestor() {
		PortfolioPolicy policy = strategy.policyFor(
				InvestmentGoal.WEALTH_CREATION, RiskProfile.CONSERVATIVE, InvestmentHorizon.MORE_THAN_10_YEARS);

		assertTrue(policy.equityPercent() < 70, "a conservative appetite must lower the equity target");
		assertEquals(55, policy.equityPercent());
		assertEquals(25, policy.debtPercent());
		assertTotalsTo100(policy);
	}

	@Test
	void capsEquityForAShortHorizonRegardlessOfAppetite() {
		PortfolioPolicy policy = strategy.policyFor(
				InvestmentGoal.WEALTH_CREATION, RiskProfile.AGGRESSIVE, InvestmentHorizon.LESS_THAN_3_YEARS);

		assertTrue(policy.equityPercent() <= 30,
				"near-term money must not sit in volatile equity, even for an aggressive investor");
		assertTotalsTo100(policy);
	}

	@Test
	void keepsDebtHeavyForAShortHomePurchase() {
		PortfolioPolicy policy = strategy.policyFor(
				InvestmentGoal.HOME_PURCHASE, RiskProfile.MODERATE, InvestmentHorizon.LESS_THAN_3_YEARS);

		assertTrue(policy.debtPercent() >= policy.equityPercent(),
				"a near-term house deposit should lean on debt, not equity");
		assertTotalsTo100(policy);
	}

	@Test
	void requiresElssForTaxSavingAndFrontLoadsIt() {
		PortfolioPolicy policy = strategy.policyFor(
				InvestmentGoal.TAX_SAVING, RiskProfile.MODERATE, InvestmentHorizon.FIVE_TO_TEN_YEARS);

		assertTrue(policy.requiredSubCategories().contains(FundSubCategory.ELSS));
		assertEquals(FundSubCategory.ELSS, policy.prioritizedSubCategories().get(0));
	}

	@Test
	void everyGoalProducesAPolicyThatTotalsTo100() {
		for (InvestmentGoal goal : InvestmentGoal.values()) {
			for (RiskProfile profile : RiskProfile.values()) {
				for (InvestmentHorizon horizon : InvestmentHorizon.values()) {
					assertTotalsTo100(strategy.policyFor(goal, profile, horizon));
				}
			}
		}
	}
}
