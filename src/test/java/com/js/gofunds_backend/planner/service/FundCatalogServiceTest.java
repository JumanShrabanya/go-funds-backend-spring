package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.domain.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FundCatalogServiceTest {

	@Mock
	private FundRepository fundRepository;

	private FundCatalogService catalogService;

	@BeforeEach
	void setUp() {
		catalogService = new FundCatalogService(fundRepository, 30);
	}

	private static Fund fund(String name, FundSubCategory subCategory, FundMainCategory mainCategory) {
		Fund fund = new Fund();
		fund.setId(UUID.randomUUID());
		fund.setSchemeName(name);
		fund.setSubCategory(subCategory);
		fund.setMainCategory(mainCategory);
		fund.setCurrentNav(new BigDecimal("100.0000"));
		return fund;
	}

	/**
	 * Answers the repository per category with a distinct list, mirroring the real
	 * finder. Returning one shared list for every category would hand the same
	 * funds back three times, which is not what production does.
	 */
	private void givenFundsByCategory(Fund equity, Fund hybrid, Fund debt) {
		when(fundRepository.findByMainCategoryAndRiskLevelIn(eq(FundMainCategory.EQUITY), anyCollection()))
				.thenReturn(equity == null ? List.of() : List.of(equity));
		when(fundRepository.findByMainCategoryAndRiskLevelIn(eq(FundMainCategory.HYBRID), anyCollection()))
				.thenReturn(hybrid == null ? List.of() : List.of(hybrid));
		when(fundRepository.findByMainCategoryAndRiskLevelIn(eq(FundMainCategory.DEBT), anyCollection()))
				.thenReturn(debt == null ? List.of() : List.of(debt));
	}

	/** Captures every risk-level set the service requested, across all categories. */
	private List<RiskLevel> capturedRiskLevels() {
		ArgumentCaptor<Collection<RiskLevel>> captor = ArgumentCaptor.forClass(Collection.class);
		verify(fundRepository, atLeastOnce())
				.findByMainCategoryAndRiskLevelIn(any(), captor.capture());
		return captor.getAllValues().stream().flatMap(Collection::stream).distinct().toList();
	}

	@Test
	void returnsAnEmptyListWhenNothingIsEligible() {
		givenFundsByCategory(null, null, null);

		assertTrue(catalogService.eligibleFunds(RiskProfile.MODERATE, InvestmentHorizon.FIVE_TO_TEN_YEARS)
				.isEmpty());
	}

	@Test
	void capsTheShortlistAtTheConfiguredSize() {
		List<Fund> many = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			many.add(fund("Fund " + i, FundSubCategory.LARGE_CAP, FundMainCategory.EQUITY));
		}
		when(fundRepository.findByMainCategoryAndRiskLevelIn(eq(FundMainCategory.EQUITY), anyCollection()))
				.thenReturn(many);

		assertEquals(30, catalogService.eligibleFunds(RiskProfile.AGGRESSIVE,
				InvestmentHorizon.MORE_THAN_10_YEARS).size());
	}

	@Test
	void spreadsTheShortlistAcrossSubCategories() {
		// Heavy on large caps, as the real AMFI feed is. Filling sequentially would
		// return 30 large caps and leave the model no real choice.
		List<Fund> equity = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			equity.add(fund("Large Cap " + i, FundSubCategory.LARGE_CAP, FundMainCategory.EQUITY));
		}
		equity.add(fund("Nifty Index", FundSubCategory.INDEX, FundMainCategory.EQUITY));
		equity.add(fund("Flexi Cap", FundSubCategory.MID_CAP, FundMainCategory.EQUITY));
		givenFundsByCategory(null, null, null);
		when(fundRepository.findByMainCategoryAndRiskLevelIn(eq(FundMainCategory.EQUITY), anyCollection()))
				.thenReturn(equity);

		List<Fund> shortlist = catalogService.eligibleFunds(RiskProfile.MODERATE,
				InvestmentHorizon.FIVE_TO_TEN_YEARS);

		// Cap is 30 / 3 sub-categories = 10, so large caps stop at 10 even though
		// 100 exist, and the two scarce sleeves contribute one each.
		assertEquals(12, shortlist.size());
		assertEquals(3, shortlist.stream().map(Fund::getSubCategory).distinct().count(),
				"each sub-category must be represented");
		assertEquals(10, shortlist.stream()
				.filter(f -> f.getSubCategory() == FundSubCategory.LARGE_CAP)
				.count(),
				"the dominant sub-category must be capped, not allowed to swamp the rest");
	}

	@Test
	void usesTheFullBudgetWhenOnlyOneSubCategoryIsEligible() {
		// With a single sleeve there is nothing to balance against, so trimming to
		// a third of the budget would starve the model for no benefit.
		List<Fund> onlyLargeCaps = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			onlyLargeCaps.add(fund("Large Cap " + i, FundSubCategory.LARGE_CAP, FundMainCategory.EQUITY));
		}
		givenFundsByCategory(null, null, null);
		when(fundRepository.findByMainCategoryAndRiskLevelIn(eq(FundMainCategory.EQUITY), anyCollection()))
				.thenReturn(onlyLargeCaps);

		assertEquals(30, catalogService.eligibleFunds(RiskProfile.AGGRESSIVE,
				InvestmentHorizon.MORE_THAN_10_YEARS).size());
	}

	@Test
	void isDeterministicForTheSameInput() {
		givenFundsByCategory(
				fund("Alpha Fund", FundSubCategory.INDEX, FundMainCategory.EQUITY),
				null,
				fund("Gamma Fund", FundSubCategory.LIQUID, FundMainCategory.DEBT));

		List<Fund> first = catalogService.eligibleFunds(RiskProfile.MODERATE,
				InvestmentHorizon.FIVE_TO_TEN_YEARS);
		List<Fund> second = catalogService.eligibleFunds(RiskProfile.MODERATE,
				InvestmentHorizon.FIVE_TO_TEN_YEARS);

		assertEquals(first.stream().map(Fund::getSchemeName).toList(),
				second.stream().map(Fund::getSchemeName).toList());
	}

	@Test
	void excludesHighRiskFundsForAShortHorizon() {
		givenFundsByCategory(null, null, null);

		catalogService.eligibleFunds(RiskProfile.AGGRESSIVE, InvestmentHorizon.LESS_THAN_3_YEARS);

		List<RiskLevel> requested = capturedRiskLevels();
		assertTrue(requested.stream()
				.noneMatch(level -> level == RiskLevel.HIGH || level == RiskLevel.VERY_HIGH),
				"a sub-3-year horizon must exclude volatile funds, requested " + requested);
	}

	@Test
	void allowsHighRiskFundsForALongHorizon() {
		givenFundsByCategory(null, null, null);

		catalogService.eligibleFunds(RiskProfile.AGGRESSIVE, InvestmentHorizon.MORE_THAN_10_YEARS);

		assertTrue(capturedRiskLevels().contains(RiskLevel.VERY_HIGH));
	}

	@Test
	void doesNotDuplicateAFundThatMatchesTwoCategories() {
		// A fund has exactly one main category, so the shortlist must never repeat
		// a row even if the same id arrives from more than one query.
		Fund shared = fund("Shared", FundSubCategory.LARGE_CAP, FundMainCategory.EQUITY);
		givenFundsByCategory(shared, shared, null);

		List<Fund> shortlist = catalogService.eligibleFunds(RiskProfile.MODERATE,
				InvestmentHorizon.FIVE_TO_TEN_YEARS);

		assertEquals(1, shortlist.size());
	}
}