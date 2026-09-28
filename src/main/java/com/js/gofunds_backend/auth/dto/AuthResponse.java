package com.js.gofunds_backend.auth.dto;

import com.js.gofunds_backend.domain.enums.UserRole;

import java.util.UUID;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		UUID id,
		String email,
		String firstName,
		String lastName,
		UserRole role,
		boolean emailVerified) {
}