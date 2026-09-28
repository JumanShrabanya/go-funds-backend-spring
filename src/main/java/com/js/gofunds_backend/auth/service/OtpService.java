package com.js.gofunds_backend.auth.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.common.util.TokenUtil;
import com.js.gofunds_backend.domain.entity.EmailOtp;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.OtpType;
import com.js.gofunds_backend.domain.repository.EmailOtpRepository;
import com.js.gofunds_backend.mail.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class OtpService {

	private final EmailOtpRepository otpRepository;
	private final EmailService emailService;
	private final int expirationMinutes;

	public OtpService(
			EmailOtpRepository otpRepository,
			EmailService emailService,
			@Value("${otp.expiration-minutes:15}") int expirationMinutes) {
		this.otpRepository = otpRepository;
		this.emailService = emailService;
		this.expirationMinutes = expirationMinutes;
	}

	@Transactional
	public void createAndSend(User user, OtpType type) {
		otpRepository.deleteByUserIdAndOtpType(user.getId(), type);

		String otp = TokenUtil.generateOtp();
		EmailOtp entity = new EmailOtp();
		entity.setUser(user);
		entity.setOtpHash(TokenUtil.sha256Hex(otp));
		entity.setOtpType(type);
		entity.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
		otpRepository.save(entity);

		log.info("OTP for user {} ({}): {}", user.getEmail(), type, otp);
		if (type == OtpType.VERIFICATION) {
			emailService.sendVerificationEmail(user.getEmail(), otp, expirationMinutes);
		} else if (type == OtpType.PASSWORD_RESET) {
			emailService.sendPasswordResetEmail(user.getEmail(), otp, expirationMinutes);
		}
	}

	@Transactional
	public void validate(User user, OtpType type, String otp) {
		EmailOtp stored = otpRepository
				.findFirstByUserIdAndOtpTypeOrderByCreatedAtDesc(user.getId(), type)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No OTP found. Please request a new one."));

		if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "OTP has expired. Please request a new one.");
		}
		if (!stored.getOtpHash().equals(TokenUtil.sha256Hex(otp))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP. Please try again.");
		}

		otpRepository.delete(stored);
	}
}