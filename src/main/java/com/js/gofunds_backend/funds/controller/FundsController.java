package com.js.gofunds_backend.funds.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.funds.dto.FundResponse;
import com.js.gofunds_backend.funds.service.FundsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/funds")
@RequiredArgsConstructor
public class FundsController {

	private final FundsQueryService fundsQueryService;

	@GetMapping
	public ResponseEntity<ApiResponse<Page<FundResponse>>> findFunds(
			@RequestParam(required = false) FundMainCategory category,
			@RequestParam(required = false) RiskLevel riskLevel,
			@RequestParam(required = false) String search,
			@PageableDefault(size = 20, sort = "schemeName") Pageable pageable) {

		Page<FundResponse> funds = fundsQueryService
				.findAll(category, riskLevel, search, pageable)
				.map(FundResponse::from);

		return ResponseEntity.ok(ApiResponse.success(funds));
	}

	@GetMapping("/{schemeCode}")
	public ResponseEntity<ApiResponse<FundResponse>> findFund(@PathVariable String schemeCode) {
		Fund fund = fundsQueryService.findBySchemeCode(schemeCode);
		if (fund == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "No fund found for scheme code " + schemeCode);
		}
		return ResponseEntity.ok(ApiResponse.success(FundResponse.from(fund)));
	}
}
