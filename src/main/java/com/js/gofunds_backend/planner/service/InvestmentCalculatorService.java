package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Deterministic SIP projection. The LLM picks funds and an allocation, but every
 * number the user is shown is computed here so the same inputs always produce the
 * same output.
 *
 * <p>Uses the future value of an annuity due, i.e. contributions are treated as
 * landing at the start of each month:
 *
 * <pre>FV = P x [((1 + r)^n - 1) / r] x (1 + r)</pre>
 *
 * where {@code P} is the monthly contribution, {@code r} the monthly rate and
 * {@code n} the number of months.
 */
@Service
public class InvestmentCalculatorService {

	private static final MathContext MC = MathContext.DECIMAL64;

	/** Representative years assumed for each horizon band. */
	private static final Map<InvestmentHorizon, Integer> HORIZON_YEARS = Map.of(
			InvestmentHorizon.LESS_THAN_3_YEARS, 2,
			InvestmentHorizon.THREE_TO_FIVE_YEARS, 4,
			InvestmentHorizon.FIVE_TO_TEN_YEARS, 7,
			InvestmentHorizon.MORE_THAN_10_YEARS, 15);

	/** Future value of {@code monthlyInvestment} invested for {@code horizon}. */
	public BigDecimal projectFutureValue(BigDecimal monthlyInvestment,
			BigDecimal annualReturnRate, InvestmentHorizon horizon) {

		int months = months(horizon);
		if (monthlyInvestment == null || monthlyInvestment.signum() <= 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}

		BigDecimal monthlyRate = monthlyRate(annualReturnRate);
		if (monthlyRate.signum() == 0) {
			// Guard the division below: a 0% return is simply P x n.
			return money(monthlyInvestment.multiply(BigDecimal.valueOf(months)));
		}

		BigDecimal growth = BigDecimal.ONE.add(monthlyRate)
				.pow(months, MC)
				.subtract(BigDecimal.ONE, MC)
				.divide(monthlyRate, MC)
				.multiply(BigDecimal.ONE.add(monthlyRate), MC);

		return money(monthlyInvestment.multiply(growth));
	}

	/** Total amount contributed over the horizon, excluding any returns. */
	public BigDecimal totalInvested(BigDecimal monthlyInvestment, InvestmentHorizon horizon) {
		if (monthlyInvestment == null || monthlyInvestment.signum() <= 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return money(monthlyInvestment.multiply(BigDecimal.valueOf(months(horizon))));
	}

	/** Profit = future value minus everything paid in. */
	public BigDecimal projectedReturns(BigDecimal monthlyInvestment,
			BigDecimal annualReturnRate, InvestmentHorizon horizon) {
		BigDecimal futureValue = projectFutureValue(monthlyInvestment, annualReturnRate, horizon);
		return money(futureValue.subtract(totalInvested(monthlyInvestment, horizon)));
	}

	/** Representative years for a horizon band. Bands are ranges, so a single point in the middle is used. */
	public int years(InvestmentHorizon horizon) {
		return HORIZON_YEARS.getOrDefault(horizon, 5);
	}

	private int months(InvestmentHorizon horizon) {
		return years(horizon) * 12;
	}

	/** Monthly rate as a fraction: {@code annualPercent / 100 / 12}. */
	private static BigDecimal monthlyRate(BigDecimal annualReturnRate) {
		if (annualReturnRate == null || annualReturnRate.signum() <= 0) {
			return BigDecimal.ZERO;
		}
		return annualReturnRate.divide(BigDecimal.valueOf(1200), MC);
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}
}