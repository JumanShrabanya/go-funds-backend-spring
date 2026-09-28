package com.js.gofunds_backend.common.security;

import com.js.gofunds_backend.domain.enums.UserRole;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, UserRole role) {
}