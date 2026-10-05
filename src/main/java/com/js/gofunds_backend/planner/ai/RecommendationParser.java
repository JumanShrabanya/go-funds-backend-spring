package com.js.gofunds_backend.planner.ai;

import com.js.gofunds_backend.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a raw model completion into a {@link Recommendation}.
 *
 * <p>Despite an explicit "JSON only" instruction, models routinely wrap the
 * object in markdown fences or add a sentence of preamble. Rather than fight
 * that with prompt engineering alone, the first balanced <code>{...}</code>
 * block is located and parsed.
 *
 * <p>Unknown fields need no special handling: {@link Recommendation} carries
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} on every record, so the
 * shared {@code ObjectMapper} is injected as-is.
 *
 * <p>Anything unreadable is reported as 502. The request was fine, so this is an
 * upstream fault rather than the client's or the server's.
 */
@Slf4j
@Component
public class RecommendationParser {

	private final ObjectMapper objectMapper;

	public RecommendationParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * @throws ApiException 502 if no complete JSON object can be read from the
	 *         completion
	 */
	public Recommendation parse(String completion) {
		String json = extractJsonObject(completion);

		try {
			Recommendation parsed = objectMapper.readValue(json, Recommendation.class);
			if (parsed == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model returned no recommendation");
			}
			return parsed;
		} catch (JacksonException ex) {
			log.warn("Could not parse the model completion as a recommendation: {}", ex.getOriginalMessage());
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model returned a response that could not be read as a recommendation");
		}
	}

	/**
	 * Returns the first balanced <code>{...}</code> region, ignoring fences and
	 * any surrounding text. Brace counting is string- and escape-aware so a brace
	 * inside a quoted value does not end the object early.
	 *
	 * @throws ApiException 502 if there is no object, or it never closes
	 */
	private String extractJsonObject(String completion) {
		if (completion == null || completion.isBlank()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model returned an empty response");
		}

		int start = completion.indexOf('{');
		if (start < 0) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"The AI model did not return a JSON recommendation");
		}

		int depth = 0;
		boolean inString = false;
		boolean escaped = false;

		for (int i = start; i < completion.length(); i++) {
			char c = completion.charAt(i);

			if (escaped) {
				escaped = false;
			} else if (c == '\\' && inString) {
				escaped = true;
			} else if (c == '"') {
				inString = !inString;
			} else if (!inString && c == '{') {
				depth++;
			} else if (!inString && c == '}') {
				depth--;
				if (depth == 0) {
					return completion.substring(start, i + 1);
				}
			}
		}

		throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI model returned truncated JSON");
	}
}
