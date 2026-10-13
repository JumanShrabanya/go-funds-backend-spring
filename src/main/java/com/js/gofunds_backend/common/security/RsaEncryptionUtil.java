package com.js.gofunds_backend.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaEncryptionUtil {

	// AlgorithmIdentifier for rsaEncryption: SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }
	private static final byte[] RSA_ALGORITHM_IDENTIFIER = {
			0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86,
			(byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00
	};

	private final String privateKeyPem;
	private PrivateKey privateKey;

	public RsaEncryptionUtil(@Value("${rsa.private-key}") String privateKeyPem) {
		this.privateKeyPem = privateKeyPem;
	}

	@PostConstruct
	void init() {
		byte[] der = decodePem(privateKeyPem);
		KeyFactory keyFactory;
		try {
			keyFactory = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("RSA KeyFactory is unavailable on this JVM", e);
		}
		try {
			this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (InvalidKeySpecException pkcs8Failure) {
			// "-----BEGIN RSA PRIVATE KEY-----" is PKCS#1: no PrivateKeyInfo wrapper, so
			// KeyFactory rejects it as PKCS#8. Wrap it and retry.
			try {
				this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs1ToPkcs8(der)));
			} catch (GeneralSecurityException | IllegalArgumentException e) {
				throw new IllegalStateException(
						"rsa.private-key is not a valid RSA private key. Expected PKCS#8 "
								+ "(-----BEGIN PRIVATE KEY-----) or PKCS#1 (-----BEGIN RSA PRIVATE KEY-----). "
								+ "Generate one with: openssl genpkey -algorithm RSA "
								+ "-pkeyopt rsa_keygen_bits:2048 -out private.pem", e);
			}
		}
	}

	private static byte[] decodePem(String pem) {
		String normalized = pem
				.replace("\\r\\n", "\n")
				.replace("\\n", "\n")
				.replaceAll("-----[A-Z][^-]*-----", "")
				.replaceAll("\\s", "");
		try {
			return Base64.getDecoder().decode(normalized);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(
					"rsa.private-key is not valid Base64 once the PEM headers are removed", e);
		}
	}

	private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
		byte[] version = {0x02, 0x01, 0x00};
		byte[] privateKey = concat(new byte[]{0x04}, derLength(pkcs1.length), pkcs1);
		byte[] body = concat(version, RSA_ALGORITHM_IDENTIFIER, privateKey);
		return concat(new byte[]{0x30}, derLength(body.length), body);
	}

	private static byte[] derLength(int length) {
		if (length < 0x80) {
			return new byte[]{(byte) length};
		}
		if (length < 0x100) {
			return new byte[]{(byte) 0x81, (byte) length};
		}
		return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
	}

	private static byte[] concat(byte[]... parts) {
		int total = 0;
		for (byte[] part : parts) {
			total += part.length;
		}
		byte[] out = new byte[total];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, out, offset, part.length);
			offset += part.length;
		}
		return out;
	}

	public String decryptPassword(String encryptedPassword) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		byte[] decodedInput = Base64.getDecoder().decode(encryptedPassword.trim());
		return new String(cipher.doFinal(decodedInput), StandardCharsets.UTF_8);
	}
}
