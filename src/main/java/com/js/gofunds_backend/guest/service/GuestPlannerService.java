package com.js.gofunds_backend.guest.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import com.js.gofunds_backend.planner.service.PlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Generates a plan without an account.
 *
 * <p>
 * The plan is not written to the database: {@link PlannerService#preview} runs
 * the same recommendation and projection pipeline as the authenticated generate
 * endpoint and returns the result directly.
 *
 * <p>
 * One visitor gets one plan per cooldown. Failed generations release the slot,
 * so an empty catalogue or a Gemini outage does not use up the visitor's
 * attempt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestPlannerService {

	private final PlannerService plannerService;
	private final GuestPlannerRateLimiter rateLimiter;

	/**
	 * @throws ApiException 429 when this IP has already used its guest plan,
	 *                      409/502/503 from the planner when a plan cannot be
	 *                      produced
	 */
	public InvestmentPlanResponse generate(InvestmentPlanRequest request, String clientIp) {
		Optional<Instant> reservation = rateLimiter.tryAcquire(clientIp);
		if (reservation.isEmpty()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"A guest plan has already been generated from this IP address. "
							+ "Sign in to generate and save more plans.");
		}

		try {
			InvestmentPlanResponse plan = plannerService.preview(request);
			log.info("Generated guest plan for {}", clientIp);
			return plan;
		} catch (RuntimeException ex) {
			rateLimiter.release(clientIp, reservation.get());
			throw ex;
		}
	}
}
