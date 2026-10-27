package com.js.gofunds_backend.guest.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import com.js.gofunds_backend.guest.service.ClientIpResolver;
import com.js.gofunds_backend.guest.service.GuestPlannerService;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated preview of the planner.
 *
 * <p>Nothing is saved and no account is required. Each IP address is limited to
 * one generated plan per cooldown (24 hours by default); over that it gets
 * {@code 429} until the window passes.
 */
@Tag(name = "Guest Planner", description = "Generate one plan without signing in. The plan is not saved.")
@RestController
@RequestMapping("/api/v1/guest/planner")
@RequiredArgsConstructor
public class GuestPlannerController {

	private final GuestPlannerService guestPlannerService;
	private final ClientIpResolver clientIpResolver;

	@Operation(summary = "Generate a guest investment plan",
			description = """
					Same recommendation pipeline as `POST /api/v1/planner/generate`, but the plan is \
					returned and discarded instead of being stored. No token is required.

					Limited to one plan per IP address per cooldown window (24 hours by default). \
					The `id` in the response is transient and cannot be fetched later.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "201", description = "Guest plan generated, not saved"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "409", description = "The fund catalogue is empty — AMFI has not synced yet"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "429", description = "This IP address has already generated a guest plan"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "502",
					description = "Gemini replied, but the reply could not be repaired into a usable plan"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "503", description = "Gemini is not configured or unreachable")
	})
	@PostMapping("/generate")
	public ResponseEntity<ApiResponse<InvestmentPlanResponse>> generate(
			@Valid @RequestBody InvestmentPlanRequest request,
			HttpServletRequest httpRequest) {

		String clientIp = clientIpResolver.resolve(httpRequest);
		InvestmentPlanResponse plan = guestPlannerService.generate(request, clientIp);

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Guest plan generated. Sign in to save plans.", plan));
	}
}
