package com.js.gofunds_backend.funds.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.repository.FundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pulls the AMFI "all open ended schemes" NAV text file and upserts it into the
 * {@code funds} table.
 *
 * <p>The feed is {@code ;}-delimited with eight columns:
 * <pre>
 * Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date
 * </pre>
 * interspersed with {@code Open Ended Schemes(Category)} section headers and
 * bare fund-house lines such as {@code Axis Mutual Fund}. Only rows whose
 * first column is numeric are data rows; the most recent bare line above a
 * data row is used as that scheme's fund house.
 *
 * <p>Only growth (or plan-less) options are ingested. IDCW / dividend options
 * are skipped because their NAV is not comparable with a growth NAV, which
 * would corrupt any return-based ranking. Rows with a NAV of zero or less are
 * skipped as well - AMFI uses those for wound-up segregated portfolios.
 */
@Slf4j
@Service
public class FundsSyncService {

	private static final String FILE_HEADER_PREFIX = "Scheme Code";
	private static final String SCHEME_CODE_PATTERN = "\\d+";
	private static final int COLUMN_COUNT = 8;
	private static final int SCHEME_CODE = 0;
	private static final int SCHEME_NAME = 3;
	private static final int OPTION = 5;
	private static final int NAV = 6;

	private final FundRepository fundRepository;
	private final FundClassifier fundClassifier;
	private final RestClient restClient;
	private final String navUrl;

	public FundsSyncService(
			FundRepository fundRepository,
			FundClassifier fundClassifier,
			@Value("${amfi.nav-url}") String navUrl) {
		this.fundRepository = fundRepository;
		this.fundClassifier = fundClassifier;
		this.navUrl = navUrl;
		this.restClient = RestClient.builder()
				.defaultHeader(HttpHeaders.ACCEPT, "text/plain")
				.requestFactory(requestFactory())
				.build();
	}

	@Transactional
	public void syncFromAmfi() {
		String payload = fetchNavFile();
		if (payload == null || payload.isBlank()) {
			throw new IllegalStateException(
					"AMFI NAV file came back empty from " + navUrl + ". Nothing was synced.");
		}

		// Loading the catalogue up front keeps the loop free of per-fund
		// lookups, and lets the existing rows stay managed so that the dirty
		// check flushes the NAV updates in batches instead of one merge each.
		Map<String, Fund> catalog = new HashMap<>();
		fundRepository.findAll().forEach(fund -> catalog.put(fund.getSchemeCode(), fund));

		LocalDateTime syncedAt = LocalDateTime.now();
		List<Fund> added = new ArrayList<>();
		int refreshed = 0;
		int skipped = 0;
		String fundHouse = null;

		for (String rawLine : payload.split("\\R")) {
			String line = rawLine.strip();
			if (line.isEmpty() || line.startsWith(FILE_HEADER_PREFIX)) {
				continue;
			}

			String[] columns = line.split(";", -1);
			if (columns.length < COLUMN_COUNT) {
				if (isFundHouse(line)) {
					fundHouse = line;
				}
				continue;
			}
			if (!columns[SCHEME_CODE].strip().matches(SCHEME_CODE_PATTERN)) {
				continue;
			}

			Optional<Fund> parsed = toFund(columns, fundHouse, syncedAt);
			if (parsed.isEmpty()) {
				skipped++;
				continue;
			}

			Fund incoming = parsed.get();
			Fund existing = catalog.get(incoming.getSchemeCode());
			if (existing == null) {
				catalog.put(incoming.getSchemeCode(), incoming);
				added.add(incoming);
			} else {
				refresh(existing, incoming);
				refreshed++;
			}
		}

		if (!added.isEmpty()) {
			fundRepository.saveAll(added);
		}
		fundRepository.flush();

		// A redirect page, an error page or a truncated response all parse to
		// zero rows and would otherwise be indistinguishable from a legitimately
		// unchanged feed. Fail loudly instead of reporting a successful no-op.
		if (added.isEmpty() && refreshed == 0) {
			throw new IllegalStateException(
					"AMFI sync matched no fund rows from " + navUrl + " (payload "
							+ (payload == null ? 0 : payload.length()) + " chars). "
							+ "Expected a 'Scheme Code;...;Net Asset Value;Date' header, so the "
							+ "feed layout probably changed or the response was not the NAV file.");
		}

		log.info("AMFI sync finished from {}: {} inserted, {} refreshed, {} rows skipped",
				navUrl, added.size(), refreshed, skipped);
	}

	String fetchNavFile() {
		log.info("Fetching AMFI NAV file from {}", navUrl);
		return restClient.get()
				.uri(navUrl)
				.retrieve()
				.body(String.class);
	}

	/**
	 * A bare line is a fund-house heading only if it is not one of the
	 * {@code Open Ended Schemes(...)} category headers.
	 */
	private boolean isFundHouse(String line) {
		return !line.contains("(") && !line.contains(")") && !line.contains(";");
	}

	private Optional<Fund> toFund(String[] columns, String fundHouse, LocalDateTime syncedAt) {
		String schemeCode = columns[SCHEME_CODE].strip();
		String schemeName = columns[SCHEME_NAME].strip();
		String option = columns[OPTION].strip();

		if (schemeName.isEmpty() || !isGrowthOrPlanless(option) || isUnclaimed(schemeName, option)) {
			return Optional.empty();
		}

		BigDecimal nav;
		try {
			nav = new BigDecimal(columns[NAV].strip());
		} catch (NumberFormatException ex) {
			return Optional.empty();
		}
		if (nav.signum() <= 0) {
			return Optional.empty();
		}

		Fund fund = new Fund();
		fund.setSchemeCode(schemeCode);
		fund.setSchemeName(schemeName);
		fund.setFundHouse(fundHouse);
		fund.setCurrentNav(nav);
		fund.setSupportsSip(true);
		fund.setSupportsLumpSum(true);
		fund.setLastSyncedAt(syncedAt);
		fundClassifier.classify(schemeName).applyTo(fund);
		return Optional.of(fund);
	}

	private boolean isGrowthOrPlanless(String option) {
		return option.isEmpty() || option.toLowerCase().contains("growth");
	}

	private boolean isUnclaimed(String schemeName, String option) {
		return option.toLowerCase().contains("unclaimed") || schemeName.toLowerCase().contains("unclaimed");
	}

	private void refresh(Fund target, Fund source) {
		target.setSchemeName(source.getSchemeName());
		target.setFundHouse(source.getFundHouse());
		target.setCurrentNav(source.getCurrentNav());
		target.setLastSyncedAt(source.getLastSyncedAt());
		fundClassifier.classify(source.getSchemeName()).applyTo(target);
	}

	/**
	 * AMFI serves {@code NAVAll.txt} behind a 302 to {@code portal.amfiindia.com}.
	 * The JDK {@code HttpClient} defaults to {@code Redirect.NEVER}, so without
	 * this the body is a 169-byte "Object Moved" HTML page that parses to zero
	 * rows - and {@code retrieve()} does not treat 3xx as an error, so the sync
	 * used to report "0 inserted" and look successful.
	 */
	private static JdkClientHttpRequestFactory requestFactory() {
		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(Duration.ofSeconds(30));
		return factory;
	}
}
