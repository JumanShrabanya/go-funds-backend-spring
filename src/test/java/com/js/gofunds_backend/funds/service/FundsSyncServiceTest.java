package com.js.gofunds_backend.funds.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.repository.FundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundsSyncServiceTest {

	private static final String HEADER =
			"Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;"
					+ "Scheme Name;Plan;Option;Net Asset Value;Date";

	/** Must match the funds.current_nav column and {@code Fund#currentNav}. */
	private static final int CURRENT_NAV_PRECISION = 18;
	private static final int CURRENT_NAV_SCALE = 4;

	@Mock
	private FundRepository fundRepository;

	@Test
	void ingestsGrowthOptionsAndDerivesFundHouse() {
		String payload = String.join("\n",
				HEADER,
				" ",
				"Open Ended Schemes(Children's Fund)",
				"Axis Mutual Fund",
				" ",
				"135762;INF1;INF2;Axis Children's Fund;Direct Plan;Growth Option;29.3632;28-Sep-2026",
				"135763;INF3;INF4;Axis Liquid Fund;Direct Plan;Growth Option;2.5000;28-Sep-2026",
				// IDCW is skipped: its NAV is not comparable with a growth NAV.
				"135763;INF3;INF5;Axis Liquid Fund;Direct Plan;IDCW Option;2.4000;28-Sep-2026");

		service(payload).syncFromAmfi();

		List<Fund> saved = captureSaved();
		assertEquals(2, saved.size(), "only growth options should be ingested");

		Fund child = saved.get(0);
		assertEquals("135762", child.getSchemeCode());
		assertEquals("Axis Mutual Fund", child.getFundHouse());
		assertEquals("29.3632", child.getCurrentNav().toPlainString());
		assertEquals(FundMainCategory.EQUITY, child.getMainCategory());
		assertEquals(FundSubCategory.LARGE_CAP, child.getSubCategory());

		Fund liquid = saved.get(1);
		assertEquals(FundMainCategory.DEBT, liquid.getMainCategory());
		assertEquals(FundSubCategory.LIQUID, liquid.getSubCategory());
		assertEquals(RiskLevel.LOW, liquid.getRiskLevel());
	}

	@Test
	void skipsZeroNavUsedByWoundUpPortfolios() {
		String payload = String.join("\n",
				HEADER,
				"Franklin India Mutual Fund",
				"111111;INF1;INF2;Franklin India Ultra Short Bond Fund;Direct;Growth Option;0.0000;28-Sep-2026",
				"111112;INF3;INF4;Franklin India Overnight Fund;Direct;Growth Option;10.0000;28-Sep-2026");

		service(payload).syncFromAmfi();

		List<Fund> saved = captureSaved();
		assertEquals(1, saved.size());
		assertEquals("111112", saved.get(0).getSchemeCode());
	}

	/**
	 * A redirect page or an error page used to parse to zero rows and log a
	 * successful "0 inserted, 0 refreshed" no-op.
	 */
	@Test
	void failsLoudlyWhenThePayloadIsNotTheNavFeed() {
		String objectMoved = "<head><title>Document Moved</title></head>"
				+ "<body><h1>Object Moved</h1>This document may be found "
				+ "<a HREF=\"https://portal.amfiindia.com/spages/NAVAll.txt\">here</a></body>";

		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> service(objectMoved).syncFromAmfi());
		assertEquals(true, ex.getMessage().contains("matched no fund rows"));
	}

	/**
	 * funds.current_nav is NUMERIC(18,4). It used to be NUMERIC(10,4), which caps
	 * the value at 999,999.9999, and the real feed publishes 12 schemes above
	 * 1,000,000 (max 2,540,201.9508). One such row aborted the entire seed batch
	 * with SQLState 22003 and, because the sync writes in a single transaction,
	 * threw away all ~9,200 good rows too. This asserts the largest NAV actually
	 * seen in the feed still fits the declared precision and scale.
	 */
	@Test
	void keepsNavsThatExceedTheOldNumericTenFourCeiling() {
		String payload = String.join("\n",
				HEADER,
				"ICICI Prudential Mutual Fund",
				// Real values from the live AMFI feed.
				"130565;INF1;INF2;IL&FS Infrastructure Debt Fund Series 1A;Direct Plan;Growth Option;1680494.0463;30-Sep-2026",
				"130566;INF3;INF4;IL&FS Infrastructure Debt Fund Series 1B;Direct Plan;Growth Option;2540201.9508;30-Sep-2026");

		service(payload).syncFromAmfi();

		List<Fund> saved = captureSaved();
		assertEquals(2, saved.size());

		Fund largest = saved.get(1);
		assertEquals("2540201.9508", largest.getCurrentNav().toPlainString());
		assertFitsCurrentNavColumn(largest.getCurrentNav());
		assertFitsCurrentNavColumn(saved.get(0).getCurrentNav());
	}

	/**
	 * Mirrors the PostgreSQL rule behind SQLState 22003: a NUMERIC(p, s) value
	 * must round to an absolute value below 10^(p - s). Keep
	 * {@link #CURRENT_NAV_PRECISION} in step with the
	 * {@code current_nav} column and with {@code Fund#currentNav}.
	 */
	private static void assertFitsCurrentNavColumn(BigDecimal nav) {
		BigDecimal limit = BigDecimal.TEN.pow(CURRENT_NAV_PRECISION - CURRENT_NAV_SCALE);
		assertTrue(nav.abs().compareTo(limit) < 0, "NAV " + nav + " overflows NUMERIC("
				+ CURRENT_NAV_PRECISION + "," + CURRENT_NAV_SCALE + ") (must be < " + limit + ")");
	}

	@Test
	void failsLoudlyOnAnEmptyPayload() {
		FundsSyncService service = new FundsSyncService(fundRepository, new FundClassifier(), "http://localhost") {
			@Override
			String fetchNavFile() {
				return "";
			}
		};

		IllegalStateException ex = assertThrows(IllegalStateException.class, service::syncFromAmfi);
		assertEquals(true, ex.getMessage().contains("came back empty"));
	}

	private FundsSyncService service(String payload) {
		when(fundRepository.findAll()).thenReturn(List.of());
		return new FundsSyncService(fundRepository, new FundClassifier(), "http://localhost/stub") {
			@Override
			String fetchNavFile() {
				return payload;
			}
		};
	}

	@SuppressWarnings("unchecked")
	private List<Fund> captureSaved() {
		ArgumentCaptor<List<Fund>> captor = ArgumentCaptor.forClass(List.class);
		verify(fundRepository).saveAll(captor.capture());
		return captor.getValue();
	}
}
