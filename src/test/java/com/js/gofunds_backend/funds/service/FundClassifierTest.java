package com.js.gofunds_backend.funds.service;

import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundClassifierTest {

	private final FundClassifier classifier = new FundClassifier();

	private void assertClassified(String schemeName, FundMainCategory mainCategory,
			FundSubCategory subCategory, RiskLevel riskLevel) {
		FundClassifier.Classification actual = classifier.classify(schemeName);
		assertEquals(mainCategory, actual.mainCategory(), schemeName + " mainCategory");
		assertEquals(subCategory, actual.subCategory(), schemeName + " subCategory");
		assertEquals(riskLevel, actual.riskLevel(), schemeName + " riskLevel");
	}

	@Test
	void classifiesEquityFunds() {
		assertClassified("HDFC Large Cap Fund", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);
		assertClassified("Abakkus Flexi Cap Fund", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);
		assertClassified("SBI Small Cap Fund", FundMainCategory.EQUITY, FundSubCategory.SMALL_CAP, RiskLevel.VERY_HIGH);
		assertClassified("Axis Midcap Fund", FundMainCategory.EQUITY, FundSubCategory.MID_CAP, RiskLevel.HIGH);
		assertClassified("Quant ELSS Tax Saver Fund", FundMainCategory.EQUITY, FundSubCategory.ELSS, RiskLevel.HIGH);
		assertClassified("ICICI Prudential Nifty 50 Index Fund", FundMainCategory.EQUITY, FundSubCategory.INDEX, RiskLevel.MODERATE);
		assertClassified("Nippon India Dividend Yield Fund", FundMainCategory.EQUITY, FundSubCategory.DIVIDEND_YIELD, RiskLevel.MODERATE);
		assertClassified("Axis Children's Fund", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.HIGH);
	}

	@Test
	void classifiesDebtFunds() {
		assertClassified("SBI Overnight Fund", FundMainCategory.DEBT, FundSubCategory.OVERNIGHT, RiskLevel.LOW);
		assertClassified("UTI Ultra Short Fund", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.LOW);
		assertClassified("Kotak Bond Fund", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE);
		assertClassified("DSP Corporate Bond Fund", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE);
		assertClassified("DSP Credit Risk Fund", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.MODERATE);
		assertClassified("Bandhan Gilt Fund", FundMainCategory.DEBT, FundSubCategory.GOVT_BOND, RiskLevel.LOW_TO_MODERATE);
		assertClassified("Quantum Dynamic Term Fund", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE);
		assertClassified("Franklin India Low Duration Fund", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE);
		assertClassified("Kotak Treasury Advantage", FundMainCategory.DEBT, FundSubCategory.LIQUID, RiskLevel.LOW);
	}

	@Test
	void classifiesHybridFunds() {
		assertClassified("LIC MF Aggressive Hybrid Fund", FundMainCategory.HYBRID, FundSubCategory.AGGRESSIVE_HYBRID, RiskLevel.HIGH);
		assertClassified("UTI Conservative Hybrid Fund", FundMainCategory.HYBRID, FundSubCategory.CONSERVATIVE, RiskLevel.LOW_TO_MODERATE);
		assertClassified("ICICI Prudential Balanced Advantage Fund", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.MODERATE);
		assertClassified("Tata Income Fund", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.LOW_TO_MODERATE);
	}

	/**
	 * "etf" must only match as a standalone word: squashing the name turns
	 * "Mar<b>k</b>e<b>t</b> Fund" into "...etf...", which used to misclassify
	 * every money market fund as an index fund.
	 */
	@Test
	void etfKeywordOnlyMatchesAsAWholeWord() {
		assertClassified("SBI Money Market Fund", FundMainCategory.DEBT, FundSubCategory.LIQUID, RiskLevel.LOW);
		assertClassified("ICICI Prudential Money Market Fund - Cash Option", FundMainCategory.DEBT, FundSubCategory.LIQUID, RiskLevel.LOW);
		assertClassified("Mirae Asset Nifty 8-13 yr G-Sec ETF", FundMainCategory.EQUITY, FundSubCategory.INDEX, RiskLevel.MODERATE);
	}

	@Test
	void bankingAndPsuKeywordsRequireDebtContext() {
		assertClassified("Franklin India Banking & PSU Debt Fund", FundMainCategory.DEBT,
				FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE);
		assertClassified("SBI PSU Bond Fund", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND,
				RiskLevel.LOW_TO_MODERATE);
		// No "debt"/"bond" in the name, so these stay equity rather than being
		// swallowed by the debt rules.
		assertClassified("Nippon India PSU Thematic Fund", FundMainCategory.EQUITY, FundSubCategory.SECTORAL,
				RiskLevel.VERY_HIGH);
		assertClassified("Nippon India Banking and Insurance Fund", FundMainCategory.EQUITY,
				FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);
	}

	@Test
	void capKeywordsIgnoreSpacingAndCase() {
		assertClassified("NIPTON INDIA SMALLCAP FUND", FundMainCategory.EQUITY, FundSubCategory.SMALL_CAP,
				RiskLevel.VERY_HIGH);
		assertClassified("UTI Nifty Smallcap 250 Index Fund", FundMainCategory.EQUITY, FundSubCategory.SMALL_CAP,
				RiskLevel.VERY_HIGH);
		assertClassified("SBI Small & Mid Cap Fund", FundMainCategory.EQUITY, FundSubCategory.SMALL_CAP,
				RiskLevel.VERY_HIGH);
	}

	@Test
	void fallsBackForUnrecognisedNames() {
		assertClassified("Kotak Contra Fund", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);
		assertClassified("Some Unknown Fund", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);
		assertClassified(null, FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);
	}
}
