package com.js.gofunds_backend.mail;

/**
 * Raised when a message could not be handed to the SMTP server.
 *
 * <p>Exists so callers can distinguish "the mail server is unreachable" from a
 * genuine programming fault. {@link com.js.gofunds_backend.auth.service.OtpService}
 * treats this one as non-fatal, while any other exception still propagates.
 */
public class MailDeliveryException extends RuntimeException {

	public MailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}