package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.domain.repository.FundRepository;
import com.js.gofunds_backend.planner.service.GoalStrategy.PortfolioPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the shortlist of funds handed to the model.
 *
 * <p>Three decisions worth knowing about:
 *
 * <ul>
 *   <li><b>Goal-driven, not just risk-driven.</b> The fund sleeves are chosen by
 *       {@link GoalStrategy} from the investor's goal, so a tax-saving request is
 *       offered ELSS funds and a home-purchase request is offered short-duration
 *       debt, instead of both receiving the same generic equity list. Risk and
 *       horizon still filter the risk levels within those sleeves.
 *   <li><b>Fallbacks only top up.</b> If the goal's preferred sleeves cannot fill
 *       the budget the remaining sleeves are appended, but the preferred sleeves
 *       keep their full share of the budget rather than being diluted.
 *   <li><b>Spread across fund houses.</b> Within a sleeve, one fund per fund
 *       house is taken first, so the model chooses between managers rather than
 *       between five flavours of the same AMC.
 * </ul>
 *
 * <p>Not ranked by return: the AMFI feed carries no return history, so
 * {@code returnRate1Year} is null for every row and ordering by it would be
 * arbitrary. Everything is ordered deterministically so the same request always
 * produces the same shortlist.
 */
@Slf4j
@Service
public class FundCatalogService {

	/** Risk levels each profile may be recommended from. */
	private static final Map<RiskProfile, Set<RiskLevel>> ALLOWED_RISK = new EnumMap<>(Map.of(
			RiskProfile.CONSERVATIVE, Set.of(RiskLevel.LOW, RiskLevel.LOW_TO_MODERATE),
			RiskProfile.MODERATE, Set.of(RiskLevel.LOW_TO_MODERATE, RiskLevel.MODERATE, RiskLevel.HIGH),
			RiskProfile.AGGRESSIVE, Set.of(RiskLevel.MODERATE, RiskLevel.HIGH, RiskLevel.VERY_HIGH)));

	private final FundRepository fundRepository;
	private final GoalStrategy goalStrategy;
	private final int catalogSize;

	public FundCatalogService(FundRepository fundRepository,
			GoalStrategy goalStrategy,
			@Value("${planner.catalog.size:30}") int catalogSize) {
		this.fundRepository = fundRepository;
		this.goalStrategy = goalStrategy;
		this.catalogSize = catalogSize;
	}

	/**
	 * @return up to {@code planner.catalog.size} funds eligible for this goal,
	 *         profile and horizon, weighted towards the goal's relevant sleeves.
	 *         Never {@code null}, but empty when the catalogue has not been synced.
	 */
	@Transactional(readOnly = true)
	public List<Fund> eligibleFunds(InvestmentGoal goal, RiskProfile profile, InvestmentHorizon horizon) {
		Set<RiskLevel> riskLevels = allowedRiskLevels(profile, horizon);
		Collection<FundMainCategory> categories = allowedCategories(profile);

		// Keyed by id so a fund matched by more than one category query is
		// shortlisted once.
		Map<UUID, Fund> eligible = new LinkedHashMap<>();
		for (FundMainCategory category : categories) {
			for (Fund fund : fundRepository.findByMainCategoryAndRiskLevelIn(category, riskLevels)) {
				eligible.putIfAbsent(fund.getId(), fund);
			}
		}

		if (eligible.isEmpty()) {
			log.warn("No funds eligible for goal {} profile {} - is the catalogue synced?", goal, profile);
			return List.of();
		}

		PortfolioPolicy policy = goalStrategy.policyFor(goal, profile, horizon);
		List<Fund> shortlist = balance(eligible.values(), policy);
		log.debug("Shortlisted {} of {} eligible funds for goal {} profile {}",
				shortlist.size(), eligible.size(), goal, profile);
		return shortlist;
	}

