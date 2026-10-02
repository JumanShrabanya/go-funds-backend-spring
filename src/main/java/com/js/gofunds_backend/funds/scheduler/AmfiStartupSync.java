package com.js.gofunds_backend.funds.scheduler;

import com.js.gofunds_backend.funds.service.FundsSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * One-off catalogue seeding for development, where waiting for the daily cron to
 * fire is inconvenient.
 *
 * <p>Disabled by default. The daily {@link AmfiSyncScheduler} remains the source
 * of truth for NAV refreshes, so turning this on in production only means an
 * extra idempotent upsert on every restart - leave it off there.
 *
 * <p>Listens for {@link ApplicationReadyEvent} rather than implementing
 * {@code ApplicationRunner} so that a slow download does not delay the
 * application becoming ready to serve traffic.
 */
@Slf4j
@Component
public class AmfiStartupSync {

	private final FundsSyncService fundsSyncService;
	private final boolean enabled;

	public AmfiStartupSync(
			FundsSyncService fundsSyncService,
			@Value("${funds.sync-on-startup:false}") boolean enabled) {
		this.fundsSyncService = fundsSyncService;
		this.enabled = enabled;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void syncOnStartup() {
		if (!enabled) {
			return;
		}
		log.info("funds.sync-on-startup is enabled, running the initial AMFI sync");
		try {
			fundsSyncService.syncFromAmfi();
			log.info("Initial AMFI sync completed");
		} catch (Exception ex) {
			// The application is already serving traffic at this point, so a
			// failed seed must not take it down.
			log.error("Initial AMFI sync failed", ex);
		}
	}
}
