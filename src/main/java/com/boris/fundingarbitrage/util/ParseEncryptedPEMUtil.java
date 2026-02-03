package com.boris.fundingarbitrage.util;

import com.boris.fundingarbitrage.util.logger.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class ParseEncryptedPEMUtil {
	public static PrivateKey parse(String pemContent, String passphrase) {
		Security.addProvider(new BouncyCastleProvider());

		try {
			return loadEncryptedPkcs8PrivateKey(pemContent, passphrase.toCharArray());
		} catch (Exception e) {
			Logger.getInstance().error(e.getMessage());
			throw new RuntimeException("Failed to parse encrypted PEM private key", e);
		}
	}

	private static PrivateKey loadEncryptedPkcs8PrivateKey(
					String pem,
					char[] passphrase
	) throws Exception {
		byte[] der = decodePem(pem);
		EncryptedPrivateKeyInfo encInfo = new EncryptedPrivateKeyInfo(der);
		PBEKeySpec pbeKeySpec = new PBEKeySpec(passphrase);
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encInfo.getAlgName());
		SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);
		Cipher cipher = Cipher.getInstance(encInfo.getAlgName());
		cipher.init(Cipher.DECRYPT_MODE, pbeKey, encInfo.getAlgParameters());
		PKCS8EncodedKeySpec pkcs8Spec = encInfo.getKeySpec(cipher);

		return KeyFactory.getInstance("Ed25519").generatePrivate(pkcs8Spec);
	}

	private static byte[] decodePem(String pem) {
		String normalized = pem
						.replace("-----BEGIN ENCRYPTED PRIVATE KEY-----", "")
						.replace("-----END ENCRYPTED PRIVATE KEY-----", "")
						.replaceAll("\\s", "");
		return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
	}
}

