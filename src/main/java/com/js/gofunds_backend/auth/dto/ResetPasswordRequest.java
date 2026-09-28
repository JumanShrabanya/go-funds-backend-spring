package com.js.gofunds_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 6, max = 6) String otp,
		@NotBlank @Size(min = 8, max = 72) String newPassword) {
}