package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Create an account")
public record RegisterRequest(
		@NotBlank @Email
		@Schema(example = "asha@example.com")
		String email,

		@NotBlank
		@Schema(description = "RSA-encrypted, then Base64-encoded — same encoding as `/auth/login`. "
				+ "The decrypted value is bcrypt-hashed before storage. Max 72 characters.",
				example = "kQd9x0Zr1mA8sT2v...",
				format = "password")
		String password,

		@NotBlank
		@Schema(example = "Asha")
		String firstName,

		@NotBlank
		@Schema(example = "Menon")
		String lastName,

		@Schema(description = "Optional", example = "+919876543210")
		String phone) {
}
