package com.js.gofunds_backend.auth.dto;

import com.js.gofunds_backend.domain.enums.OtpType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Re-issue an OTP of a given type")
public record ResendOtpRequest(
		@NotBlank @Email
		@Schema(example = "asha@example.com")
		String email,

		@NotNull
		@Schema(description = "`VERIFICATION` for signup, `PASSWORD_RESET` for a password reset",
				example = "VERIFICATION", allowableValues = {"VERIFICATION", "PASSWORD_RESET"})
		OtpType type) {
}