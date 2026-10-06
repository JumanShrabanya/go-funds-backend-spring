package com.js.gofunds_backend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The envelope every endpoint returns")
public record ApiResponse<T>(
		@Schema(description = "False when the request failed. Redundant with the HTTP status, kept so "
				+ "clients can branch on the body alone.", example = "true")
		boolean success,

		@Schema(description = "Human-readable summary. On failure this is the reason.",
				example = "Registration successful.")
		String message,

		@Schema(description = "The payload. Null on failure and on endpoints that return no body.")
		T data,

		@Schema(description = "Server time, ISO-8601 UTC")
		Instant timestamp) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "Success", data, Instant.now());
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, message, data, Instant.now());
	}

	public static <T> ApiResponse<T> error(String message) {
		return new ApiResponse<>(false, message, null, Instant.now());
	}

	public static <T> ApiResponse<T> error(String message, T data) {
		return new ApiResponse<>(false, message, data, Instant.now());
	}
}
