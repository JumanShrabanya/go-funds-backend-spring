package com.js.gofunds_backend.guest.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import com.js.gofunds_backend.planner.service.PlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Generates a plan without an account.
 *
 * <p>
 * The plan is not written to the database: {@link PlannerService#preview} runs
 * the same recommendation and projection pipeline as the authenticated generate
 * endpoint and returns the result directly.
 *
 * <p>
 * The IP limit is applied to generation attempts before the expensive AI call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestPlannerService {

	private final PlannerService plannerService;
	private final GuestPlannerRateLimiter rateLimiter;

	/**
	 * @throws ApiException 429 when this IP has exhausted its guest plan attempts,
	 *                      409/502/503 from the planner when a plan cannot be
	 *                      produced
	 */
	public InvestmentPlanResponse generate(InvestmentPlanRequest request, String clientIp) {
		if (!rateLimiter.tryAcquire(clientIp)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"This IP address has reached the guest plan generation limit. "
							+ "Please try again later or sign in to save more plans.");
		}

		InvestmentPlanResponse plan = plannerService.preview(request);
		log.info("Generated guest plan for {}", clientIp);
		return plan;
	}
}
