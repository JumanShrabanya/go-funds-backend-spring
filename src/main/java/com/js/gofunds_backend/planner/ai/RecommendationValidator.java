package com.js.gofunds_backend.planner.ai;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Checks the model's answer before any of it is trusted or persisted.
 *
 * <p>Models routinely drift from the contract: allocations that total 99,
 * duplicated or hallucinated fund ids, a missing name, a {@code null} rate.
 * None of that deserves a 500, so each case is either repaired or rejected with
 * a clear message. Anything that cannot be made safe is a 502, since the model
 * is at fault rather than the client.
 */
@Component
public class RecommendationValidator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal MAX_RETURN_RATE = BigDecimal.valueOf(50);
	private static final int MIN_FUNDS = 3;

	/**
	 * Floor on the share of the allocation that must survive filtering. Below this
	 * the model understood the contract so poorly that redistributing its numbers
	 * would be inventing a portfolio on its behalf.
	 */
	private static final BigDecimal MIN_RECOVERABLE_TOTAL = BigDecimal.valueOf(10);

	/** Drift tolerated on the descriptive category split, which is not rescaled. */
	private static final BigDecimal ASSET_ALLOCATION_TOLERANCE = BigDecimal.valueOf(1);

	/**
	 * Validates and normalises a parsed recommendation against the catalogue it
	 * was given.
	 *
	 * @param catalogue the exact funds listed in the prompt
	 * @return the same recommendation with nulls filled in and allocations
	 *         rescaled to total exactly 100
	 * @throws ApiException 502 if the answer cannot be made usable
	 */
	public Recommendation validate(Recommendation recommendation, List<Fund> catalogue) {
		if (recommendation == null) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model returned no recommendation");
		}
		if (recommendation.selectedFunds() == null || recommendation.selectedFunds().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model recommended no funds");
		}

		Map<UUID, Fund> catalogueById = new HashMap<>();
		for (Fund fund : catalogue) {
			catalogueById.put(fund.getId(), fund);
		}

		Map<UUID, Recommendation.SelectedFund> usable = resolve(recommendation.selectedFunds(), catalogueById);

		if (usable.size() < MIN_FUNDS) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model recommended only " + usable.size()
							+ " usable funds; at least " + MIN_FUNDS + " are required");
		}

		rescaleToOneHundred(usable);
		validateAssetAllocation(recommendation.assetAllocation());
		validateReturnRate(recommendation.blendedReturnRate());

		return new Recommendation(
				recommendation.riskProfile() == null ? RiskProfile.MODERATE : recommendation.riskProfile(),
				recommendation.assetAllocation(),
				List.copyOf(usable.values()),
				recommendation.blendedReturnRate(),
				recommendation.explanation() == null ? "" : recommendation.explanation().trim());
	}

	/**
	 * Drops hallucinated ids, collapses duplicates, and back-fills missing names.
	 *
	 * <p>Fund names are read from the catalogue rather than kept from the model,
	 * so a plan always reflects the catalogue as it actually stands.
	 */
	private Map<UUID, Recommendation.SelectedFund> resolve(
			List<Recommendation.SelectedFund> selected, Map<UUID, Fund> catalogueById) {

		Map<UUID, Recommendation.SelectedFund> usable = new LinkedHashMap<>();

		for (Recommendation.SelectedFund entry : selected) {
			Fund fund = entry.fundId() == null ? null : catalogueById.get(entry.fundId());
			if (fund == null) {
				// An id we never sent: the model invented it. Dropping beats
				// failing the whole plan when the rest of the answer is sound.
				continue;
			}

			BigDecimal percentage = entry.allocationPercentage();
			if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(HUNDRED) > 0) {
				continue;
			}

			BigDecimal normalised = percentage.setScale(2, RoundingMode.HALF_UP);
			if (normalised.signum() <= 0) {
				continue;
			}

			// First occurrence wins, so a duplicate cannot double-count.
			usable.putIfAbsent(fund.getId(),
					new Recommendation.SelectedFund(fund.getId(), fund.getSchemeName(), normalised));
		}
		return usable;
	}

	/**
	 * Rescales the surviving allocations in place so they total exactly 100.
	 *
	 * <p>This is what absorbs drift from any cause: the model rounding, and
	 * entries dropped above because their id was hallucinated or their percentage
	 * unusable. Redistribution is proportional to what the model actually asked
	 * for, so a dropped 10% holding is spread across the rest rather than
	 * silently vanishing.
	 *
	 * @throws ApiException 502 if too little of the allocation survived to
	 *         redistribute meaningfully
	 */
	private void rescaleToOneHundred(Map<UUID, Recommendation.SelectedFund> usable) {
		BigDecimal total = usable.values().stream()
				.map(Recommendation.SelectedFund::allocationPercentage)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		if (total.signum() <= 0) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model allocated 0% of the portfolio");
		}
		if (total.compareTo(MIN_RECOVERABLE_TOTAL) < 0) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model allocated only "
							+ total.stripTrailingZeros().toPlainString()
							+ "% of the portfolio across usable funds");
		}

		BigDecimal factor = HUNDRED.divide(total, 6, RoundingMode.HALF_UP);
		BigDecimal runningTotal = BigDecimal.ZERO;
		UUID largestId = null;
		BigDecimal largest = null;

		for (Recommendation.SelectedFund entry : usable.values()) {
			BigDecimal scaled = entry.allocationPercentage().multiply(factor)
					.setScale(2, RoundingMode.HALF_UP);
			usable.put(entry.fundId(),
					new Recommendation.SelectedFund(entry.fundId(), entry.fundName(), scaled));
			runningTotal = runningTotal.add(scaled);

			if (largest == null || scaled.compareTo(largest) > 0) {
				largestId = entry.fundId();
				largest = scaled;
			}
		}

		// Hand the rounding remainder to the biggest holding so the total lands on
		// exactly 100.00.
		BigDecimal remainder = HUNDRED.subtract(runningTotal);
		if (remainder.signum() != 0 && largestId != null) {
			Recommendation.SelectedFund biggest = usable.get(largestId);
			usable.put(largestId, new Recommendation.SelectedFund(largestId, biggest.fundName(),
					largest.add(remainder).setScale(2, RoundingMode.HALF_UP)));
		}
	}

	/**
	 * Checks the category split is a real allocation rather than a guess.
	 *
	 * <p>Unlike the fund allocations this is not rescaled - it is descriptive
	 * metadata, and a model that cannot add three numbers to 100 has not followed
	 * the contract closely enough to trust the rest of its reasoning. Drift of 1
	 * is tolerated because rounding produces it routinely.
	 */
	private void validateAssetAllocation(Recommendation.AssetAllocation allocation) {
		if (allocation == null || allocation.equity() == null || allocation.debt() == null
				|| allocation.hybrid() == null) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model returned an incomplete asset allocation");
		}

		for (Integer value : List.of(allocation.equity(), allocation.debt(), allocation.hybrid())) {
			if (value < 0 || value > 100) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"The AI model returned an out-of-range asset allocation");
			}
		}

		BigDecimal total = BigDecimal.valueOf(allocation.equity())
				.add(BigDecimal.valueOf(allocation.debt()))
				.add(BigDecimal.valueOf(allocation.hybrid()));

		if (HUNDRED.subtract(total).abs().compareTo(ASSET_ALLOCATION_TOLERANCE) > 0) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model asset allocation totals " + total.toPlainString() + "% instead of 100%");
		}
	}

	private void validateReturnRate(BigDecimal rate) {
		if (rate == null || rate.signum() < 0 || rate.compareTo(MAX_RETURN_RATE) > 0) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model returned an implausible expected return of " + rate);
		}
	}
}
