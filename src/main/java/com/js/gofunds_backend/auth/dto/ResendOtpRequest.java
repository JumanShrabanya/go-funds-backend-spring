package com.js.gofunds_backend.auth.dto;

import com.js.gofunds_backend.domain.enums.OtpType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResendOtpRequest(
		@NotBlank @Email String email,
		@NotNull OtpType type) {
}