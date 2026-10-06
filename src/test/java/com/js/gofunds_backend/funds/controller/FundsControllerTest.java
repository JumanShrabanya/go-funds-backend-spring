package com.js.gofunds_backend.funds.controller;

import com.js.gofunds_backend.common.security.JwtProvider;
import com.js.gofunds_backend.config.CorsConfig;
import com.js.gofunds_backend.config.SecurityConfig;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.enums.UserRole;
import com.js.gofunds_backend.domain.repository.UserRepository;
import com.js.gofunds_backend.funds.service.FundsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Query-parameter handling for {@link FundsController}.
 *
 * <p>{@code category} and {@code riskLevel} bind to enums. A value outside the
 * enum raises {@code MethodArgumentTypeMismatchException}, which without a
 * dedicated handler falls through to the catch-all in
 * {@code GlobalExceptionHandler} and blames the server with a 500. These tests
 * pin the client-error behaviour so it cannot silently regress.
 */
@WebMvcTest(FundsController.class)
@Import({ SecurityConfig.class, CorsConfig.class, FundsControllerTest.TestBeans.class })
class FundsControllerTest {

	private static final String ACCESS_SECRET = "test-access-secret-key-for-webmvc-slice-0123456789";
	private static final String REFRESH_SECRET = "test-refresh-secret-key-for-webmvc-slice-0123456789";

	private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FundsQueryService fundsQueryService;

	/**
	 * Not used by {@link FundsController}, but the WebMvc slice instantiates
	 * {@code CurrentUserArgumentResolver} (it is a HandlerMethodArgumentResolver,
	 * which the slice includes) and that bean needs a repository.
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

	private static String bearer() {
		return "Bearer " + new JwtProvider(ACCESS_SECRET, 900_000L, REFRESH_SECRET, 604_800_000L)
				.generateAccessToken(USER_ID, "user@example.com", UserRole.USER.name());
	}

	private void emptyPage() {
		when(fundsQueryService.findAll(any(), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));
	}

	// --- Invalid enum values are the client's mistake: 400, not 500 ---

	@Test
	void unknownCategoryIsRejectedAsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/funds").param("category", "CRYPTO")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid value for parameter: category"));

		// The query must never reach the service with a value it cannot handle.
		verify(fundsQueryService, never()).findAll(any(), any(), any(), any(Pageable.class));
	}

	@Test
	void unknownRiskLevelIsRejectedAsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/funds").param("riskLevel", "VERY_RISKY")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter: riskLevel"));
	}

	@Test
	void lowercaseEnumValueIsStillRejected() throws Exception {
		// Enum binding is case-sensitive by default; being lenient is a separate
		// decision, but answering 400 rather than 500 is the part that matters.
		mockMvc.perform(get("/api/v1/funds").param("category", "equity")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theBadRequestDoesNotEchoTheOffendingValue() throws Exception {
		String body = mockMvc.perform(get("/api/v1/funds")
						.param("category", "<script>alert(1)</script>")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isBadRequest())
				.andReturn().getResponse().getContentAsString();

		assertFalse(body.contains("script"),
				"the message should name the parameter only, not reflect the input value");
	}

	// --- Valid requests still work ---

	@Test
	void validCategoryIsAccepted() throws Exception {
		emptyPage();

		mockMvc.perform(get("/api/v1/funds").param("category", "EQUITY")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(fundsQueryService)
				.findAll(eq(FundMainCategory.EQUITY), isNull(), isNull(), any(Pageable.class));
	}

	@Test
	void validRiskLevelIsAccepted() throws Exception {
		emptyPage();

		mockMvc.perform(get("/api/v1/funds").param("riskLevel", "VERY_HIGH")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk());

		verify(fundsQueryService)
				.findAll(isNull(), eq(RiskLevel.VERY_HIGH), isNull(), any(Pageable.class));
	}

	@Test
	void searchIsAPlainStringSoAnythingGoes() throws Exception {
		emptyPage();

		mockMvc.perform(get("/api/v1/funds").param("search", "hdfc")
						.header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk());

		verify(fundsQueryService)
				.findAll(isNull(), isNull(), eq("hdfc"), any(Pageable.class));
	}

	@Test
	void noFiltersReturnsTheWholeCatalogue() throws Exception {
		emptyPage();

		mockMvc.perform(get("/api/v1/funds").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isOk());
	}

	// --- Auth is still required ---

	@Test
	void theCatalogueIsNotReadableAnonymously() throws Exception {
		mockMvc.perform(get("/api/v1/funds"))
				.andExpect(status().isUnauthorized());
	}
}