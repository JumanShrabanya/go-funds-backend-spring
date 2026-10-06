package com.js.gofunds_backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login credentials")
public record LoginRequest(
		@NotBlank @Email
		@Schema(example = "asha@example.com")
		String email,

		@NotBlank
		@Schema(description = "RSA-encrypted, then Base64-encoded. Encrypt with the server's public key "
				+ "using `RSA/ECB/PKCS1Padding`.",
				example = "kQd9x0Zr1mA8sT2v...",
				format = "password")
		String password) {
}
