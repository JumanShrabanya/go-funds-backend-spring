package com.js.gofunds_backend.auth.dto;

import com.js.gofunds_backend.domain.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "A freshly issued token pair plus the user it belongs to")
public record AuthResponse(
		@Schema(description = "Bearer token. Send as `Authorization: Bearer <token>`. Lasts 15 minutes.")
		String accessToken,

		@Schema(description = "Single-use, 7 day lifetime. Exchanging it returns a new pair and "
				+ "invalidates itself.")
		String refreshToken,

		@Schema(example = "Bearer") String tokenType,

		@Schema(description = "Access token lifetime in seconds", example = "900")
		long expiresIn,

		UUID id,
		String email,
		String firstName,
		String lastName,
		UserRole role,

		@Schema(description = "False until the emailed OTP is consumed via `/auth/verify-email`")
		boolean emailVerified) {
}
