package com.js.gofunds_backend.auth.controller;

import com.js.gofunds_backend.auth.dto.AuthResponse;
import com.js.gofunds_backend.auth.dto.UserResponse;
import com.js.gofunds_backend.auth.service.AuthService;
import com.js.gofunds_backend.common.security.JwtProvider;
import com.js.gofunds_backend.config.CorsConfig;
import com.js.gofunds_backend.config.SecurityConfig;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.UserRole;
import com.js.gofunds_backend.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks down the authorization matrix in {@link SecurityConfig}.
 *
 * <p>The security rules rely on matcher ordering rather than on anything
 * self-evident: {@code /api/v1/auth/me} and {@code /api/v1/auth/logout} are
 * declared {@code authenticated} <em>before</em> the catch-all
 * {@code /api/v1/auth/** -> permitAll}, relying on first-match-wins. Moving
 * those two lines below the catch-all would silently make every authenticated
 * endpoint public, and nothing else in the suite would notice. These tests are
 * the only thing standing between that reordering and an auth bypass, so if you
 * change the matcher order in {@link SecurityConfig}, expect failures here.
 *
 * <p>No database is involved: {@link AuthService} and {@link UserRepository} are
 * mocked, and the real {@link JwtProvider} signs genuine tokens so the real
 * {@code JwtAuthenticationFilter} is exercised end to end.
 */
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, CorsConfig.class, AuthSecurityTest.TestBeans.class })
class AuthSecurityTest {

	private static final String ACCESS_SECRET = "test-access-secret-key-for-webmvc-slice-0123456789";
	private static final String REFRESH_SECRET = "test-refresh-secret-key-for-webmvc-slice-0123456789";
	private static final String OTHER_SECRET = "some-other-secret-key-that-is-long-enough-0123456789";

	private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String EMAIL = "user@example.com";

	private static final String VALID_LOGIN_BODY = """
			{ "email": "user@example.com", "password": "correct-horse" }""";
	private static final String VALID_REGISTER_BODY = """
			{
			  "email": "new@example.com",
			  "password": "correct-horse",
			  "firstName": "Ada",
			  "lastName": "Lovelace"
			}""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private UserRepository userRepository;

	@TestConfiguration(proxyBeanMethods = false)
	static class TestBeans {

		@Bean
		JwtProvider jwtProvider() {
			return new JwtProvider(ACCESS_SECRET, 900_000L, REFRESH_SECRET, 604_800_000L);
		}
	}

	// --- Public endpoints: reachable with no credentials at all ---

	@Test
	void registerIsPubliclyAccessible() throws Exception {
		when(authService.register(any())).thenReturn(authResponse());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_REGISTER_BODY))
				.andExpect(status().isOk());
	}

	@Test
	void loginIsPubliclyAccessible() throws Exception {
		when(authService.login(any())).thenReturn(authResponse());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_LOGIN_BODY))
				.andExpect(status().isOk());
	}

	@Test
	void refreshIsPubliclyAccessible() throws Exception {
		when(authService.refresh(any())).thenReturn(authResponse());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "refreshToken": "some-refresh-token" }"""))
				.andExpect(status().isOk());
	}

	// --- Protected endpoints: rejected before reaching any handler ---

	@Test
	void meIsRejectedWithoutAToken() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(authService);
	}

	@Test
	void logoutIsRejectedWithoutAToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(authService);
	}

	@Test
	void plannerIsRejectedWithoutAToken() throws Exception {
		mockMvc.perform(post("/api/v1/planner/generate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void fundsAreRejectedWithoutAToken() throws Exception {
		mockMvc.perform(get("/api/v1/funds"))
				.andExpect(status().isUnauthorized());
	}

	// --- Protected endpoints: a genuine token gets through ---

	@Test
	void meIsServedWhenTheTokenIsValid() throws Exception {
		User user = registeredUser();
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(authService.getCurrentUser(user)).thenReturn(userResponse());

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(validAccessToken())))
				.andExpect(status().isOk());

		verify(authService).getCurrentUser(user);
	}

	@Test
	void logoutIsServedWhenTheTokenIsValid() throws Exception {
		User user = registeredUser();
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

		mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(validAccessToken())))
				.andExpect(status().isOk());

		verify(authService).logout(user);
	}

	// --- Token rejection: a bad token must never be treated as anonymous access ---

	@Test
	void garbageTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(authService);
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() throws Exception {
		String forged = new JwtProvider(OTHER_SECRET, 900_000L, REFRESH_SECRET, 604_800_000L)
				.generateAccessToken(USER_ID, EMAIL, UserRole.USER.name());

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		// Negative expiry produces a token that was already stale when it was minted.
		String expired = new JwtProvider(ACCESS_SECRET, -60_000L, REFRESH_SECRET, 604_800_000L)
				.generateAccessToken(USER_ID, EMAIL, UserRole.USER.name());

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshTokenCannotBeUsedAsAnAccessToken() throws Exception {
		String refreshToken = new JwtProvider(ACCESS_SECRET, 900_000L, REFRESH_SECRET, 604_800_000L)
				.generateRefreshToken(USER_ID, EMAIL, UserRole.USER.name());

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(refreshToken)))
				.andExpect(status().isUnauthorized());
	}

	// --- Helpers ---

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static String validAccessToken() {
		return new JwtProvider(ACCESS_SECRET, 900_000L, REFRESH_SECRET, 604_800_000L)
				.generateAccessToken(USER_ID, EMAIL, UserRole.USER.name());
	}

	private static User registeredUser() {
		User user = new User();
		user.setId(USER_ID);
		user.setEmail(EMAIL);
		user.setFirstName("Ada");
		user.setLastName("Lovelace");
		user.setRole(UserRole.USER);
		user.setEmailVerified(true);
		user.setActive(true);
		return user;
	}

	private static AuthResponse authResponse() {
		return new AuthResponse(
				"access-token", "refresh-token", "Bearer", 900_000L,
				USER_ID, EMAIL, "Ada", "Lovelace", UserRole.USER, true);
	}

	private static UserResponse userResponse() {
		return new UserResponse(USER_ID, EMAIL, "Ada", "Lovelace", null, true, true);
	}
}