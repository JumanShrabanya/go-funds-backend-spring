package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request a password reset email")
public record ForgotPasswordRequest(
		@NotBlank @Email
		@Schema(example = "asha@example.com")
		String email) {
}