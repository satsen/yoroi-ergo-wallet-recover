package satsen.yoroiergowalletrecover;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

public class KeyDecryption {

	public static final int ITER = 19_162;
	public static final int SALT_SIZE = 32;
	public static final int NONCE_SIZE = 12;
	public static final int KEY_SIZE = 32;
	public static final int TAG_SIZE = 16;

	public static final int METADATA_SIZE = SALT_SIZE + NONCE_SIZE + TAG_SIZE;

	public static final int SALT_START = 0;
	public static final int SALT_END = SALT_START + SALT_SIZE;
	public static final int NONCE_START = SALT_END;
	public static final int NONCE_END = NONCE_START + NONCE_SIZE;
	public static final int TAG_START = NONCE_END;
	public static final int TAG_END = TAG_START + TAG_SIZE;
	public static final int ENCRYPTED_START = TAG_END;

	public static byte[] decrypt(byte[] data, char[] password) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		byte[] salt = Arrays.copyOfRange(data, SALT_START, SALT_END);
		byte[] nonce = Arrays.copyOfRange(data, NONCE_START, NONCE_END);
		byte[] tag = Arrays.copyOfRange(data, TAG_START, TAG_END);
		byte[] encrypted = Arrays.copyOfRange(data, ENCRYPTED_START, data.length);

		SecretKey secretKey;
		{
			KeySpec spec = new PBEKeySpec(password, salt, ITER, 256);
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
			byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
			secretKey = new SecretKeySpec(key, "ChaCha20");
		}

		Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");

		cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(nonce));

		cipher.update(encrypted);
		return cipher.doFinal(tag);
	}
}
