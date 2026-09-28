package com.js.gofunds_backend.common.security;

import com.js.gofunds_backend.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				Claims claims = jwtProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
				UUID userId = UUID.fromString(claims.getSubject());
				String email = claims.get("email", String.class);
				String role = claims.get("role", String.class);

				AuthenticatedUser principal = new AuthenticatedUser(userId, email, safeRole(role));
				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						principal,
						null,
						List.of(new SimpleGrantedAuthority("ROLE_" + safeRole(role).name())));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} catch (Exception ex) {
				log.debug("Invalid JWT: {}", ex.getMessage());
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}

	private UserRole safeRole(String role) {
		try {
			return UserRole.valueOf(role);
		} catch (Exception ex) {
			return UserRole.USER;
		}
	}
}