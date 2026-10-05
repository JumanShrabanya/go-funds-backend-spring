package com.js.gofunds_backend.planner.ai;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationValidatorTest {

	private final RecommendationValidator validator = new RecommendationValidator();

	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
	private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

	private static List<Fund> catalogue() {
		return List.of(fund(A, "Alpha Large Cap"), fund(B, "Beta Bond"), fund(C, "Gamma Index"));
	}

	private static Fund fund(UUID id, String name) {
		Fund fund = new Fund();
		fund.setId(id);
		fund.setSchemeName(name);
		fund.setMainCategory(FundMainCategory.EQUITY);
		fund.setSubCategory(FundSubCategory.LARGE_CAP);
		fund.setRiskLevel(RiskLevel.MODERATE);
		return fund;
	}

	private static Recommendation recommendation(List<Recommendation.SelectedFund> funds) {
		return new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(60, 30, 10),
				funds,
				new BigDecimal("12.5"),
				"A balanced plan.");
	}

	private static Recommendation.SelectedFund selected(UUID id, String allocation) {
		return new Recommendation.SelectedFund(id, "whatever the model said", new BigDecimal(allocation));
	}

	@Test
	void acceptsAWellFormedRecommendation() {
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "50"), selected(B, "30"), selected(C, "20"))),
				catalogue());

		assertEquals(3, result.selectedFunds().size());
		assertEquals(RiskProfile.MODERATE, result.riskProfile());
	}

	@Test
	void backFillsTheFundNameFromTheCatalogue() {
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "50"), selected(B, "30"), selected(C, "20"))),
				catalogue());

		// The model supplied a wrong name for A; the catalogue wins.
		assertEquals("Alpha Large Cap", result.selectedFunds().get(0).fundName());
	}

	@Test
	void dropsHallucinatedFundIdsAndRedistributesTheirAllocation() {
		// 40/30/20 are real and sum to 90; the hallucinated 10% is dropped and
		// its share is spread across the survivors rather than losing the value.
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "40"), selected(B, "30"), selected(C, "20"),
						selected(UNKNOWN, "10"))),
				catalogue());

		assertEquals(3, result.selectedFunds().size());
		assertTrue(result.selectedFunds().stream()
				.noneMatch(f -> UNKNOWN.equals(f.fundId())));
		assertEquals(0, allocatedTotal(result).compareTo(BigDecimal.valueOf(100)));
	}

	@Test
	void duplicateIdsDoNotDoubleCount() {
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "40"), selected(A, "30"), selected(B, "20"),
						selected(C, "10"))),
				catalogue());

		assertEquals(3, result.selectedFunds().size());
		long aCount = result.selectedFunds().stream()
				.filter(f -> A.equals(f.fundId()))
				.count();
		assertEquals(1, aCount);
	}

	@Test
	void allocationsThatSumToOneHundredAreLeftUnchanged() {
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "50"), selected(B, "30"), selected(C, "20"))),
				catalogue());

		assertEquals(0, allocatedTotal(result).compareTo(BigDecimal.valueOf(100)));
		assertEquals(0, result.selectedFunds().get(0).allocationPercentage()
				.compareTo(new BigDecimal("50.00")));
	}

	@Test
	void allocationsWithinToleranceAreRescaledToExactlyOneHundred() {
		// 33 + 33 + 33 = 99, the classic rounding drift.
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "33"), selected(B, "33"), selected(C, "33"))),
				catalogue());

		assertEquals(0, allocatedTotal(result).compareTo(BigDecimal.valueOf(100)));
	}

	@Test
	void rejectsAnAllocationWithAlmostNothingUsable() {
		// Only 5% survives filtering, below the recoverable floor.
		ApiException ex = assertThrows(ApiException.class, () -> validator.validate(
				recommendation(List.of(selected(A, "5"), selected(UNKNOWN, "95"))),
				catalogue()));

		assertTrue(ex.getMessage().contains("usable funds"));
	}

	private static BigDecimal allocatedTotal(Recommendation recommendation) {
		return recommendation.selectedFunds().stream()
				.map(Recommendation.SelectedFund::allocationPercentage)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	@Test
	void rejectsTooFewUsableFunds() {
		// One real fund plus two hallucinated ones leaves only one usable.
		ApiException ex = assertThrows(ApiException.class, () -> validator.validate(
				recommendation(List.of(selected(A, "60"), selected(UNKNOWN, "20"),
						selected(UUID.randomUUID(), "20"))),
				catalogue()));

		assertTrue(ex.getMessage().contains("usable funds"));
	}

	@Test
	void rejectsAnEmptySelection() {
		ApiException ex = assertThrows(ApiException.class, () -> validator.validate(
				recommendation(List.of()), catalogue()));

		assertTrue(ex.getMessage().contains("no funds"));
	}

	@Test
	void rejectsANullSelection() {
		Recommendation dto = new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(60, 30, 10),
				null,
				new BigDecimal("12.5"),
				"text");

		assertThrows(ApiException.class, () -> validator.validate(dto, catalogue()));
	}

	@Test
	void rejectsAnAssetAllocationThatDoesNotTotalOneHundred() {
		Recommendation dto = new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(50, 30, 10),
				List.of(selected(A, "50"), selected(B, "30"), selected(C, "20")),
				new BigDecimal("12.5"),
				"text");

		ApiException ex = assertThrows(ApiException.class, () -> validator.validate(dto, catalogue()));

		assertTrue(ex.getMessage().contains("asset allocation"));
	}

	@Test
	void rejectsAnImplausibleReturnRate() {
		Recommendation dto = new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(60, 30, 10),
				List.of(selected(A, "50"), selected(B, "30"), selected(C, "20")),
				new BigDecimal("900"),
				"to the moon");

		ApiException ex = assertThrows(ApiException.class, () -> validator.validate(dto, catalogue()));

		assertTrue(ex.getMessage().contains("implausible"));
	}

	@Test
	void rejectsANegativeReturnRate() {
		Recommendation dto = new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(60, 30, 10),
				List.of(selected(A, "50"), selected(B, "30"), selected(C, "20")),
				new BigDecimal("-5"),
				"loss");

		assertThrows(ApiException.class, () -> validator.validate(dto, catalogue()));
	}

	@Test
	void fillsInAMissingRiskProfile() {
		Recommendation dto = new Recommendation(
				null,
				new Recommendation.AssetAllocation(60, 30, 10),
				List.of(selected(A, "50"), selected(B, "30"), selected(C, "20")),
				new BigDecimal("12.5"),
				"text");

		assertEquals(RiskProfile.MODERATE, validator.validate(dto, catalogue()).riskProfile());
	}

	@Test
	void replacesAMissingExplanationWithAnEmptyString() {
		Recommendation dto = new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(60, 30, 10),
				List.of(selected(A, "50"), selected(B, "30"), selected(C, "20")),
				new BigDecimal("12.5"),
				null);

		assertEquals("", validator.validate(dto, catalogue()).explanation());
	}

	@Test
	void dropsZeroAndNegativeAllocations() {
		Recommendation result = validator.validate(
				recommendation(List.of(selected(A, "50"), selected(B, "30"), selected(C, "20"),
						selected(UNKNOWN, "0"))),
				catalogue());

		assertEquals(3, result.selectedFunds().size());
	}

	@Test
	void rejectsANullRecommendation() {
		assertThrows(ApiException.class, () -> validator.validate(null, catalogue()));
	}
}