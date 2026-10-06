package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "A user's public profile. Never includes the password hash.")
public record UserResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		String phone,

		@Schema(description = "False until the emailed OTP is consumed via `/auth/verify-email`")
		boolean emailVerified,

		@Schema(description = "False for accounts an admin has disabled")
		boolean active) {
}