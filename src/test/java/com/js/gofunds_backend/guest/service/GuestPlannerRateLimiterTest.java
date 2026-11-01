package com.js.gofunds_backend.guest.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestPlannerRateLimiterTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Test
	void allowsAnAttemptWhenRedisScriptAcceptsIt() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
				.thenReturn(1L);

		GuestPlannerRateLimiter limiter = new GuestPlannerRateLimiter(redisTemplate, 3, "PT1H");

		assertTrue(limiter.tryAcquire("203.0.113.7"));
	}

	@Test
	void rejectsAnAttemptWhenRedisScriptReportsTheLimit() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
				.thenReturn(0L);

		GuestPlannerRateLimiter limiter = new GuestPlannerRateLimiter(redisTemplate, 3, "PT1H");

		assertFalse(limiter.tryAcquire("203.0.113.7"));
	}
}
