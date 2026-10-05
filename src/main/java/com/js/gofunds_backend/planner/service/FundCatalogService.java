package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.domain.repository.FundRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the shortlist of funds handed to the model.
 *
 * <p>Two decisions worth knowing about:
 *
 * <ul>
 *   <li><b>Not ranked by return.</b> The AMFI feed carries no return history, so
 *       {@code returnRate1Year} is null for every row and ordering by it would be
 *       arbitrary. Funds are ordered by sub-category and then by scheme name, so
 *       the same request always produces the same shortlist.
 *   <li><b>Capped per sub-category.</b> Without a cap, a request that allows
 *       equity would hand over ~5,660 large caps and blow the token budget. A
 *       small, balanced set gives the model a realistic choice instead.
 * </ul>
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
	private final int catalogSize;

	public FundCatalogService(FundRepository fundRepository,
			@Value("${planner.catalog.size:30}") int catalogSize) {
		this.fundRepository = fundRepository;
		this.catalogSize = catalogSize;
	}

	/**
	 * @return up to {@code planner.catalog.size} funds eligible for this profile
	 *         and horizon, spread across sub-categories. Never {@code null}, but
	 *         empty when the catalogue has not been synced.
	 */
	@Transactional(readOnly = true)
	public List<Fund> eligibleFunds(RiskProfile profile, InvestmentHorizon horizon) {
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
			log.warn("No funds eligible for profile {} - is the catalogue synced?", profile);
			return List.of();
		}

		List<Fund> shortlist = balance(eligible.values());
		log.debug("Shortlisted {} of {} eligible funds for {}", shortlist.size(), eligible.size(), profile);
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
	 * Picks up to {@code catalogSize} funds, bounding how many come from any one
	 * sub-category.
	 *
	 * <p>The cap is {@code catalogSize / distinctSubCategories} rather than a fixed
	 * fraction, so the shortlist is only trimmed when it would otherwise be
	 * dominated. When just one sub-category is eligible the cap equals the whole
	 * budget and nothing is needlessly withheld from the model.
	 *
	 * <p>Buckets are visited in {@link FundSubCategory} order and sorted by scheme
	 * name, so the same request always produces the same shortlist.
	 */
	private List<Fund> balance(Collection<Fund> eligible) {
		Map<FundSubCategory, List<Fund>> bySubCategory = new EnumMap<>(FundSubCategory.class);
		for (Fund fund : eligible) {
			bySubCategory.computeIfAbsent(fund.getSubCategory(), key -> new ArrayList<>()).add(fund);
		}
		bySubCategory.values().forEach(bucket -> bucket.sort(Comparator.comparing(Fund::getSchemeName)));

		int capPerSubCategory = Math.max(1, catalogSize / bySubCategory.size());

		return bySubCategory.values().stream()
				.flatMap(bucket -> bucket.stream().limit(capPerSubCategory))
				.limit(catalogSize)
				.toList();
	}
}
