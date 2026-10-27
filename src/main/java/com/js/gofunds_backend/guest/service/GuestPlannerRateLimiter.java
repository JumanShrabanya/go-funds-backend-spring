package com.js.gofunds_backend.guest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory one-plan-per-IP gate for the guest planner.
 *
 * <p>Deliberately not backed by a table: guest plans are not persisted either, so
 * a restart clearing the cooldown is acceptable. If guest plans ever become
 * durable, this needs to move to a shared store (Redis or a table) or every
 * instance behind a load balancer would hand out its own free plan.
 *
 * <p>A successful acquire returns a reservation token. Callers that fail after
 * acquiring must pass that token to {@link #release(String, Instant)} so a Gemini
 * outage does not burn the visitor's single attempt. Removal is conditional on
 * the token, so a release cannot clear a newer reservation.
 */
@Slf4j
@Component
public class GuestPlannerRateLimiter {

	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Duration cooldown;
	private final Clock clock;
	private final ConcurrentMap<String, Instant> lastUse = new ConcurrentHashMap<>();

	@Autowired
	public GuestPlannerRateLimiter(
			@Value("${guest.planner.cooldown:PT24H}") String cooldown) {
		this(Duration.parse(cooldown), Clock.systemUTC());
	}

	GuestPlannerRateLimiter(Duration cooldown, Clock clock) {
		this.cooldown = cooldown;
		this.clock = clock;
	}

	/**
	 * Atomically claims the IP's next slot.
	 *
	 * @return the reservation instant when allowed, empty when still in cooldown
	 */
	public Optional<Instant> tryAcquire(String clientIp) {
		Instant now = clock.instant();
		evictExpiredIfLarge(now);

		AtomicReference<Instant> reservation = new AtomicReference<>();
		lastUse.compute(clientIp, (ip, previous) -> {
			if (previous == null || !now.isBefore(previous.plus(cooldown))) {
				reservation.set(now);
				return now;
			}
			return previous;
		});

		return Optional.ofNullable(reservation.get());
	}

	/** Returns the slot after a failed generation. */
	public void release(String clientIp, Instant reservation) {
		lastUse.remove(clientIp, reservation);
		log.debug("Released guest planner reservation for {}", clientIp);
	}

	/**
	 * Keeps the map from growing without bound. This runs only once it is large,
	 * so the common path stays a single map operation.
	 */
	private void evictExpiredIfLarge(Instant now) {
		if (lastUse.size() < CLEANUP_THRESHOLD) {
			return;
		}
		lastUse.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().plus(cooldown)));
	}
}
