package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestmentCalculatorServiceTest {

	private final InvestmentCalculatorService calculator = new InvestmentCalculatorService();

	@Test
	void zeroReturnYieldsExactlyTheContributions() {
		BigDecimal result = calculator.projectFutureValue(
				new BigDecimal("10000"), BigDecimal.ZERO, InvestmentHorizon.FIVE_TO_TEN_YEARS);

		// 7 years x 12 months x 10,000
		assertEquals(new BigDecimal("840000.00"), result);
	}

	@Test
	void nullReturnRateIsTreatedAsZero() {
		BigDecimal result = calculator.projectFutureValue(
				new BigDecimal("5000"), null, InvestmentHorizon.THREE_TO_FIVE_YEARS);

		assertEquals(new BigDecimal("240000.00"), result);
	}

	@Test
	void twelvePercentOverSevenYearsMatchesHandCalculatedFutureValue() {
		// FV = 10000 x [((1 + 0.01)^84 - 1) / 0.01] x 1.01 = 1,319,789.97
		BigDecimal result = calculator.projectFutureValue(
				new BigDecimal("10000"), new BigDecimal("12"), InvestmentHorizon.FIVE_TO_TEN_YEARS);

		assertEquals(new BigDecimal("1319789.97"), result);
	}

	@Test
	void higherReturnProducesAHigherFutureValue() {
		BigDecimal low = calculator.projectFutureValue(
				new BigDecimal("10000"), new BigDecimal("8"), InvestmentHorizon.MORE_THAN_10_YEARS);
		BigDecimal high = calculator.projectFutureValue(
				new BigDecimal("10000"), new BigDecimal("14"), InvestmentHorizon.MORE_THAN_10_YEARS);

		assertTrue(high.compareTo(low) > 0, "a higher rate must compound to more");
	}

	@Test
	void longerHorizonProducesAHigherFutureValue() {
		BigDecimal shorter = calculator.projectFutureValue(
				new BigDecimal("10000"), new BigDecimal("12"), InvestmentHorizon.THREE_TO_FIVE_YEARS);
		BigDecimal longer = calculator.projectFutureValue(
				new BigDecimal("10000"), new BigDecimal("12"), InvestmentHorizon.MORE_THAN_10_YEARS);

		assertTrue(longer.compareTo(shorter) > 0);
	}

	@Test
	void horizonBandsMapToTheirRepresentativeYears() {
		assertEquals(2, calculator.years(InvestmentHorizon.LESS_THAN_3_YEARS));
		assertEquals(4, calculator.years(InvestmentHorizon.THREE_TO_FIVE_YEARS));
		assertEquals(7, calculator.years(InvestmentHorizon.FIVE_TO_TEN_YEARS));
		assertEquals(15, calculator.years(InvestmentHorizon.MORE_THAN_10_YEARS));
	}

	@Test
	void totalInvestedIsMonthlyTimesMonths() {
		BigDecimal invested = calculator.totalInvested(
				new BigDecimal("10000"), InvestmentHorizon.LESS_THAN_3_YEARS);

		assertEquals(new BigDecimal("240000.00"), invested);
	}

	@Test
	void projectedReturnsAreFutureValueLessContributions() {
		BigDecimal returns = calculator.projectedReturns(
				new BigDecimal("10000"), new BigDecimal("12"), InvestmentHorizon.FIVE_TO_TEN_YEARS);

		// 1,319,789.97 - 840,000.00
		assertEquals(new BigDecimal("479789.97"), returns);
	}

	@Test
	void negativeMonthlyInvestmentIsRejectedRatherThanCompounded() {
		BigDecimal result = calculator.projectFutureValue(
				new BigDecimal("-500"), new BigDecimal("12"), InvestmentHorizon.FIVE_TO_TEN_YEARS);

		assertEquals(0, result.signum());
	}

	@Test
	void moneyIsAlwaysScaledToTwoDecimals() {
		BigDecimal result = calculator.projectFutureValue(
				new BigDecimal("777.77"), new BigDecimal("11.5"), InvestmentHorizon.THREE_TO_FIVE_YEARS);

		assertEquals(2, result.scale());
	}
}