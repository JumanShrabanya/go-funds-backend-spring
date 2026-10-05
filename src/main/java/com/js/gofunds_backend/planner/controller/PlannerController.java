package com.js.gofunds_backend.planner.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import com.js.gofunds_backend.common.security.CurrentUser;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.planner.dto.DashboardStatsResponse;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import com.js.gofunds_backend.planner.service.PlannerService;
import com.js.gofunds_backend.planner.service.PlannerStatsService;
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
@RestController
@RequestMapping("/api/v1/planner")
@RequiredArgsConstructor
public class PlannerController {

	private final PlannerService plannerService;
	private final PlannerStatsService plannerStatsService;

	@PostMapping("/generate")
	public ResponseEntity<ApiResponse<InvestmentPlanResponse>> generate(
			@CurrentUser User user,
			@Valid @RequestBody InvestmentPlanRequest request) {

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(plannerService.generate(user, request)));
	}

	@GetMapping("/plans")
	public ResponseEntity<ApiResponse<List<InvestmentPlanResponse>>> findPlans(@CurrentUser User user) {
		return ResponseEntity.ok(ApiResponse.success(plannerService.findAll(user)));
	}

	@GetMapping("/plans/{planId}")
	public ResponseEntity<ApiResponse<InvestmentPlanResponse>> findPlan(
			@CurrentUser User user,
			@PathVariable UUID planId) {

		return ResponseEntity.ok(ApiResponse.success(plannerService.findById(user, planId)));
	}

	@DeleteMapping("/plans/{planId}")
	public ResponseEntity<ApiResponse<Void>> deletePlan(@CurrentUser User user, @PathVariable UUID planId) {
		plannerService.delete(user, planId);
		return ResponseEntity.ok(ApiResponse.success("Investment plan deleted", null));
	}

	@GetMapping("/stats")
	public ResponseEntity<ApiResponse<DashboardStatsResponse>> stats(@CurrentUser User user) {
		return ResponseEntity.ok(ApiResponse.success(plannerStatsService.statsFor(user)));
	}
}