package com.js.gofunds_backend.funds.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundsQueryService {

	private final FundRepository fundRepository;

	public Page<Fund> findAll(FundMainCategory mainCategory, RiskLevel riskLevel, String search, Pageable pageable) {
		Specification<Fund> spec = buildSpec(mainCategory, riskLevel, search);
		return spec == null ? fundRepository.findAll(pageable) : fundRepository.findAll(spec, pageable);
	}

	public Fund findBySchemeCode(String schemeCode) {
		return fundRepository.findBySchemeCode(schemeCode).orElse(null);
	}

	private Specification<Fund> buildSpec(FundMainCategory mainCategory, RiskLevel riskLevel, String search) {
		Specification<Fund> spec = null;

		if (mainCategory != null) {
			spec = and(spec, (root, query, cb) -> cb.equal(root.get("mainCategory"), mainCategory));
		}
		if (riskLevel != null) {
			spec = and(spec, (root, query, cb) -> cb.equal(root.get("riskLevel"), riskLevel));
		}
		if (search != null && !search.isBlank()) {
			String pattern = "%" + search.strip().toLowerCase() + "%";
			spec = and(spec, (root, query, cb) -> cb.or(
					cb.like(cb.lower(root.get("schemeName")), pattern),
					cb.like(cb.lower(cb.coalesce(root.get("fundHouse"), "")), pattern)));
		}
		return spec;
	}

	private static Specification<Fund> and(Specification<Fund> left, Specification<Fund> right) {
		return left == null ? right : left.and(right);
	}
}
