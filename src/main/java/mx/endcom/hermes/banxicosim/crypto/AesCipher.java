package mx.endcom.hermes.banxicosim.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES tal como lo usa minos para cifrar el cuerpo de mensajes tras el intercambio ClvSim/RespClvSim.
 * Ver {@code util/impl/CipherAES.java} del repo minos: modo {@code AES/CBC/PKCS5Padding},
 * llave y IV de 16 bytes cada uno (juntos forman los 32 bytes que van cifrados con RSA en ClvSim).
 */
public final class AesCipher {

	private static final String ALGO = "AES";
	private static final String CIPHER_ALGO = "AES/CBC/PKCS5Padding";

	private AesCipher() {
	}

	public static byte[] encrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
		Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGO), new IvParameterSpec(iv));
		return cipher.doFinal(data);
	}

	public static byte[] decrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
		Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGO), new IvParameterSpec(iv));
		return cipher.doFinal(data);
	}
}
