package com.js.gofunds_backend.guest.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the address the guest rate limiter keys on.
 *
 * <p>{@code X-Forwarded-For} is ignored by default because any client can set it
 * and spoofing it would hand out unlimited guest plans. Turn it on only when the
 * app sits behind a proxy you control that overwrites the header, and keep Spring
 * Boot's {@code server.forward-headers-strategy} consistent with that.
 */
@Component
public class ClientIpResolver {

	private final boolean trustForwardedHeaders;

	public ClientIpResolver(
			@Value("${guest.planner.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
		this.trustForwardedHeaders = trustForwardedHeaders;
	}

	public String resolve(HttpServletRequest request) {
		if (trustForwardedHeaders) {
			String forwarded = request.getHeader("X-Forwarded-For");
			if (forwarded != null && !forwarded.isBlank()) {
				return forwarded.split(",")[0].trim();
			}
		}
		return request.getRemoteAddr();
	}
}
