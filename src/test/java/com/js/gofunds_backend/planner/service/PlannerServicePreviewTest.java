package com.js.gofunds_backend.planner.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.InvestmentGoal;
import com.js.gofunds_backend.domain.enums.InvestmentHorizon;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import com.js.gofunds_backend.domain.repository.FundRepository;
import com.js.gofunds_backend.domain.repository.InvestmentPlanRepository;
import com.js.gofunds_backend.planner.ai.Recommendation;
import com.js.gofunds_backend.planner.dto.InvestmentPlanRequest;
import com.js.gofunds_backend.planner.dto.InvestmentPlanResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannerServicePreviewTest {

	private static final UUID FUND_ID = UUID.fromString("22222222-3333-4444-5555-666666666666");

	@Mock
	private RecommendationService recommendationService;

	@Mock
	private InvestmentCalculatorService calculatorService;

	@Mock
	private InvestmentPlanRepository investmentPlanRepository;

	@Mock
	private FundRepository fundRepository;

	@InjectMocks
	private PlannerService plannerService;

	private final InvestmentPlanRequest request = new InvestmentPlanRequest(
			new BigDecimal("25000"), InvestmentGoal.WEALTH_CREATION,
			InvestmentHorizon.FIVE_TO_TEN_YEARS, RiskProfile.MODERATE);

	@Test
	void previewReturnsAPlanWithoutWritingIt() {
		when(recommendationService.recommend(request)).thenReturn(recommendation());
		when(fundRepository.findAllById(any())).thenReturn(List.of(fund()));
		when(calculatorService.totalInvested(any(), any())).thenReturn(new BigDecimal("2100000.00"));
		when(calculatorService.projectedReturns(any(), any(), any()))
				.thenReturn(new BigDecimal("900000.00"));

		InvestmentPlanResponse response = plannerService.preview(request);

		assertNotNull(response.id());
		assertEquals(InvestmentGoal.WEALTH_CREATION, response.goal());
		assertEquals(RiskProfile.MODERATE, response.riskProfile());
		assertEquals(1, response.recommendedFunds().size());
		assertEquals(0, response.projectedValue().compareTo(new BigDecimal("3000000.00")));
		verify(investmentPlanRepository, never()).save(any());
	}

	private static Recommendation recommendation() {
		return new Recommendation(
				RiskProfile.MODERATE,
				new Recommendation.AssetAllocation(100, 0, 0),
				List.of(new Recommendation.SelectedFund(
						FUND_ID, "Test Equity Fund", new BigDecimal("100"))),
				new BigDecimal("11.5"),
				"Long-horizon equity allocation.");
	}

	private static Fund fund() {
		Fund fund = new Fund();
		fund.setId(FUND_ID);
		fund.setSchemeCode("12345");
		fund.setSchemeName("Test Equity Fund");
		fund.setFundHouse("Test House");
		fund.setMainCategory(FundMainCategory.EQUITY);
		fund.setSubCategory(FundSubCategory.LARGE_CAP);
		fund.setRiskLevel(RiskLevel.HIGH);
		fund.setCurrentNav(new BigDecimal("123.4567"));
		fund.setSupportsSip(true);
		return fund;
	}
}
