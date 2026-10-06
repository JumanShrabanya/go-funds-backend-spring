package com.js.gofunds_backend.planner.dto;

import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Investor profile to plan for")
public record InvestmentPlanRequest(
		@NotNull
		@DecimalMin(value = "500.00", message = "monthlyInvestment must be at least 500")
		@DecimalMax(value = "10000000.00", message = "monthlyInvestment must not exceed 10000000")
		@Schema(description = "Monthly SIP in rupees. This is the total that gets split across the "
				+ "recommended funds, not the amount per fund.",
				example = "25000")
		BigDecimal monthlyInvestment,

		@NotNull
		@Schema(description = "`TAX_SAVING` biases the shortlist towards ELSS funds, which carry a "
				+ "lock-in that the other goals do not.",
				example = "WEALTH_CREATION")
		InvestmentGoal goal,

		@NotNull
		@Schema(description = "Also caps the risk level of eligible funds: a short horizon excludes "
				+ "`VERY_HIGH` risk regardless of the risk profile.",
				example = "FIVE_TO_TEN_YEARS")
		InvestmentHorizon investmentHorizon,

		@Schema(description = "Optional. Omit it and Gemini infers one from the goal and horizon.",
				example = "MODERATE")
		RiskProfile riskProfile) {
}
