package com.js.gofunds_backend.funds.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.funds.dto.FundResponse;
import com.js.gofunds_backend.funds.service.FundsQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Funds", description = "The mutual fund catalogue, synced daily from AMFI")
@RestController
@RequestMapping("/api/v1/funds")
@RequiredArgsConstructor
public class FundsController {

	private final FundsQueryService fundsQueryService;

	@Operation(summary = "Search funds",
			description = """
					All filters are optional and combine with AND. Only `Growth` (and dividend-reinvested) \
					options are stored, so every row is directly investable.

					`returnRate1Year` / `3Year` / `5Year` are usually `null`: AMFI publishes current NAV only, \
					not return history. The planner tells Gemini to read a null rate as unknown rather than zero.""")
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

	@Operation(summary = "Get one fund by AMFI scheme code",
			description = "`schemeCode` is the AMFI code, e.g. `130565` — not the ISIN.")
	// Fully qualified because our own ApiResponse envelope owns the simple name in
	// this file. Error responses still carry the standard envelope shape.
	@ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404", description = "No fund with that scheme code"))
	@GetMapping("/{schemeCode}")
	public ResponseEntity<ApiResponse<FundResponse>> findFund(
			@Parameter(description = "AMFI scheme code, e.g. 130565. Not the ISIN.")
			@PathVariable String schemeCode) {
		Fund fund = fundsQueryService.findBySchemeCode(schemeCode);
		if (fund == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "No fund found for scheme code " + schemeCode);
		}
		return ResponseEntity.ok(ApiResponse.success(FundResponse.from(fund)));
	}
}
