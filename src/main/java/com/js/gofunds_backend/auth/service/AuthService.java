package com.js.gofunds_backend.auth.service;

import com.js.gofunds_backend.auth.dto.AuthResponse;
import com.js.gofunds_backend.auth.dto.ForgotPasswordRequest;
import com.js.gofunds_backend.auth.dto.LoginRequest;
import com.js.gofunds_backend.auth.dto.RefreshTokenRequest;
import com.js.gofunds_backend.auth.dto.RegisterRequest;
import com.js.gofunds_backend.auth.dto.ResendOtpRequest;
import com.js.gofunds_backend.auth.dto.ResetPasswordRequest;
import com.js.gofunds_backend.auth.dto.UserResponse;
import com.js.gofunds_backend.auth.dto.VerifyEmailRequest;
import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.common.security.JwtProvider;
import com.js.gofunds_backend.common.security.RsaEncryptionUtil;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.OtpType;
import com.js.gofunds_backend.domain.enums.UserRole;
import com.js.gofunds_backend.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RsaEncryptionUtil rsaEncryptionUtil;
	private final JwtProvider jwtProvider;
	private final RefreshTokenService refreshTokenService;
	private final OtpService otpService;
	private final long accessExpirationMs;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			RsaEncryptionUtil rsaEncryptionUtil,
			JwtProvider jwtProvider,
			RefreshTokenService refreshTokenService,
			OtpService otpService,
			@Value("${jwt.access.expiration}") long accessExpirationMs) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.rsaEncryptionUtil = rsaEncryptionUtil;
		this.jwtProvider = jwtProvider;
		this.refreshTokenService = refreshTokenService;
		this.otpService = otpService;
		this.accessExpirationMs = accessExpirationMs;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase();
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
		}

		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(decryptPassword(request.password())));
		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setPhone(request.phone());
		user.setRole(UserRole.USER);
		user.setEmailVerified(false);
		user.setActive(true);
		userRepository.save(user);

		otpService.createAndSend(user, OtpType.VERIFICATION);

		return issueTokens(user);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmailIgnoreCase(request.email().trim())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		if (!passwordEncoder.matches(decryptPassword(request.password()), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}
		if (!user.isActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Account has been disabled");
		}

		return issueTokens(user);
	}

	@Transactional
	public AuthResponse refresh(RefreshTokenRequest request) {
		User user = refreshTokenService.validateAndRotate(request.refreshToken());
		if (!user.isActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Account has been disabled");
		}
		return issueTokens(user);
	}

	@Transactional
	public void logout(User user) {
		refreshTokenService.revokeAll(user.getId());
	}

	public UserResponse getCurrentUser(User user) {
		return toUserResponse(user);
	}

	@Transactional
	public UserResponse verifyEmail(VerifyEmailRequest request) {
		User user = findUserByEmail(request.email());
		otpService.validate(user, OtpType.VERIFICATION, request.otp());
		user.setEmailVerified(true);
		userRepository.save(user);
		return toUserResponse(user);
	}

	@Transactional
	public void resendOtp(ResendOtpRequest request) {
		User user = findUserByEmail(request.email());
		otpService.createAndSend(user, request.type());
	}

	public String forgotPassword(ForgotPasswordRequest request) {
		// Do not leak whether the account exists.
		userRepository.findByEmailIgnoreCase(request.email().trim())
				.ifPresent(user -> otpService.createAndSend(user, OtpType.PASSWORD_RESET));
		return "If the email is registered, a password reset OTP has been sent.";
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		User user = findUserByEmail(request.email());
		otpService.validate(user, OtpType.PASSWORD_RESET, request.otp());
		user.setPasswordHash(passwordEncoder.encode(decryptPassword(request.newPassword())));
		userRepository.save(user);
	}

	private User findUserByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(email.trim())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No account found for this email"));
	}

	private String decryptPassword(String encryptedPassword) {
		try {
			return rsaEncryptionUtil.decryptPassword(encryptedPassword);
		} catch (Exception ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Could not decrypt password");
		}
	}

	private AuthResponse issueTokens(User user) {
		String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
		String refreshToken = refreshTokenService.generateFor(user).refreshToken();

		return new AuthResponse(
				accessToken,
				refreshToken,
				"Bearer",
				accessExpirationMs,
				user.getId(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.getRole(),
				user.isEmailVerified());
	}

	private UserResponse toUserResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.getPhone(),
				user.isEmailVerified(),
				user.isActive());
	}
}