package com.js.gofunds_backend.domain.repository;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundRepository extends JpaRepository<Fund, UUID>, JpaSpecificationExecutor<Fund> {

	Optional<Fund> findBySchemeCode(String schemeCode);

	List<Fund> findByMainCategory(FundMainCategory mainCategory);

	List<Fund> findByRiskLevelIn(Collection<RiskLevel> riskLevels);

	/**
	 * Funds in one category whose risk level is in the given set.
	 *
	 * <p>Used by the planner to build a shortlist. Deliberately unpaged - the
	 * caller caps the result - because paging here would truncate whole
	 * sub-categories and leave the shortlist unbalanced.
	 */
	List<Fund> findByMainCategoryAndRiskLevelIn(FundMainCategory mainCategory,
			Collection<RiskLevel> riskLevels);

	@Query("""
			select f from Fund f
			where f.mainCategory = :mainCategory
			and f.riskLevel in :riskLevels
			order by f.returnRate1Year desc nulls last
			""")
	List<Fund> findTopPerforming(@Param("mainCategory") FundMainCategory mainCategory,
			@Param("riskLevels") Collection<RiskLevel> riskLevels, Pageable pageable);

	Page<Fund> findBySchemeNameContainingIgnoreCase(String schemeName, Pageable pageable);
}
