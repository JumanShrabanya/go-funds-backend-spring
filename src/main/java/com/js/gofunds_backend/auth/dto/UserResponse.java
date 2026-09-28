package com.js.gofunds_backend.auth.dto;

import java.util.UUID;

public record UserResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		String phone,
		boolean emailVerified,
		boolean active) {
}