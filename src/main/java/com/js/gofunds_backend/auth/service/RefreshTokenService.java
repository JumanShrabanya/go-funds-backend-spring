package com.js.gofunds_backend.auth.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.common.security.JwtProvider;
import com.js.gofunds_backend.common.util.TokenUtil;
import com.js.gofunds_backend.domain.entity.RefreshToken;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtProvider jwtProvider;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProvider jwtProvider) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtProvider = jwtProvider;
	}

	public record TokenPair(String refreshToken, LocalDateTime expiresAt) {
	}

	@Transactional
	public TokenPair generateFor(User user) {
		String token = jwtProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole().name());
		refreshTokenRepository.deleteByUserId(user.getId());

		RefreshToken entity = new RefreshToken();
		entity.setUser(user);
		entity.setTokenHash(TokenUtil.sha256Hex(token));
		entity.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtProvider.refreshExpirationMs())));
		refreshTokenRepository.save(entity);

		return new TokenPair(token, entity.getExpiresAt());
	}

	@Transactional
	public User validateAndRotate(String rawToken) {
		Claims claims;
		try {
			claims = jwtProvider.parseRefreshToken(rawToken);
		} catch (Exception ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
		}

		UUID userId = UUID.fromString(claims.getSubject());
		RefreshToken stored = refreshTokenRepository.findByUserId(userId).stream()
				.filter(token -> token.getTokenHash().equals(TokenUtil.sha256Hex(rawToken)))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

		if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token has expired. Please log in again.");
		}

		User user = stored.getUser();
		refreshTokenRepository.delete(stored);
		return user;
	}

	@Transactional
	public void revokeAll(UUID userId) {
		refreshTokenRepository.deleteByUserId(userId);
	}
}