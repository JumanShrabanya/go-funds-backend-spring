package com.js.gofunds_backend.domain.repository;

import com.js.gofunds_backend.domain.entity.InvestmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentPlanRepository extends JpaRepository<InvestmentPlan, UUID> {

	List<InvestmentPlan> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

	Optional<InvestmentPlan> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
