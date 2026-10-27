package com.js.gofunds_backend.guest.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuestPlannerRateLimiterTest {

	private static final String IP = "203.0.113.7";
	private static final Duration COOLDOWN = Duration.ofHours(24);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T00:00:00Z"));

	@Test
	void firstAttemptIsAllowed() {
		GuestPlannerRateLimiter limiter = limiter();

		assertTrue(limiter.tryAcquire(IP).isPresent());
	}

	@Test
	void secondAttemptIsRejectedWithinTheCooldown() {
		GuestPlannerRateLimiter limiter = limiter();
		limiter.tryAcquire(IP);

		clock.advance(Duration.ofHours(23));

		assertTrue(limiter.tryAcquire(IP).isEmpty());
	}

	@Test
	void attemptIsAllowedAgainAfterTheCooldown() {
		GuestPlannerRateLimiter limiter = limiter();
		limiter.tryAcquire(IP);

		clock.advance(COOLDOWN);

		assertTrue(limiter.tryAcquire(IP).isPresent());
	}

	@Test
	void differentIpsDoNotShareTheLimit() {
		GuestPlannerRateLimiter limiter = limiter();
		limiter.tryAcquire(IP);

		assertTrue(limiter.tryAcquire("198.51.100.4").isPresent());
	}

	@Test
	void releasingTheReservationAllowsAnImmediateRetry() {
		GuestPlannerRateLimiter limiter = limiter();
		Optional<Instant> reservation = limiter.tryAcquire(IP);
		assertTrue(reservation.isPresent());

		limiter.release(IP, reservation.orElseThrow());

		assertTrue(limiter.tryAcquire(IP).isPresent());
	}

	@Test
	void releaseDoesNotClearANewerReservation() {
		GuestPlannerRateLimiter limiter = limiter();
		Instant first = limiter.tryAcquire(IP).orElseThrow();

		clock.advance(COOLDOWN);
		Instant second = limiter.tryAcquire(IP).orElseThrow();

		limiter.release(IP, first);

		assertTrue(limiter.tryAcquire(IP).isEmpty());
		assertTrue(second.isAfter(first));
	}

	private GuestPlannerRateLimiter limiter() {
		return new GuestPlannerRateLimiter(COOLDOWN, clock);
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		private MutableClock(Instant start) {
			this.now = start;
		}

		private void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
