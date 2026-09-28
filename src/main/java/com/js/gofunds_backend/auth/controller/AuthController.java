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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Registration successful.", authService.register(request)));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
	}

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(@CurrentUser User user) {
		authService.logout(user);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserResponse>> me(@CurrentUser User user) {
		return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUser(user)));
	}

	@PostMapping("/verify-email")
	public ResponseEntity<ApiResponse<UserResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		return ResponseEntity.ok(ApiResponse.success("Email verified successfully", authService.verifyEmail(request)));
	}

	@PostMapping("/resend-otp")
	public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
		authService.resendOtp(request);
		return ResponseEntity.ok(ApiResponse.success("OTP has been sent.", null));
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		return ResponseEntity.ok(ApiResponse.success(authService.forgotPassword(request)));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		authService.resetPassword(request);
		return ResponseEntity.ok(ApiResponse.success("Password has been reset. Please log in.", null));
	}
}