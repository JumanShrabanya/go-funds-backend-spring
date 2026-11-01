package com.js.gofunds_backend.guest.controller;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.common.security.JwtProvider;
import com.js.gofunds_backend.config.CorsConfig;
import com.js.gofunds_backend.config.SecurityConfig;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.domain.repository.UserRepository;
import com.js.gofunds_backend.guest.service.ClientIpResolver;
import com.js.gofunds_backend.guest.service.GuestPlannerService;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The guest endpoint is the only planner route that is not authenticated, and it
 * is protected by a per-IP cooldown instead. These tests pin both facts: no token
 * needed, validation still applies, and a used slot comes back as {@code 429}.
 */
@WebMvcTest(GuestPlannerController.class)
@Import({ SecurityConfig.class, CorsConfig.class, GuestPlannerControllerTest.TestBeans.class })
class GuestPlannerControllerTest {

	private static final String ACCESS_SECRET = "test-access-secret-key-for-webmvc-slice-0123456789";
	private static final String REFRESH_SECRET = "test-refresh-secret-key-for-webmvc-slice-0123456789";
	private static final String CLIENT_IP = "203.0.113.7";

	private static final String VALID_BODY = """
			{
			  "monthlyInvestment": 25000,
			  "goal": "WEALTH_CREATION",
			  "investmentHorizon": "FIVE_TO_TEN_YEARS",
			  "riskProfile": "MODERATE"
			}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GuestPlannerService guestPlannerService;

	@MockitoBean
	private ClientIpResolver clientIpResolver;

	/**
	 * Needed by {@code CurrentUserArgumentResolver}, which the WebMvc slice
	 * instantiates even though this controller never uses it.
	 */
	@MockitoBean
	private UserRepository userRepository;

	@TestConfiguration(proxyBeanMethods = false)
	static class TestBeans {

		@Bean
		JwtProvider jwtProvider() {
			return new JwtProvider(ACCESS_SECRET, 900_000L, REFRESH_SECRET, 604_800_000L);
		}
	}

	@Test
	void generateDoesNotRequireAToken() throws Exception {
		when(clientIpResolver.resolve(any())).thenReturn(CLIENT_IP);
		when(guestPlannerService.generate(any(InvestmentPlanRequest.class), eq(CLIENT_IP)))
				.thenReturn(preview());

		mockMvc.perform(post("/api/v1/guest/planner/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.goal").value("WEALTH_CREATION"));

		verify(guestPlannerService).generate(any(InvestmentPlanRequest.class), eq(CLIENT_IP));
	}

	@Test
	void malformedBodyIsRejectedBeforeTheServiceRuns() throws Exception {
		mockMvc.perform(post("/api/v1/guest/planner/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "monthlyInvestment": 25000, "investmentHorizon": "FIVE_TO_TEN_YEARS" }"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));

		verify(guestPlannerService, never()).generate(any(), any());
	}

	@Test
	void aUsedIpIsToldToTryAgainLater() throws Exception {
		when(clientIpResolver.resolve(any())).thenReturn(CLIENT_IP);
		when(guestPlannerService.generate(any(InvestmentPlanRequest.class), eq(CLIENT_IP)))
				.thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
						"A guest plan has already been generated from this IP address."));

		mockMvc.perform(post("/api/v1/guest/planner/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.success").value(false));
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
