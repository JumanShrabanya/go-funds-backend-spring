package com.js.gofunds_backend.config;

import com.js.gofunds_backend.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/v1/health").permitAll()
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