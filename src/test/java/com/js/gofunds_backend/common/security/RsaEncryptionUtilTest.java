package com.js.gofunds_backend.common.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RsaEncryptionUtilTest {

	@Test
	void decryptsPasswordEncryptedWithPkcs8Pem() throws Exception {
		KeyPair keyPair = generateKeyPair();
		String pem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());

		RsaEncryptionUtil util = new RsaEncryptionUtil(pem);
		util.init();

		assertEquals("hunter2", util.decryptPassword(encrypt("hunter2", keyPair)));
	}

	@Test
	void decryptsPasswordEncryptedWithPkcs1Pem() throws Exception {
		KeyPair keyPair = generateKeyPair();
		String pem = toPem("RSA PRIVATE KEY", pkcs1Of(keyPair.getPrivate().getEncoded()));

		RsaEncryptionUtil util = new RsaEncryptionUtil(pem);
		util.init();

		assertEquals("hunter2", util.decryptPassword(encrypt("hunter2", keyPair)));
	}

	@Test
	void acceptsLiteralEscapeNewlinesInsteadOfRealOnes() throws Exception {
		KeyPair keyPair = generateKeyPair();
		String pem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded())
				.replace("\n", "\\n");

		RsaEncryptionUtil util = new RsaEncryptionUtil(pem);
		util.init();

		assertEquals("hunter2", util.decryptPassword(encrypt("hunter2", keyPair)));
	}

	@Test
	void rejectsGarbageKeyWithActionableMessage() {
		RsaEncryptionUtil util = new RsaEncryptionUtil("not-a-key");

		IllegalStateException e = assertThrows(IllegalStateException.class, util::init);
		assertEquals("rsa.private-key is not valid Base64 once the PEM headers are removed",
				e.getMessage());
	}

	private static KeyPair generateKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private static String encrypt(String plaintext, KeyPair keyPair) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
		return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
	}

	private static String toPem(String label, byte[] der) {
		String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
		return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----";
	}

	// Pulls the privateKey OCTET STRING back out of a PKCS#8 PrivateKeyInfo, yielding
	// the equivalent PKCS#1 RSAPrivateKey DER.
	private static byte[] pkcs1Of(byte[] pkcs8) {
		int[] pos = {0};
		expectTag(pkcs8, pos, 0x30);
		readLength(pkcs8, pos);
		expectTag(pkcs8, pos, 0x02);
		skipValue(pkcs8, pos);
		expectTag(pkcs8, pos, 0x30);
		skipValue(pkcs8, pos);
		expectTag(pkcs8, pos, 0x04);
		int length = readLength(pkcs8, pos);
		byte[] pkcs1 = new byte[length];
		System.arraycopy(pkcs8, pos[0], pkcs1, 0, length);
		return pkcs1;
	}

	private static void expectTag(byte[] der, int[] pos, int tag) {
		if (der[pos[0]++] != tag) {
			throw new IllegalStateException("unexpected DER tag");
		}
	}

	private static void skipValue(byte[] der, int[] pos) {
		int length = readLength(der, pos);
		pos[0] += length;
	}

	private static int readLength(byte[] der, int[] pos) {
		int length = der[pos[0]++] & 0xFF;
		if ((length & 0x80) == 0) {
			return length;
		}
		int bytes = length & 0x7F;
		length = 0;
		for (int i = 0; i < bytes; i++) {
			length = (length << 8) | (der[pos[0]++] & 0xFF);
		}
		return length;
	}
}
