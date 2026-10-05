package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.planner.dto.PlanFundResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts between {@link PlanFundResponse} and the JSONB list stored in
 * {@code investment_plans.recommended_funds}.
 *
 * <p>Stored as a plain list of maps rather than a relation, which is what lets a
 * plan keep its recommended funds even if the catalogue is later re-synced. Reads
 * are defensive: the column is JSON, so a value can be missing, null or the wrong
 * type, and one malformed entry should not fail the whole plan listing.
 */
public final class PlanFundMapper {

	private PlanFundMapper() {
	}

	/** Serialises for persistence. */
	public static List<Map<String, Object>> toStored(List<PlanFundResponse> funds) {
		List<Map<String, Object>> stored = new ArrayList<>(funds.size());
		for (PlanFundResponse fund : funds) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("fundId", fund.fundId() == null ? null : fund.fundId().toString());
			row.put("schemeCode", fund.schemeCode());
			row.put("schemeName", fund.schemeName());
			row.put("fundHouse", fund.fundHouse());
			row.put("mainCategory", fund.mainCategory());
			row.put("subCategory", fund.subCategory());
			row.put("riskLevel", fund.riskLevel());
			row.put("allocationPercentage", fund.allocationPercentage());
			row.put("monthlyAmount", fund.monthlyAmount());
			stored.add(row);
		}
		return stored;
	}

	/**
	 * Deserialises a stored list, skipping entries that cannot be read.
	 *
	 * @param stored the raw JSONB value, which may be {@code null} or not a list
	 */
	@SuppressWarnings("unchecked")
	public static List<PlanFundResponse> toPlanFunds(Object stored) {
		if (!(stored instanceof List<?> rows)) {
			return List.of();
		}

		List<PlanFundResponse> funds = new ArrayList<>(rows.size());
		for (Object row : rows) {
			if (row instanceof Map<?, ?> map) {
				PlanFundResponse fund = toPlanFund((Map<String, Object>) map);
				if (fund != null) {
					funds.add(fund);
				}
			}
		}
		return List.copyOf(funds);
	}

	private static PlanFundResponse toPlanFund(Map<String, Object> map) {
		String fundId = asString(map.get("fundId"));
		String schemeName = asString(map.get("schemeName"));

		// Without an id and a name the entry is not identifiable, so drop it.
		if (fundId == null || schemeName == null) {
			return null;
		}

		UUID parsedId;
		try {
			parsedId = UUID.fromString(fundId);
		} catch (IllegalArgumentException ex) {
			return null;
		}

		return new PlanFundResponse(
				parsedId,
				asString(map.get("schemeCode")),
				schemeName,
				asString(map.get("fundHouse")),
				asString(map.get("mainCategory")),
				asString(map.get("subCategory")),
				asString(map.get("riskLevel")),
				asBigDecimal(map.get("allocationPercentage")),
				asBigDecimal(map.get("monthlyAmount")));
	}

	private static String asString(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return text.isBlank() ? null : text;
	}

	/** JSONB numbers come back as Integer, Long or BigDecimal depending on scale. */
	private static BigDecimal asBigDecimal(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof Number number) {
			return new BigDecimal(number.toString());
		}
		try {
			return new BigDecimal(String.valueOf(value));
		} catch (NumberFormatException ex) {
			return null;
		}
	}
}