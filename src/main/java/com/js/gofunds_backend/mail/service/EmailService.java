package com.js.gofunds_backend.mail.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

	private final JavaMailSender mailSender;
	private final String fromEmail;

	public EmailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String fromEmail) {
		this.mailSender = mailSender;
		this.fromEmail = fromEmail;
	}

	public void sendVerificationEmail(String email, String otp, int expiresInMinutes) {
		sendEmail(email, "Go Funds - Email Verification",
				String.format("Your verification code is: %s%nThis code expires in %d minutes.", otp, expiresInMinutes));
	}

	public void sendPasswordResetEmail(String email, String otp, int expiresInMinutes) {
		sendEmail(email, "Go Funds - Password Reset",
				String.format("Your password reset code is: %s%nThis code expires in %d minutes.", otp, expiresInMinutes));
	}

	private void sendEmail(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromEmail);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);

		try {
			mailSender.send(message);
			log.info("Email sent to: {} (subject: {})", to, subject);
		} catch (Exception ex) {
			log.error("Failed to send email to: {}", to, ex);
			throw new RuntimeException("Email send failed", ex);
		}
	}
}