package com.js.gofunds_backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

	private final SecretKey accessKey;
	private final long accessExpirationMs;
	private final SecretKey refreshKey;
	private final long refreshExpirationMs;

	public JwtProvider(
			@Value("${jwt.access.secret}") String accessSecret,
			@Value("${jwt.access.expiration}") long accessExpiration,
			@Value("${jwt.refresh.secret}") String refreshSecret,
			@Value("${jwt.refresh.expiration}") long refreshExpiration) {
		this.accessKey = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
		this.accessExpirationMs = accessExpiration;
		this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
		this.refreshExpirationMs = refreshExpiration;
	}

	public String generateAccessToken(UUID userId, String email, String role) {
		return buildToken(userId, email, role, accessKey, accessExpirationMs);
	}

	public String generateRefreshToken(UUID userId, String email, String role) {
		return buildToken(userId, email, role, refreshKey, refreshExpirationMs);
	}

	public Claims parseAccessToken(String token) throws JwtException {
		return Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
	}

	public Claims parseRefreshToken(String token) throws JwtException {
		return Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
	}

	public long refreshExpirationMs() {
		return refreshExpirationMs;
	}

	private String buildToken(UUID userId, String email, String role, SecretKey key, long expirationMs) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", email)
				.claim("role", role)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}
}