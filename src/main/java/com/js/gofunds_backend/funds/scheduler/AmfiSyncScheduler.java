package com.js.gofunds_backend.funds.scheduler;

import com.js.gofunds_backend.funds.service.FundsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmfiSyncScheduler {

	private final FundsSyncService fundsSyncService;

	/**
	 * Runs daily at 00:30 UTC (06:00 IST), after AMFI publishes the previous
	 * business day's NAVs.
	 */
	@Scheduled(cron = "${amfi.sync-cron}")
	public void dailyNavSync() {
		log.info("Starting scheduled AMFI NAV sync");
		try {
			fundsSyncService.syncFromAmfi();
			log.info("Scheduled AMFI NAV sync completed");
		} catch (Exception ex) {
			log.error("Scheduled AMFI NAV sync failed", ex);
		}
	}
}
