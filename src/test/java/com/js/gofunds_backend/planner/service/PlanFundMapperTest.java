package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.planner.dto.PlanFundResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanFundMapperTest {

	private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static PlanFundResponse fund() {
		return new PlanFundResponse(
				ID, "100001", "Alpha Large Cap", "Alpha Mutual", "EQUITY", "LARGE_CAP",
				"MODERATE", new BigDecimal("60.00"), new BigDecimal("6000.00"));
	}

	@Test
	void roundTripsAFundThroughTheJsonbShape() {
		List<PlanFundResponse> restored = PlanFundMapper.toPlanFunds(PlanFundMapper.toStored(List.of(fund())));

		assertEquals(1, restored.size());
		assertEquals(fund(), restored.get(0));
	}

	@Test
	void preservesTheSchemeCodeThatOnlyTheDatabaseKnows() {
		List<PlanFundResponse> restored = PlanFundMapper.toPlanFunds(PlanFundMapper.toStored(List.of(fund())));

		assertEquals("100001", restored.get(0).schemeCode());
	}

	@Test
	void returnsAnEmptyListForANullColumn() {
		assertTrue(PlanFundMapper.toPlanFunds(null).isEmpty());
	}

	@Test
	void returnsAnEmptyListWhenTheColumnIsNotAList() {
		assertTrue(PlanFundMapper.toPlanFunds("not a list").isEmpty());
	}

	@Test
	void skipsEntriesThatAreNotMaps() {
		List<Object> stored = List.of("garbage", 42, PlanFundMapper.toStored(List.of(fund())).get(0));

		assertEquals(1, PlanFundMapper.toPlanFunds(stored).size());
	}

	@Test
	void skipsAnEntryWithNoFundId() {
		List<Map<String, Object>> stored = PlanFundMapper.toStored(List.of(fund()));
		stored.get(0).remove("fundId");

		assertTrue(PlanFundMapper.toPlanFunds(stored).isEmpty());
	}

	@Test
	void skipsAnEntryWithAMalformedFundId() {
		List<Map<String, Object>> stored = PlanFundMapper.toStored(List.of(fund()));
		stored.get(0).put("fundId", "not-a-uuid");

		assertTrue(PlanFundMapper.toPlanFunds(stored).isEmpty());
	}

	@Test
	void keepsGoodEntriesWhenOneIsMalformed() {
		List<Map<String, Object>> stored = PlanFundMapper.toStored(List.of(fund()));
		stored.add(Map.of("fundId", "not-a-uuid", "schemeName", "Broken"));
		stored.add(Map.of("fundId", "22222222-2222-2222-2222-222222222222", "schemeName", "Good"));

		List<PlanFundResponse> restored = PlanFundMapper.toPlanFunds(stored);

		assertEquals(2, restored.size());
	}

	@Test
	void readsNumbersBackRegardlessOfTheirJsonNumericType() {
		// JSONB numbers come back as Integer, Long or BigDecimal depending on scale.
		List<Object> stored = List.of(Map.of(
				"fundId", ID.toString(),
				"schemeName", "Alpha",
				"allocationPercentage", 60,
				"monthlyAmount", 6000.50d));

		PlanFundResponse restored = PlanFundMapper.toPlanFunds(stored).get(0);

		assertEquals(0, restored.allocationPercentage().compareTo(new BigDecimal("60")));
		assertEquals(0, restored.monthlyAmount().compareTo(new BigDecimal("6000.50")));
	}

	@Test
	void toleratesUnparseableNumbers() {
		List<Object> stored = List.of(Map.of(
				"fundId", ID.toString(),
				"schemeName", "Alpha",
				"allocationPercentage", "not a number"));

		assertNull(PlanFundMapper.toPlanFunds(stored).get(0).allocationPercentage());
	}

	@Test
	void treatsBlankStringsAsNull() {
		List<Object> stored = List.of(Map.of(
				"fundId", ID.toString(),
				"schemeName", "Alpha",
				"fundHouse", "   "));

		assertNull(PlanFundMapper.toPlanFunds(stored).get(0).fundHouse());
	}
}