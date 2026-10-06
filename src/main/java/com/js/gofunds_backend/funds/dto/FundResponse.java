package com.js.gofunds_backend.funds.dto;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "A mutual fund from the AMFI-synced catalogue")
public record FundResponse(
		UUID id,

		@Schema(description = "AMFI scheme code — the identifier to use in `/funds/{schemeCode}`",
				example = "130565")
		String schemeCode,

		@Schema(example = "IL&FS Infrastructure Debt Fund Series 1A")
		String schemeName,

		@Schema(description = "Parsed from the heading line above each block in the AMFI feed",
				example = "ICICI Prudential")
		String fundHouse,

		@Schema(description = "Inferred from the scheme name by `FundClassifier`, not supplied by AMFI")
		FundMainCategory mainCategory,

		@Schema(description = "Inferred from the scheme name, same caveat as `mainCategory`")
		FundSubCategory subCategory,

		@Schema(description = "Inferred from the scheme name, same caveat as `mainCategory`")
		RiskLevel riskLevel,

		@Schema(description = "Net asset value per unit, in rupees. Synced daily.",
				example = "2540201.9508")
		BigDecimal currentNav,

		@Schema(description = "Cumulative return, percent. Usually null — AMFI publishes NAV only, so "
				+ "there is no return history unless another source filled it in.", nullable = true)
		BigDecimal returnRate1Year,

		@Schema(description = "Cumulative return, percent. Usually null.", nullable = true)
		BigDecimal returnRate3Year,

		@Schema(description = "Cumulative return, percent. Usually null.", nullable = true)
		BigDecimal returnRate5Year,

		@Schema(description = "Defaults to true: AMFI's feed does not say which schemes allow SIP")
		boolean supportsSip,

		@Schema(description = "Defaults to true, same caveat as `supportsSip`")
		boolean supportsLumpSum,

		@Schema(description = "When this row was last refreshed from AMFI")
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
