package com.js.gofunds_backend.config;

import com.js.gofunds_backend.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final CorsConfigurationSource corsConfigurationSource;
	private final int bcryptSaltRounds;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			CorsConfigurationSource corsConfigurationSource,
			@Value("${bcrypt.salt-rounds:10}") int bcryptSaltRounds) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.corsConfigurationSource = corsConfigurationSource;
		this.bcryptSaltRounds = bcryptSaltRounds;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(bcryptSaltRounds);
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Without an explicit entry point Spring Security falls back to
			// Http403ForbiddenEntryPoint, so a request with no or an invalid token
			// comes back as 403. This is a stateless JSON API and
			// CurrentUserArgumentResolver already answers 401, so the filter has to
			// agree: 401 means "authenticate", 403 is reserved for "authenticated but
			// not allowed" and this app has no such case yet.
			.exceptionHandling(exceptions -> exceptions
					.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/v1/health").permitAll()
				// springdoc's own endpoints. They only expose the API surface that is
				// already public plus schema, so they do not leak anything, and the
				// catch-all .anyRequest().authenticated() below would otherwise lock
				// out Swagger UI entirely. /swagger-ui/** is the webjars UI itself;
				// /v3/api-docs** is the raw document.
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
						"/swagger-ui/**").permitAll()
				.requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
				.requestMatchers("/api/v1/auth/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/funds/**").authenticated()
				.requestMatchers("/api/v1/planner/**").authenticated()
				.anyRequest().authenticated()
			)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}