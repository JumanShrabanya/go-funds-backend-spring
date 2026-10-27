package com.js.gofunds_backend.guest.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import com.js.gofunds_backend.planner.service.PlannerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestPlannerServiceTest {

	private static final String IP = "203.0.113.7";

	@Mock
	private PlannerService plannerService;

	@Mock
	private GuestPlannerRateLimiter rateLimiter;

	@InjectMocks
	private GuestPlannerService guestPlannerService;

	private final InvestmentPlanRequest request = new InvestmentPlanRequest(
			new BigDecimal("25000"), InvestmentGoal.WEALTH_CREATION,
			InvestmentHorizon.FIVE_TO_TEN_YEARS, RiskProfile.MODERATE);

	@Test
	void returnsThePreviewWithoutSavingIt() {
		InvestmentPlanResponse preview = preview();
		when(rateLimiter.tryAcquire(IP)).thenReturn(Optional.of(Instant.now()));
		when(plannerService.preview(request)).thenReturn(preview);

		InvestmentPlanResponse result = guestPlannerService.generate(request, IP);

		assertSame(preview, result);
		verify(plannerService).preview(request);
	}

	@Test
	void rejectsAnIpThatHasAlreadyUsedItsGuestPlan() {
		when(rateLimiter.tryAcquire(IP)).thenReturn(Optional.empty());

		ApiException error = assertThrows(ApiException.class,
				() -> guestPlannerService.generate(request, IP));

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
		verify(plannerService, never()).preview(any());
	}

	@Test
	void releasesTheSlotWhenGenerationFails() {
		Instant reservation = Instant.now();
		when(rateLimiter.tryAcquire(IP)).thenReturn(Optional.of(reservation));
		when(plannerService.preview(request))
				.thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini unreachable"));

		assertThrows(ApiException.class, () -> guestPlannerService.generate(request, IP));

		verify(rateLimiter).release(IP, reservation);
	}

	private static InvestmentPlanResponse preview() {
		return new InvestmentPlanResponse(
				UUID.randomUUID(),
				InvestmentGoal.WEALTH_CREATION,
				InvestmentHorizon.FIVE_TO_TEN_YEARS,
				new BigDecimal("25000.00"),
				RiskProfile.MODERATE,
				Map.of("equity", 70, "debt", 20, "hybrid", 10, "blendedReturnRate", 11.5),
				List.of(),
				new BigDecimal("2100000.00"),
				new BigDecimal("3000000.00"),
				new BigDecimal("900000.00"),
				"Balanced mix for a long horizon.",
				PlanStatus.ACTIVE,
				LocalDateTime.now());
	}
}
