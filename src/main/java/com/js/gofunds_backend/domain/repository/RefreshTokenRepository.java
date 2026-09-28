package com.js.gofunds_backend.domain.repository;

import com.js.gofunds_backend.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	List<RefreshToken> findByUserId(UUID userId);

	void deleteByUserId(UUID userId);
}
