package com.js.gofunds_backend.funds.dto;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FundResponse(
		UUID id,
		String schemeCode,
		String schemeName,
		String fundHouse,
		FundMainCategory mainCategory,
		FundSubCategory subCategory,
		RiskLevel riskLevel,
		BigDecimal currentNav,
		BigDecimal returnRate1Year,
		BigDecimal returnRate3Year,
		BigDecimal returnRate5Year,
		boolean supportsSip,
		boolean supportsLumpSum,
		LocalDateTime lastSyncedAt) {

	public static FundResponse from(Fund fund) {
		return new FundResponse(
				fund.getId(),
				fund.getSchemeCode(),
				fund.getSchemeName(),
				fund.getFundHouse(),
				fund.getMainCategory(),
				fund.getSubCategory(),
				fund.getRiskLevel(),
				fund.getCurrentNav(),
				fund.getReturnRate1Year(),
				fund.getReturnRate3Year(),
				fund.getReturnRate5Year(),
				fund.isSupportsSip(),
				fund.isSupportsLumpSum(),
				fund.getLastSyncedAt());
	}
}
