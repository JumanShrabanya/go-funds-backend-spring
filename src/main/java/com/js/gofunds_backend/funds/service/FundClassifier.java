package com.js.gofunds_backend.funds.service;

import com.js.gofunds_backend.domain.entity.Fund;
import com.js.gofunds_backend.domain.enums.FundMainCategory;
import com.js.gofunds_backend.domain.enums.FundSubCategory;
import com.js.gofunds_backend.domain.enums.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps an AMFI scheme name onto the fund taxonomy stored on
 * {@link Fund#mainCategory}, {@link Fund#subCategory} and {@link Fund#riskLevel}.
 *
 * <p>Pure keyword heuristic with no Spring dependency, so it can be unit tested
 * without a context. Matching is done on the scheme name lower-cased with all
 * non-alphanumerics removed, which makes "Large Cap", "Largecap" and
 * "LARGE-CAP" equivalent. Keywords marked {@code wholeWord} are instead matched
 * against the name's individual words - short generic tokens such as "etf"
 * would otherwise false-positive on unrelated names ("Mar<b>k</b>e<b>t</b> Fund"
 * contains "etf").
 *
 * <p>Rules are evaluated in declaration order and the first match wins, so more
 * specific keywords must be listed before broader ones. Two-keyword rules
 * require both keywords to be present, which is how "Banking" and "PSU" are
 * told apart from equity funds that merely reference the theme.
 */
public final class FundClassifier {

	private static final Rule FALLBACK =
			new Rule(null, null, false, FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE);

	private static final List<Rule> RULES = List.of(
			// Thematic / sector funds first: their names are the most specific.
			rule("thematic", FundMainCategory.EQUITY, FundSubCategory.SECTORAL, RiskLevel.VERY_HIGH),
			rule("sectoral", FundMainCategory.EQUITY, FundSubCategory.SECTORAL, RiskLevel.VERY_HIGH),
			rule("sector", FundMainCategory.EQUITY, FundSubCategory.SECTORAL, RiskLevel.VERY_HIGH),
			rule("play", FundMainCategory.EQUITY, FundSubCategory.SECTORAL, RiskLevel.VERY_HIGH),

			// Hybrid funds.
			rule("aggressive", FundMainCategory.HYBRID, FundSubCategory.AGGRESSIVE_HYBRID, RiskLevel.HIGH),
			rule("conservative", FundMainCategory.HYBRID, FundSubCategory.CONSERVATIVE, RiskLevel.LOW_TO_MODERATE),
			rule("balanced", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.MODERATE),
			rule("hybrid", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.MODERATE),
			rule("equity oriented", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.MODERATE),
			rule("income", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.LOW_TO_MODERATE),
			rule("pension", FundMainCategory.HYBRID, FundSubCategory.BALANCED, RiskLevel.LOW_TO_MODERATE),

			// Equity categories, narrowest first.
			rule("elss", FundMainCategory.EQUITY, FundSubCategory.ELSS, RiskLevel.HIGH),
			rule("dividend yield", FundMainCategory.EQUITY, FundSubCategory.DIVIDEND_YIELD, RiskLevel.MODERATE),
			rule("small & mid", FundMainCategory.EQUITY, FundSubCategory.SMALL_CAP, RiskLevel.VERY_HIGH),
			rule("smallcap", FundMainCategory.EQUITY, FundSubCategory.SMALL_CAP, RiskLevel.VERY_HIGH),
			rule("midcap", FundMainCategory.EQUITY, FundSubCategory.MID_CAP, RiskLevel.HIGH),
			rule("largecap", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.MODERATE),
			rule("child", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.HIGH),
			rule("mother", FundMainCategory.EQUITY, FundSubCategory.LARGE_CAP, RiskLevel.HIGH),
			rule("nifty", FundMainCategory.EQUITY, FundSubCategory.INDEX, RiskLevel.MODERATE),
			rule("sensex", FundMainCategory.EQUITY, FundSubCategory.INDEX, RiskLevel.MODERATE),
			rule("index", FundMainCategory.EQUITY, FundSubCategory.INDEX, RiskLevel.MODERATE),
			word("etf", FundMainCategory.EQUITY, FundSubCategory.INDEX, RiskLevel.MODERATE),

			// Debt funds.
			rule("overnight", FundMainCategory.DEBT, FundSubCategory.OVERNIGHT, RiskLevel.LOW),
			rule("money market", FundMainCategory.DEBT, FundSubCategory.LIQUID, RiskLevel.LOW),
			rule("liquid", FundMainCategory.DEBT, FundSubCategory.LIQUID, RiskLevel.LOW),
			rule("ultra short", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.LOW),
			rule("corporate bond", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("credit risk", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.MODERATE),
			rule("banking", "debt", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("banking", "bond", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("banking", "psu", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("psu", "debt", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("psu", "bond", FundMainCategory.DEBT, FundSubCategory.CORPORATE_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("gilt", FundMainCategory.DEBT, FundSubCategory.GOVT_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("treasury bill", FundMainCategory.DEBT, FundSubCategory.GOVT_BOND, RiskLevel.LOW_TO_MODERATE),
			rule("short duration", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.LOW_TO_MODERATE),
			rule("duration", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE),
			rule("term", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE),
			rule("accrual", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE),
			// The taxonomy has no generic "bond" bucket, so plain duration,
			// accrual and dynamically managed bond funds land on SHORT_DURATION.
			rule("bond", FundMainCategory.DEBT, FundSubCategory.SHORT_DURATION, RiskLevel.MODERATE),
			// "treasury" is deliberately last: it is a catch-all for residual
			// cash-management funds such as "Kotak Treasury Advantage", and must
			// not shadow the gilt / duration / bond rules above.
			rule("treasury", FundMainCategory.DEBT, FundSubCategory.LIQUID, RiskLevel.LOW));

	public record Classification(FundMainCategory mainCategory, FundSubCategory subCategory, RiskLevel riskLevel) {

		public void applyTo(Fund fund) {
			fund.setMainCategory(mainCategory);
			fund.setSubCategory(subCategory);
			fund.setRiskLevel(riskLevel);
		}
	}

	private record Rule(String keyword, String alsoRequires, boolean wholeWord, FundMainCategory mainCategory,
			FundSubCategory subCategory, RiskLevel riskLevel) {

		boolean matches(String squashedName, List<String> words) {
			if (keyword == null) {
				return true;
			}
			boolean hit = wholeWord ? words.contains(keyword) : squashedName.contains(squash(keyword));
			return hit && (alsoRequires == null || squashedName.contains(squash(alsoRequires)));
		}
	}

	public Classification classify(String schemeName) {
		String lowerCased = schemeName == null ? "" : schemeName.toLowerCase(Locale.ROOT);
		String squashed = squash(lowerCased);
		List<String> words = words(lowerCased);

		for (Rule rule : RULES) {
			if (rule.matches(squashed, words)) {
				return new Classification(rule.mainCategory(), rule.subCategory(), rule.riskLevel());
			}
		}
		return new Classification(FALLBACK.mainCategory(), FALLBACK.subCategory(), FALLBACK.riskLevel());
	}

	private static Rule rule(String keyword, FundMainCategory mainCategory, FundSubCategory subCategory,
			RiskLevel riskLevel) {
		return new Rule(keyword, null, false, mainCategory, subCategory, riskLevel);
	}

	private static Rule rule(String keyword, String alsoRequires, FundMainCategory mainCategory,
			FundSubCategory subCategory, RiskLevel riskLevel) {
		return new Rule(keyword, alsoRequires, false, mainCategory, subCategory, riskLevel);
	}

	private static Rule word(String keyword, FundMainCategory mainCategory, FundSubCategory subCategory,
			RiskLevel riskLevel) {
		return new Rule(keyword, null, true, mainCategory, subCategory, riskLevel);
	}

	private static String squash(String value) {
		StringBuilder sb = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static List<String> words(String lowerCased) {
		List<String> words = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (char c : lowerCased.toCharArray()) {
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
				current.append(c);
			} else if (current.length() > 0) {
				words.add(current.toString());
				current.setLength(0);
			}
		}
		if (current.length() > 0) {
			words.add(current.toString());
		}
		return words;
	}
}
