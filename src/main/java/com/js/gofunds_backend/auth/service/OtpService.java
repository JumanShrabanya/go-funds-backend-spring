package com.js.gofunds_backend.auth.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.common.util.TokenUtil;
import com.js.gofunds_backend.domain.entity.EmailOtp;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.OtpType;
import com.js.gofunds_backend.domain.repository.EmailOtpRepository;
import com.js.gofunds_backend.mail.MailDeliveryException;
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
	private final boolean logOtp;

	public OtpService(
			EmailOtpRepository otpRepository,
			EmailService emailService,
			@Value("${otp.expiration-minutes:15}") int expirationMinutes,
			@Value("${app.dev.log-otp:false}") boolean logOtp) {
		this.otpRepository = otpRepository;
		this.emailService = emailService;
		this.expirationMinutes = expirationMinutes;
		this.logOtp = logOtp;
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

		if (logOtp) {
			log.info("OTP for user {} ({}): {}", user.getEmail(), type, otp);
		}

		sendEmail(user, type, otp);
	}

	/**
	 * Delivery is best-effort on purpose. This method runs inside the caller's
	 * transaction ({@code AuthService.register} and {@code forgotPassword} are both
	 * {@code @Transactional}), so rethrowing a mail failure would roll back a
	 * successful registration or a freshly issued reset code because Gmail was
	 * unreachable. The OTP is already stored, and both flows have a resend
	 * endpoint, so dropping the email costs the user one extra click rather than
	 * their account.
	 */
	private void sendEmail(User user, OtpType type, String otp) {
		try {
			switch (type) {
				case VERIFICATION -> emailService.sendVerificationEmail(user.getEmail(), otp, expirationMinutes);
				case PASSWORD_RESET -> emailService.sendPasswordResetEmail(user.getEmail(), otp, expirationMinutes);
			}
		} catch (MailDeliveryException ex) {
			log.error("Could not send {} OTP to {}. It is stored and the user can request a new one.", type, user.getEmail(), ex);
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