package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the questionnaire into a portfolio policy: which fund sub-categories a
 * goal actually wants, and the asset split that goal should aim for.
 *
 * <p>The goal is the strongest signal in the questionnaire and used to be
 * ignored when the shortlist was built, so a tax-saving request and a wealth
 * request were handed the same pre-alpha-sorted funds. This class is the single
 * place that maps goal to relevant sleeves and goal to a target allocation, so
 * the shortlist and the prompt reason from one policy instead of drifting apart.
 *
 * <p>Pure functions with no dependencies, so the mapping is easy to unit test.
 */
@Component
public class GoalStrategy {

	/**
	 * Sub-categories a goal favours, in descending priority. Sleeves absent from
	 * the catalogue are simply skipped, and the shortlist falls back to the other
	 * sleeves only when these cannot fill the budget.
	 */
	private static final Map<InvestmentGoal, List<FundSubCategory>> PREFERRED =
			new EnumMap<>(Map.of(
					InvestmentGoal.WEALTH_CREATION, List.of(
							FundSubCategory.LARGE_CAP, FundSubCategory.MID_CAP, FundSubCategory.INDEX,
							FundSubCategory.BALANCED, FundSubCategory.AGGRESSIVE_HYBRID,
							FundSubCategory.SMALL_CAP, FundSubCategory.ELSS),
					InvestmentGoal.TAX_SAVING, List.of(
							FundSubCategory.ELSS, FundSubCategory.LARGE_CAP, FundSubCategory.MID_CAP,
							FundSubCategory.BALANCED, FundSubCategory.SHORT_DURATION,
							FundSubCategory.CORPORATE_BOND, FundSubCategory.GOVT_BOND),
					InvestmentGoal.RETIREMENT, List.of(
							FundSubCategory.LARGE_CAP, FundSubCategory.INDEX, FundSubCategory.BALANCED,
							FundSubCategory.MID_CAP, FundSubCategory.AGGRESSIVE_HYBRID,
							FundSubCategory.SMALL_CAP, FundSubCategory.GOVT_BOND,
							FundSubCategory.CORPORATE_BOND),
					InvestmentGoal.CHILD_EDUCATION, List.of(
							FundSubCategory.LARGE_CAP, FundSubCategory.MID_CAP, FundSubCategory.BALANCED,
							FundSubCategory.INDEX, FundSubCategory.ELSS, FundSubCategory.SMALL_CAP),
					InvestmentGoal.HOME_PURCHASE, List.of(
							FundSubCategory.SHORT_DURATION, FundSubCategory.CORPORATE_BOND,
							FundSubCategory.GOVT_BOND, FundSubCategory.BALANCED,
							FundSubCategory.LARGE_CAP, FundSubCategory.LIQUID)));

	/**
	 * Sleeves a goal must show when they exist: a tax-saving plan that omits ELSS
	 * is not a tax-saving plan. The preferred list already front-loads them; this
	 * set makes the requirement explicit rather than an accident of ordering.
	 */
	private static final Map<InvestmentGoal, Set<FundSubCategory>> REQUIRED =
			new EnumMap<>(Map.of(
					InvestmentGoal.TAX_SAVING, Set.of(FundSubCategory.ELSS)));

	/** Base equity/debt/hybrid target per goal, before risk and horizon adjust it. */
	private static final Map<InvestmentGoal, int[]> BASE_ALLOCATION =
			new EnumMap<>(Map.of(
					InvestmentGoal.WEALTH_CREATION, new int[] {70, 10, 20},
					InvestmentGoal.TAX_SAVING, new int[] {65, 20, 15},
					InvestmentGoal.RETIREMENT, new int[] {60, 20, 20},
					InvestmentGoal.CHILD_EDUCATION, new int[] {60, 15, 25},
					InvestmentGoal.HOME_PURCHASE, new int[] {30, 50, 20}));

	private static final int[] DEFAULT_ALLOCATION = {60, 20, 20};

	public PortfolioPolicy policyFor(InvestmentGoal goal, RiskProfile profile, InvestmentHorizon horizon) {
		List<FundSubCategory> preferred = PREFERRED.getOrDefault(goal, List.of());
		Set<FundSubCategory> required = REQUIRED.getOrDefault(goal, Set.of());
		int[] allocation = adjust(BASE_ALLOCATION.getOrDefault(goal, DEFAULT_ALLOCATION), profile, horizon);
		return new PortfolioPolicy(preferred, required, allocation[0], allocation[1], allocation[2]);
	}

	/**
	 * Appetite tilts the equity/debt mix by 15 points and a short horizon caps
	 * equity outright, which is why the two are applied in that order: the cap
	 * overrides even an aggressive request to keep near-term money out of volatile
	 * equity. Points only ever move between equity and debt, so the split stays at
	 * 100 by construction.
	 */
	private int[] adjust(int[] base, RiskProfile profile, InvestmentHorizon horizon) {
		int equity = base[0];
		int debt = base[1];
		int hybrid = base[2];

		if (profile == RiskProfile.CONSERVATIVE) {
			equity -= 15;
			debt += 15;
		} else if (profile == RiskProfile.AGGRESSIVE) {
			equity += 15;
			debt -= 15;
		}

		int equityCap = switch (horizon) {
			case LESS_THAN_3_YEARS -> 30;
			case THREE_TO_FIVE_YEARS -> 50;
			case FIVE_TO_TEN_YEARS, MORE_THAN_10_YEARS -> 100;
		};
		if (equity > equityCap) {
			debt += equity - equityCap;
			equity = equityCap;
		}

		equity = Math.max(0, equity);
		debt = Math.max(0, debt);
		hybrid = Math.max(0, hybrid);

		int total = equity + debt + hybrid;
		if (total != 100) {
			hybrid = Math.max(0, hybrid + (100 - total));
		}
		return new int[] {equity, debt, hybrid};
	}

	/**
	 * @param preferredSubCategories goal-relevant sleeves, highest priority first
	 * @param requiredSubCategories  sleeves that must appear when the catalogue
	 *                               offers them
	 * @param equityPercent          target share for equity funds
	 * @param debtPercent            target share for debt funds
	 * @param hybridPercent          target share for hybrid funds
	 */
	public record PortfolioPolicy(
			List<FundSubCategory> preferredSubCategories,
			Set<FundSubCategory> requiredSubCategories,
			int equityPercent,
			int debtPercent,
			int hybridPercent) {

		/** The sleeves the shortlist is built from, preferred first. */
		public List<FundSubCategory> prioritizedSubCategories() {
			List<FundSubCategory> prioritized = new java.util.ArrayList<>(preferredSubCategories);
			for (FundSubCategory required : requiredSubCategories) {
				if (!prioritized.contains(required)) {
					prioritized.add(0, required);
				}
			}
			return prioritized;
		}
	}
}
