package com.js.gofunds_backend.auth.controller;

import com.js.gofunds_backend.auth.dto.AuthResponse;
import com.js.gofunds_backend.auth.dto.RegisterRequest;
import com.js.gofunds_backend.auth.service.AuthService;
import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.config.CorsConfig;
import com.js.gofunds_backend.config.SecurityConfig;
import com.js.gofunds_backend.domain.enums.UserRole;
import com.js.gofunds_backend.domain.repository.UserRepository;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers what {@link AuthSecurityTest} deliberately ignores: bean validation,
 * the JSON error envelope, and propagation of service-level failures.
 *
 * <p>{@link AuthService} is mocked, so nothing here needs a database. These
 * endpoints are all {@code permitAll} in {@link SecurityConfig}, which keeps the
 * setup free of token plumbing.
 */
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, CorsConfig.class, AuthControllerTest.NoopBeans.class })
class AuthControllerTest {

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
	static class NoopBeans {

		/**
		 * {@link SecurityConfig} only needs the type; this slice never issues a
		 * request with credentials, so the filter cannot reject anything.
		 */
		@Bean
		com.js.gofunds_backend.common.security.JwtProvider jwtProvider() {
			return new com.js.gofunds_backend.common.security.JwtProvider(
					"unused-access-secret-key-long-enough-for-hs256-0123456789", 900_000L,
					"unused-refresh-secret-key-long-enough-for-hs256-0123456789", 604_800_000L);
		}
	}

	// --- Validation: 400 before the service is ever called ---

	@Test
	void registerRejectsAMalformedEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "not-an-email",
								  "password": "correct-horse",
								  "firstName": "Ada",
								  "lastName": "Lovelace"
								}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data.email").exists());

		verify(authService, never()).register(any());
	}

	@Test
	void registerRejectsABlankPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "new@example.com",
								  "password": "   ",
								  "firstName": "Ada",
								  "lastName": "Lovelace"
								}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.data.password").exists());

		verify(authService, never()).register(any());
	}

	@Test
	void loginRejectsABlankEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "", "password": "correct-horse" }"""))
				.andExpect(status().isBadRequest());

		verify(authService, never()).login(any());
	}

	@Test
	void unparseableBodyIsRejectedAsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ this is not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed request body"));
	}

	// --- Happy path: the ApiResponse envelope the clients actually parse ---

	@Test
	void registerReturnsTokensAndAConfirmationMessage() throws Exception {
		when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(VALID_REGISTER_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Registration successful."))
				.andExpect(jsonPath("$.data.accessToken").value("access-token"))
				.andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.role").value("USER"));
	}

	@Test
	void loginUsesTheDefaultSuccessMessage() throws Exception {
		when(authService.login(any())).thenReturn(authResponse());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "new@example.com", "password": "correct-horse" }"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Success"))
				.andExpect(jsonPath("$.data.email").value("new@example.com"));
	}

	// --- Service failures keep their own status and message ---

	@Test
	void badCredentialsSurfaceAsUnauthorized() throws Exception {
		when(authService.login(any()))
				.thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "new@example.com", "password": "wrong" }"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void lockedAccountSurfacesAsForbidden() throws Exception {
		when(authService.login(any()))
				.thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Account is locked"));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "new@example.com", "password": "correct-horse" }"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Account is locked"));
	}

	private static AuthResponse authResponse() {
		return new AuthResponse(
				"access-token", "refresh-token", "Bearer", 900_000L,
				UUID.fromString("11111111-2222-3333-4444-555555555555"),
				"new@example.com", "Ada", "Lovelace", UserRole.USER, true);
	}
}