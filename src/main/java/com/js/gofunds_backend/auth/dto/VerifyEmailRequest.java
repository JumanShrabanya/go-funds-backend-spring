package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Verify an email address with the OTP sent at signup")
public record VerifyEmailRequest(
		@NotBlank @Email
		@Schema(example = "asha@example.com")
		String email,

		@NotBlank @Size(min = 6, max = 6)
		@Schema(description = "The 6-digit code from the verification email", example = "418207")
		String otp) {
}