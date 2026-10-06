package com.js.gofunds_backend.health.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "Liveness probe")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@Operation(summary = "Liveness probe",
			description = "Returns `UP` as long as the HTTP listener is up. It does not touch the database, "
					+ "so it stays green even when Postgres is unreachable.")
	@SecurityRequirements
	@GetMapping
	public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", "UP");
		body.put("timestamp", Instant.now());
		return ResponseEntity.ok(ApiResponse.success("Service is healthy", body));
	}
}