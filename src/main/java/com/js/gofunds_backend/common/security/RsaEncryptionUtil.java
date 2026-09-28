package com.js.gofunds_backend.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaEncryptionUtil {

	private final String privateKeyPem;
	private PrivateKey privateKey;

	public RsaEncryptionUtil(@Value("${rsa.private-key}") String privateKeyPem) {
		this.privateKeyPem = privateKeyPem;
	}

	@PostConstruct
	void init() throws GeneralSecurityException {
		String pem = privateKeyPem
				.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replace("\\r\\n", "\n")
				.replace("\\n", "\n")
				.replaceAll("\\s", "")
				.replace("\\", "");
		byte[] keyBytes = Base64.getDecoder().decode(pem);
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
		this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
	}

	public String decryptPassword(String encryptedPassword) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		byte[] decodedInput = Base64.getDecoder().decode(encryptedPassword.trim());
		return new String(cipher.doFinal(decodedInput), StandardCharsets.UTF_8);
	}
}