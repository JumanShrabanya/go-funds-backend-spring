package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Exchange a refresh token for a new token pair")
public record RefreshTokenRequest(
		@NotBlank
		@Schema(description = "The refresh token from `/auth/login`, `/auth/register` or a previous "
				+ "`/auth/refresh`. Single-use: it is consumed by this call.",
				example = "eyJhbGciOiJIUzI1NiJ9...")
		String refreshToken) {
}