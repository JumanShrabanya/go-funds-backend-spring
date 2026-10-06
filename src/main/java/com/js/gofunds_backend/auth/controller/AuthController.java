package com.js.gofunds_backend.auth.controller;

import com.js.gofunds_backend.auth.dto.AuthResponse;
import com.js.gofunds_backend.auth.dto.ForgotPasswordRequest;
import com.js.gofunds_backend.auth.dto.LoginRequest;
import com.js.gofunds_backend.auth.dto.RefreshTokenRequest;
import com.js.gofunds_backend.auth.dto.RegisterRequest;
import com.js.gofunds_backend.auth.dto.ResendOtpRequest;
import com.js.gofunds_backend.auth.dto.ResetPasswordRequest;
import com.js.gofunds_backend.auth.dto.UserResponse;
import com.js.gofunds_backend.auth.dto.VerifyEmailRequest;
import com.js.gofunds_backend.auth.service.AuthService;
import com.js.gofunds_backend.common.dto.ApiResponse;
import com.js.gofunds_backend.common.security.CurrentUser;
import com.js.gofunds_backend.domain.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration, login, email OTP verification and password reset")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@Operation(summary = "Register a new account",
			description = """
					Creates the account, mails a 6-digit verification OTP and returns a token pair. The account \
					can be used straight away; `emailVerified` stays false until the OTP is consumed.

					`password` is RSA-encrypted exactly like `/auth/login`, then bcrypt-hashed.""")
	@SecurityRequirements
	@PostMapping("/register")
	public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Registration successful.", authService.register(request)));
	}

	@Operation(summary = "Log in",
			description = "`password` is RSA-encrypted by the client — Base64 of a ciphertext produced "
					+ "with the server's public key using `RSA/ECB/PKCS1Padding`.")
	@SecurityRequirements
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}

	@Operation(summary = "Rotate the token pair",
			description = "Consumes the refresh token and issues a new access/refresh pair. Refresh tokens are "
					+ "single-use and only one is valid per account, so logging in elsewhere invalidates this one.")
	@SecurityRequirements
	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
	}

	@Operation(summary = "Log out", description = "Revokes every refresh token for the account. Access tokens "
			+ "stay valid until they expire, which is at most 15 minutes.")
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(@Parameter(hidden = true) @CurrentUser User user) {
		authService.logout(user);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@Operation(summary = "Current user profile")
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> me(@Parameter(hidden = true) @CurrentUser User user) {
		return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser(user)));
	}

	@Operation(summary = "Verify an email address", description = "Consumes the `VERIFICATION` OTP. It is "
			+ "single-use and expires 15 minutes after it was sent.")
	@SecurityRequirements
	@PostMapping("/verify-email")
	public ResponseEntity<ApiResponse<UserResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Email verified successfully", authService.verifyEmail(request)));
	}

	@Operation(summary = "Re-send an OTP",
			description = "Issues a fresh OTP of the given type and replaces any earlier one, so only the "
					+ "newest code will validate.")
	@SecurityRequirements
	@PostMapping("/resend-otp")
	public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
		authService.resendOtp(request);
		return ResponseEntity.ok(ApiResponse.success("OTP has been sent.", null));
	}

	@Operation(summary = "Start a password reset",
			description = "Always returns the same message whether or not the address is registered, so the "
					+ "endpoint cannot be used to find out which emails have accounts.")
	@SecurityRequirements
	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.forgotPassword(request)));
	}

	@Operation(summary = "Finish a password reset",
			description = "Consumes the `PASSWORD_RESET` OTP and sets the new password. `newPassword` must be "
					+ "RSA-encrypted the same way as `/login`.")
	@SecurityRequirements
	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return ResponseEntity.ok(ApiResponse.success("Password has been reset. Please log in.", null));
	}
}
