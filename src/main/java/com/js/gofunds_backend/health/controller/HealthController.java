package com.js.gofunds_backend.health.controller;

import com.js.gofunds_backend.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	@GetMapping
	public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", "UP");
		body.put("timestamp", Instant.now());
		return ResponseEntity.ok(ApiResponse.success("Service is healthy", body));
	}
}