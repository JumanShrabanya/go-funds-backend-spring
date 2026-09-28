package com.js.gofunds_backend.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class TokenUtil {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] DIGITS = "0123456789".toCharArray();

	private TokenUtil() {
	}

	public static String generateOtp() {
		StringBuilder sb = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			sb.append(DIGITS[RANDOM.nextInt(DIGITS.length)]);
		}
		return sb.toString();
	}

	public static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}
}