	private Set<RiskLevel> allowedRiskLevels(RiskProfile profile, InvestmentHorizon horizon) {
		Set<RiskLevel> levels = ALLOWED_RISK.getOrDefault(profile,
				Set.of(RiskLevel.LOW_TO_MODERATE, RiskLevel.MODERATE));

		if (horizon != InvestmentHorizon.LESS_THAN_3_YEARS) {
			return levels;
		}
		// A short horizon rules out the volatile tail regardless of appetite.
		return levels.stream()
				.filter(level -> level != RiskLevel.HIGH && level != RiskLevel.VERY_HIGH)
				.collect(Collectors.toUnmodifiableSet());
	}

	/** Aggressive investors are steered away from pure debt funds. */
	private Collection<FundMainCategory> allowedCategories(RiskProfile profile) {
		return profile == RiskProfile.AGGRESSIVE
				? Set.of(FundMainCategory.EQUITY, FundMainCategory.HYBRID)
				: Set.of(FundMainCategory.EQUITY, FundMainCategory.HYBRID, FundMainCategory.DEBT);
	}

	/**
	 * Fills the budget from the goal's preferred sleeves first, topping up from
	 * the remaining sleeves only when those cannot fill it.
	 *
	 * <p>Buckets are ordered by {@link FundSubCategory} declaration order and
	 * sorted by scheme name, so the same request always produces the same list.
	 */
	private List<Fund> balance(Collection<Fund> eligible, PortfolioPolicy policy) {
		Map<FundSubCategory, List<Fund>> bySubCategory = new EnumMap<>(FundSubCategory.class);
		for (Fund fund : eligible) {
			bySubCategory.computeIfAbsent(fund.getSubCategory(), key -> new ArrayList<>()).add(fund);
		}
		bySubCategory.values().forEach(bucket -> bucket.sort(Comparator.comparing(Fund::getSchemeName)));

		List<FundSubCategory> preferred = policy.prioritizedSubCategories().stream()
				.filter(bySubCategory::containsKey)
				.toList();
		// No goal-preferred sleeve survives the risk filter: fall back to the whole
		// eligible set rather than returning nothing.
		List<FundSubCategory> primary = preferred.isEmpty()
				? new ArrayList<>(bySubCategory.keySet())
				: preferred;

		List<Fund> shortlist = new ArrayList<>(catalogSize);
		int capPerPrimary = Math.max(1, catalogSize / primary.size());
		for (FundSubCategory sleeve : primary) {
			addUpTo(shortlist, bySubCategory.get(sleeve), capPerPrimary);
		}

		if (shortlist.size() < catalogSize) {
			List<FundSubCategory> fallback = bySubCategory.keySet().stream()
					.filter(sleeve -> !primary.contains(sleeve))
					.toList();
			if (!fallback.isEmpty()) {
				int capPerFallback = Math.max(1, (catalogSize - shortlist.size()) / fallback.size());
				for (FundSubCategory sleeve : fallback) {
					addUpTo(shortlist, bySubCategory.get(sleeve), capPerFallback);
				}
			}
		}

		return shortlist.size() > catalogSize ? shortlist.subList(0, catalogSize) : shortlist;
	}

	/**
	 * Appends at most {@code limit} funds from one sleeve, preferring a fund from
	 * each distinct fund house before repeating a house. Stops early once the
	 * overall shortlist is full so no sleeve can overrun the budget.
	 */
	private void addUpTo(List<Fund> shortlist, List<Fund> bucket, int limit) {
		if (bucket == null || bucket.isEmpty()) {
			return;
		}
		List<Fund> chosen = new ArrayList<>(limit);
		Set<String> houses = new HashSet<>();

		for (Fund fund : bucket) {
			if (chosen.size() >= limit || shortlist.size() + chosen.size() >= catalogSize) {
				break;
			}
			if (houses.add(houseKey(fund))) {
				chosen.add(fund);
			}
		}
		// Fill any remaining slots with same-house funds only if still under budget.
		for (Fund fund : bucket) {
			if (chosen.size() >= limit || shortlist.size() + chosen.size() >= catalogSize) {
				break;
			}
			if (!chosen.contains(fund)) {
				chosen.add(fund);
			}
		}
		shortlist.addAll(chosen);
	}

	private static String houseKey(Fund fund) {
		return fund.getFundHouse() == null ? "" : fund.getFundHouse().toLowerCase(java.util.Locale.ROOT);
	}
}
