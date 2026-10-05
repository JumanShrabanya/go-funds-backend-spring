package com.js.gofunds_backend.planner.ai;

import com.js.gofunds_backend.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the behaviour that matters without a live Gemini call: a missing key must
 * not stop the application from booting, and must surface as a 503 on generate
 * rather than a 500.
 */
class GeminiClientTest {

	private static ObjectProvider<org.springframework.ai.chat.client.ChatClient.Builder> noBuilder() {
		return new ObjectProvider<>() {
			@Override
			public org.springframework.ai.chat.client.ChatClient.Builder getObject() {
				throw new IllegalStateException("no ChatClient.Builder expected");
			}

			@Override
			public org.springframework.ai.chat.client.ChatClient.Builder getObject(Object... args) {
				throw new IllegalStateException("no ChatClient.Builder expected");
			}

			@Override
			public org.springframework.ai.chat.client.ChatClient.Builder getIfAvailable() {
				return null;
			}

			@Override
			public org.springframework.ai.chat.client.ChatClient.Builder getIfUnique() {
				return null;
			}
		};
	}

	@Test
	void isUnavailableWithoutAnApiKey() {
		GeminiClient client = new GeminiClient(noBuilder(), "");

		assertFalse(client.isAvailable());
	}

	@Test
	void isUnavailableWhenTheApiKeyIsBlank() {
		GeminiClient client = new GeminiClient(noBuilder(), "   ");

		assertFalse(client.isAvailable());
	}

	@Test
	void reportsServiceUnavailableRatherThanFailingHard() {
		// The request itself was valid, so this is an upstream problem, not a 500.
		GeminiClient client = new GeminiClient(noBuilder(), "");

		ApiException ex = assertThrows(ApiException.class, () -> client.complete("system", "user"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
		assertTrue(ex.getMessage().contains("GEMINI_API_KEY"),
				"the message should say how to fix it, got: " + ex.getMessage());
	}
}
