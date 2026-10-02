package com.js.gofunds_backend.domain.entity;

import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "funds")
@Getter
@Setter
@NoArgsConstructor
public class Fund {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "scheme_code", nullable = false, unique = true)
	private String schemeCode;

	@Column(name = "scheme_name", nullable = false)
	private String schemeName;

	@Column(name = "fund_house")
	private String fundHouse;

	@Enumerated(EnumType.STRING)
	@Column(name = "main_category", nullable = false)
	private FundMainCategory mainCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "sub_category", nullable = false)
	private FundSubCategory subCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_level", nullable = false)
	private RiskLevel riskLevel;

	// NUMERIC(10,4) capped NAV at 999,999.9999, but AMFI publishes 12 schemes
	// above 1,000,000 (max 2,540,201.9508), which aborted the whole seed batch.
	// Keep this in step with V2__widen_fund_current_nav.sql.
	@Column(name = "current_nav", nullable = false, precision = 18, scale = 4)
	private BigDecimal currentNav;

	@Column(name = "return_rate_1_year", precision = 10, scale = 4)
	private BigDecimal returnRate1Year;

	@Column(name = "return_rate_3_year", precision = 10, scale = 4)
	private BigDecimal returnRate3Year;

	@Column(name = "return_rate_5_year", precision = 10, scale = 4)
	private BigDecimal returnRate5Year;

	@Column(name = "supports_sip", nullable = false)
	private boolean supportsSip = true;

	@Column(name = "supports_lump_sum", nullable = false)
	private boolean supportsLumpSum = true;

	@Column(name = "fact_sheet", columnDefinition = "text")
	private String factSheet;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "last_synced_at", nullable = false)
	private LocalDateTime lastSyncedAt;
}
