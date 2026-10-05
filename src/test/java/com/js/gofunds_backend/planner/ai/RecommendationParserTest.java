package com.js.gofunds_backend.planner.ai;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.domain.enums.RiskProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationParserTest {

	private final RecommendationParser parser = new RecommendationParser(JsonMapper.builder().build());

	private static final String VALID = """
			{
			  "riskProfile": "AGGRESSIVE",
			  "assetAllocation": { "equity": 80, "debt": 20, "hybrid": 0 },
			  "selectedFunds": [
			    { "fundId": "11111111-1111-1111-1111-111111111111", "fundName": "Alpha",
			      "allocationPercentage": 60 },
			    { "fundId": "22222222-2222-2222-2222-222222222222", "fundName": "Beta",
			      "allocationPercentage": 40 }
			  ],
			  "blendedReturnRate": 13.25,
			  "explanation": "Growth oriented."
			}
			""";

	@Test
	void parsesCleanJson() {
		Recommendation dto = parser.parse(VALID);

		assertEquals(RiskProfile.AGGRESSIVE, dto.riskProfile());
		assertEquals(80, dto.assetAllocation().equity());
		assertEquals(2, dto.selectedFunds().size());
		assertEquals("13.25", dto.blendedReturnRate().toPlainString());
		assertEquals("Growth oriented.", dto.explanation());
	}

	@Test
	void stripsMarkdownFences() {
		String fenced = "```json\n" + VALID + "\n```";

		assertEquals(RiskProfile.AGGRESSIVE, parser.parse(fenced).riskProfile());
	}

	@Test
	void ignoresProseBeforeTheJson() {
		String chatty = "Sure! Here is the recommendation you asked for:\n\n" + VALID + "\n\nLet me know.";

		assertEquals(RiskProfile.AGGRESSIVE, parser.parse(chatty).riskProfile());
	}

	@Test
	void toleratesExtraFieldsTheModelInvented() {
		String withExtras = VALID.replace("\"explanation\"", "\"confidence\": 0.9, \"disclaimer\": \"x\", \"explanation\"");

		assertEquals(RiskProfile.AGGRESSIVE, parser.parse(withExtras).riskProfile());
	}

	@Test
	void handlesBracesInsideStringValues() {
		// A brace and an escaped quote inside a value must not terminate the object.
		String json = """
				{
				  "riskProfile": "MODERATE",
				  "assetAllocation": { "equity": 100, "debt": 0, "hybrid": 0 },
				  "selectedFunds": [],
				  "blendedReturnRate": 10,
				  "explanation": "Try { this } and \\" that."
				}
				""";

		Recommendation dto = parser.parse(json);

		assertEquals("Try { this } and \" that.", dto.explanation());
	}

	@Test
	void parsesSingleLineJson() {
		String flat = VALID.replaceAll("\\s*\\n\\s*", "");

		assertEquals(RiskProfile.AGGRESSIVE, parser.parse(flat).riskProfile());
	}

	@Test
	void rejectsACompletionWithNoJson() {
		ApiException ex = assertThrows(ApiException.class, () -> parser.parse("I cannot help with that."));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
	}

	@Test
	void rejectsAnEmptyCompletion() {
		assertThrows(ApiException.class, () -> parser.parse("   "));
		assertThrows(ApiException.class, () -> parser.parse(null));
	}

	@Test
	void rejectsTruncatedJson() {
		ApiException ex = assertThrows(ApiException.class, () -> parser.parse("{\"riskProfile\": \"MODERATE\""));

		assertTrue(ex.getMessage().contains("truncated"));
	}

	@Test
	void rejectsJsonThatDoesNotMatchTheContract() {
		ApiException ex = assertThrows(ApiException.class, () -> parser.parse("{\"riskProfile\": 42}"));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
	}

	@Test
	void reportsABadGatewayNotAServerError() {
		// A misbehaving model is an upstream fault; the client should not be told
		// the server broke.
		ApiException ex = assertThrows(ApiException.class, () -> parser.parse("nonsense"));

		assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
	}
}