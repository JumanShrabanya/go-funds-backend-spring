package com.js.gofunds_backend.domain.entity;

import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.PlanStatus;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "investment_plans")
@Getter
@Setter
@NoArgsConstructor
public class InvestmentPlan {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvestmentGoal goal;

	@Enumerated(EnumType.STRING)
	@Column(name = "investment_horizon", nullable = false)
	private InvestmentHorizon investmentHorizon;

	@Column(name = "monthly_amount", nullable = false, precision = 15, scale = 2)
	private BigDecimal monthlyAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "risk_profile", nullable = false)
	private RiskProfile riskProfile;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "allocation_breakdown", columnDefinition = "jsonb")
	private Map<String, Object> allocationBreakdown;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recommended_funds", columnDefinition = "jsonb")
	private List<Map<String, Object>> recommendedFunds;

	@Column(name = "projected_returns", precision = 15, scale = 2)
	private BigDecimal projectedReturns;

	@Column(columnDefinition = "text")
	private String explanation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlanStatus status = PlanStatus.ACTIVE;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;
}
