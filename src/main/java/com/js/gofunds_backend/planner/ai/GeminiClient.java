package com.js.gofunds_backend.planner.ai;

import com.js.gofunds_backend.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The one place in the app that talks to an LLM.
 *
 * <p>The {@code ChatClient.Builder} is resolved through an {@link ObjectProvider}
 * and the key through an {@code @Value} with an empty default. Both together mean
 * a missing {@code GEMINI_API_KEY} leaves {@code chatClient} null instead of
 * failing startup, so the rest of the app boots and only
 * {@code POST /api/v1/planner/generate} reports 503. Making the property
 * mandatory would turn a missing key into a boot failure.
 *
 * <p>This class only moves text across the wire. Interpreting the answer is
 * {@link RecommendationParser}'s job and checking it is
 * {@link RecommendationValidator}'s.
 */
@Slf4j
@Component
public class GeminiClient {

	private final ChatClient chatClient;

	public GeminiClient(ObjectProvider<ChatClient.Builder> chatClientBuilder,
			@Value("${spring.ai.google.genai.api-key:}") String apiKey) {

		ChatClient.Builder builder = StringUtils.hasText(apiKey) ? chatClientBuilder.getIfAvailable() : null;
		this.chatClient = builder == null ? null : builder.build();

		if (this.chatClient == null) {
			log.warn("Gemini disabled: no spring.ai.google.genai.api-key configured, "
					+ "POST /api/v1/planner/generate will return 503");
		}
	}

	/** Whether a key is configured. Plan reads still work when this is false. */
	public boolean isAvailable() {
		return chatClient != null;
	}

	/**
	 * Sends the prompts and returns the raw completion.
	 *
	 * @throws ApiException 503 if Gemini is unconfigured, unreachable, or replies
	 *         with nothing
	 */
	public String complete(String systemPrompt, String userPrompt) {
		if (chatClient == null) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"Gemini is not configured. Set GEMINI_API_KEY to enable plan generation.");
		}

		String content;
		try {
			content = chatClient.prompt()
					.system(systemPrompt)
					.user(userPrompt)
					.call()
					.content();
		} catch (RuntimeException ex) {
			log.warn("Gemini call failed: {}", ex.getMessage());
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"The AI model could not be reached: " + ex.getMessage());
		}

		if (!StringUtils.hasText(content)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"The AI model returned an empty response");
		}
		return content;
	}
}
