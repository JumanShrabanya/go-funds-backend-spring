package com.js.gofunds_backend.planner.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import com.js.gofunds_backend.common.security.CurrentUser;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.planner.dto.DashboardStatsResponse;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import com.js.gofunds_backend.planner.service.PlannerService;
import com.js.gofunds_backend.planner.service.PlannerStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Investment plan endpoints. All routes require authentication and are scoped to
 * the caller's own plans.
 */
@Tag(name = "Planner", description = "Gemini-powered investment plans: generate, list, read and delete")
@RestController
@RequestMapping("/api/v1/planner")
@RequiredArgsConstructor
public class PlannerController {

	private final PlannerService plannerService;
	private final PlannerStatsService plannerStatsService;

	@Operation(summary = "Generate an investment plan",
			description = """
					Sends the investor profile plus a shortlist of eligible funds to Gemini, which picks the \
					funds and splits the monthly amount across them. The model returns fund ids and allocation \
					percentages only — every rupee figure is computed in Java, and the answer is re-validated \
					against the catalogue before it is saved.

					Generation is the slowest endpoint in the API: one LLM round trip.""")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "201", description = "Plan created"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "409", description = "The fund catalogue is empty — AMFI has not synced yet"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "502",
					description = "Gemini replied, but the reply could not be repaired into a usable plan"),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
					responseCode = "503",
					description = "Gemini is not configured (GEMINI_API_KEY unset) or unreachable. Reading "
							+ "existing plans still works.")
	})
	@PostMapping("/generate")
	public ResponseEntity<ApiResponse<InvestmentPlanResponse>> generate(
			@Parameter(hidden = true)
			@CurrentUser User user,
			@Valid @RequestBody InvestmentPlanRequest request) {

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(plannerService.generate(user, request)));
	}

	@Operation(summary = "List my plans", description = "Newest first. Soft-deleted plans are excluded.")
	@GetMapping("/plans")
	public ResponseEntity<ApiResponse<List<InvestmentPlanResponse>>> findPlans(
			@Parameter(hidden = true) @CurrentUser User user) {
		return ResponseEntity.ok(ApiResponse.success(plannerService.findAll(user)));
	}

	@Operation(summary = "Get one plan")
	@ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "404", description = "No such plan for this account"))
	@GetMapping("/plans/{planId}")
	public ResponseEntity<ApiResponse<InvestmentPlanResponse>> findPlan(
			@Parameter(hidden = true) @CurrentUser User user,
			@Parameter(description = "Plan id, from `/plans` or from `generate`") @PathVariable UUID planId) {


		return ResponseEntity.ok(ApiResponse.success(plannerService.findById(user, planId)));
	}

	@Operation(summary = "Delete a plan",
			description = "Soft delete — the row stays for auditing, it is just hidden from the list and "
					+ "excluded from `/stats`.")
	@DeleteMapping("/plans/{planId}")
	public ResponseEntity<ApiResponse<Void>> deletePlan(
			@Parameter(hidden = true) @CurrentUser User user,
			@Parameter(description = "Plan id") @PathVariable UUID planId) {
		plannerService.delete(user, planId);
		return ResponseEntity.ok(ApiResponse.success("Investment plan deleted", null));
	}

	@Operation(summary = "Dashboard totals",
			description = "Per-account aggregates across all non-deleted plans. "
					+ "`averageProjectedReturns` is a rupee amount per plan, not a percentage.")
	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<DashboardStatsResponse>> stats(
			@Parameter(hidden = true) @CurrentUser User user) {
		return ResponseEntity.ok(ApiResponse.success(plannerStatsService.statsFor(user)));
	}
}
