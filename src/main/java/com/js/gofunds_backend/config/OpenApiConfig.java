package com.js.gofunds_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata and the JWT bearer scheme, so Swagger UI offers an
 * "Authorize" button that accepts an access token.
 *
 * <p>Declaring the scheme globally means endpoints do not repeat
 * {@code @SecurityRequirement} individually — it is inherited, and the public
 * endpoints opt out with {@code @SecurityRequirements}.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI goFundsOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Go Funds API")
						.version("v1")
						.description("""
								REST API for Go Funds: mutual fund catalogue, email-OTP authentication \
								and Gemini-powered investment planning.

								Every response is wrapped in an `ApiResponse` envelope \
								(`success`, `message`, `data`, `timestamp`).

								**Auth flow** — register or log in to get an access token and a refresh \
								token, then click **Authorize** above and paste the access token. Access \
								tokens last 15 minutes; refresh tokens last 7 days and are single-use, \
								rotated on every `/auth/refresh`.

								**Passwords are RSA-encrypted in transit.** `/auth/register`, \
								`/auth/login` and `/auth/reset-password` expect the password Base64-encoded after \
								encrypting it with the server's public key using `RSA/ECB/PKCS1Padding`.

								**The fund catalogue is empty until AMFI syncs.** Set \
								`FUNDS_SYNC_ON_STARTUP=true` for a first run, or wait for the \
								00:30 IST cron. `POST /planner/generate` returns 409 until then.""")
						.contact(new Contact().name("Go Funds")))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.name(BEARER_SCHEME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
