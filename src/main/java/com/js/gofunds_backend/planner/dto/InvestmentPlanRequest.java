package com.js.gofunds_backend.planner.dto;

import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to generate an investment plan.
 *
 * <p>{@code riskProfile} is optional: when omitted the model infers it. The
 * amount floor and ceiling stop a mistyped value producing an absurd plan.
 */
public record InvestmentPlanRequest(
		@NotNull
		@DecimalMin(value = "500.00", message = "monthlyInvestment must be at least 500")
		@DecimalMax(value = "10000000.00", message = "monthlyInvestment must not exceed 10000000")
		BigDecimal monthlyInvestment,

		@NotNull InvestmentGoal goal,

		@NotNull InvestmentHorizon investmentHorizon,

		RiskProfile riskProfile) {
}