package com.js.gofunds_backend.domain.repository;

import com.js.gofunds_backend.domain.entity.EmailOtp;
import com.js.gofunds_backend.domain.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

	Optional<EmailOtp> findFirstByUserIdAndOtpTypeOrderByCreatedAtDesc(UUID userId, OtpType otpType);

	void deleteByUserIdAndOtpType(UUID userId, OtpType otpType);
}
