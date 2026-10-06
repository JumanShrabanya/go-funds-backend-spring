package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Complete a password reset using the emailed OTP")
public record ResetPasswordRequest(
		@NotBlank @Email
		@Schema(example = "asha@example.com")
		String email,

		@NotBlank @Size(min = 6, max = 6)
		@Schema(description = "The 6-digit code from the reset email",
				example = "418207")
		String otp,

		@NotBlank @Size(min = 8, max = 72)
		@Schema(description = "RSA-encrypted, then Base64-encoded — same encoding as `/auth/login`. "
				+ "72 characters is bcrypt's limit.",
				example = "kQd9x0Zr1mA8sT2v...",
				format = "password")
		String newPassword) {
}
