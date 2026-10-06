package com.js.gofunds_backend.auth.service;

import com.js.gofunds_backend.common.exception.ApiException;
import com.js.gofunds_backend.common.util.TokenUtil;
import com.js.gofunds_backend.domain.entity.EmailOtp;
import com.js.gofunds_backend.domain.entity.User;
import com.js.gofunds_backend.domain.enums.OtpType;
import com.js.gofunds_backend.domain.repository.EmailOtpRepository;
import com.js.gofunds_backend.mail.MailDeliveryException;
import com.js.gofunds_backend.mail.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OtpService#createAndSend} runs inside the caller's transaction, so how
 * it treats a mail failure decides whether {@code POST /api/v1/auth/register}
 * survives an SMTP outage.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	private static final int EXPIRATION_MINUTES = 15;
	private static final String EMAIL = "user@example.com";

	@Mock
	private EmailOtpRepository otpRepository;

	@Mock
	private EmailService emailService;

	private OtpService service() {
		return new OtpService(otpRepository, emailService, EXPIRATION_MINUTES, false);
	}

	private static User user() {
		User user = new User();
		user.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
		user.setEmail(EMAIL);
		return user;
	}

	// --- The regression this guards: a Gmail outage must not lose the account ---

	@Test
	void mailFailureDoesNotEscapeCreateAndSend() {
		doThrow(new MailDeliveryException("Email send failed", new RuntimeException("smtp down")))
				.when(emailService).sendVerificationEmail(anyString(), anyString(), anyInt());

		// AuthService.register calls this inside its own @Transactional, so a throw
		// here would roll back the user that was just created.
		assertDoesNotThrow(() -> service().createAndSend(user(), OtpType.VERIFICATION));
	}

	@Test
	void passwordResetMailFailureIsAlsoNonFatal() {
		doThrow(new MailDeliveryException("Email send failed", new RuntimeException("smtp down")))
				.when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyInt());

		assertDoesNotThrow(() -> service().createAndSend(user(), OtpType.PASSWORD_RESET));
	}

	@Test
	void theOtpIsStillStoredWhenMailFails() {
		doThrow(new MailDeliveryException("Email send failed", new RuntimeException("smtp down")))
				.when(emailService).sendVerificationEmail(anyString(), anyString(), anyInt());

		service().createAndSend(user(), OtpType.VERIFICATION);

		ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
		verify(otpRepository).save(captor.capture());

		EmailOtp stored = captor.getValue();
		assertEquals(OtpType.VERIFICATION, stored.getOtpType());
		assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES - 2)),
				"OTP should expire roughly the configured window out");
	}

	@Test
	void unexpectedFailuresAreNotSwallowed() {
		doThrow(new IllegalStateException("a bug, not an SMTP problem"))
				.when(emailService).sendVerificationEmail(anyString(), anyString(), anyInt());

		// Only MailDeliveryException counts as "the mail server is down"; anything
		// else must keep propagating so it is not hidden behind a 200.
		assertThrows(IllegalStateException.class,
				() -> service().createAndSend(user(), OtpType.VERIFICATION));
	}

	// --- Storage ---

	@Test
	void onlyTheHashIsStoredAndTheRowIsTyped() {
		service().createAndSend(user(), OtpType.VERIFICATION);

		ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
		verify(otpRepository).save(captor.capture());

		EmailOtp stored = captor.getValue();
		assertTrue(stored.getOtpHash().matches("[0-9a-f]{64}"),
				"expected a lowercase hex SHA-256 digest, not the raw 6-digit code");
	}

	@Test
	void anyPreviousOtpOfTheSameTypeIsReplaced() {
		User user = user();
		service().createAndSend(user, OtpType.VERIFICATION);

		verify(otpRepository).deleteByUserIdAndOtpType(user.getId(), OtpType.VERIFICATION);
	}

	// --- Dispatch per OTP type ---

	@Test
	void verificationTypeSendsTheVerificationEmail() {
		service().createAndSend(user(), OtpType.VERIFICATION);

		verify(emailService).sendVerificationEmail(eq(EMAIL), anyString(), eq(EXPIRATION_MINUTES));
		verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyInt());
	}

	@Test
	void passwordResetTypeSendsTheResetEmail() {
		service().createAndSend(user(), OtpType.PASSWORD_RESET);

		verify(emailService).sendPasswordResetEmail(eq(EMAIL), anyString(), eq(EXPIRATION_MINUTES));
		verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyInt());
	}

	@Test
	void theEmailCarriesTheSameCodeThatWasStored() {
		service().createAndSend(user(), OtpType.VERIFICATION);

		ArgumentCaptor<String> emailed = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
		verify(emailService).sendVerificationEmail(eq(EMAIL), emailed.capture(), eq(EXPIRATION_MINUTES));
		verify(otpRepository).save(saved.capture());

		// The emailed code must be the one whose digest was written, otherwise the
		// user receives a code that validate() would reject.
		assertEquals(TokenUtil.sha256Hex(emailed.getValue()), saved.getValue().getOtpHash());
	}

	// --- validate() ---

	@Test
	void validateRejectsWhenNoOtpExists() {
		when(otpRepository.findFirstByUserIdAndOtpTypeOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Optional.empty());

		ApiException ex = assertThrows(ApiException.class,
				() -> service().validate(user(), OtpType.VERIFICATION, "123456"));
		assertEquals(400, ex.getStatus().value());
	}

	@Test
	void validateRejectsAnExpiredOtp() {
		EmailOtp stored = storedOtp("123456");
		stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
		when(otpRepository.findFirstByUserIdAndOtpTypeOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Optional.of(stored));

		ApiException ex = assertThrows(ApiException.class,
				() -> service().validate(user(), OtpType.VERIFICATION, "123456"));
		assertTrue(ex.getMessage().contains("expired"));
	}

	@Test
	void validateRejectsTheWrongOtp() {
		when(otpRepository.findFirstByUserIdAndOtpTypeOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Optional.of(storedOtp("123456")));

		ApiException ex = assertThrows(ApiException.class,
				() -> service().validate(user(), OtpType.VERIFICATION, "999999"));
		assertTrue(ex.getMessage().contains("Invalid OTP"));
	}

	@Test
	void validateConsumesTheOtpSoItCannotBeReplayed() {
		EmailOtp stored = storedOtp("123456");
		when(otpRepository.findFirstByUserIdAndOtpTypeOrderByCreatedAtDesc(any(), any()))
				.thenReturn(Optional.of(stored));

		service().validate(user(), OtpType.VERIFICATION, "123456");

		verify(otpRepository).delete(stored);
	}

	private static EmailOtp storedOtp(String plainOtp) {
		EmailOtp entity = new EmailOtp();
		entity.setOtpHash(TokenUtil.sha256Hex(plainOtp));
		entity.setOtpType(OtpType.VERIFICATION);
		entity.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
		return entity;
	}
}