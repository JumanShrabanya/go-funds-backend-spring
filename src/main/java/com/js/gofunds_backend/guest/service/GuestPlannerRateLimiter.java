package com.js.gofunds_backend.guest.service;

import com.js.gofunds_backend.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** Shared Redis-backed fixed-window limiter for guest plan generation. */
@Slf4j
@Component
public class GuestPlannerRateLimiter {

	private static final String KEY_PREFIX = "gofunds:guest-planner:ip:";
	private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
			local current = redis.call('INCR', KEYS[1])
			if current == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			if current <= tonumber(ARGV[2]) then
			  return 1
			end
			redis.call('DECR', KEYS[1])
			return 0
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final int maxAttempts;
	private final Duration window;

	public GuestPlannerRateLimiter(
			StringRedisTemplate redisTemplate,
			@Value("${guest.planner.max-attempts:3}") int maxAttempts,
			@Value("${guest.planner.window:PT1H}") String window) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("guest.planner.max-attempts must be at least 1");
		}
		this.redisTemplate = redisTemplate;
		this.maxAttempts = maxAttempts;
		this.window = Duration.parse(window);
		if (this.window.isZero() || this.window.isNegative()) {
			throw new IllegalArgumentException("guest.planner.window must be positive");
		}
	}

	/** Returns true when this IP still has capacity in the current window. */
	public boolean tryAcquire(String clientIp) {
		try {
			Long allowed = redisTemplate.execute(
					ACQUIRE_SCRIPT,
					List.of(redisKey(clientIp)),
					String.valueOf(Math.max(1, window.toSeconds())),
					String.valueOf(maxAttempts));
			return Long.valueOf(1L).equals(allowed);
		} catch (DataAccessException ex) {
			log.error("Redis is unavailable for guest planner rate limiting", ex);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"Guest plan generation is temporarily unavailable. Please try again later.");
		}
	}

	private String redisKey(String clientIp) {
		return KEY_PREFIX + sha256(clientIp);
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}
}
