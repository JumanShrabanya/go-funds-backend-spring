package com.js.gofunds_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank String password,
		@NotBlank String firstName,
		@NotBlank String lastName,
		String phone) {
}