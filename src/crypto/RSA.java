package crypto;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import atrium.Core;

public class RSA {
	
	private KeyPairGenerator kpg;
	public KeyPair myPair;
	
	public RSA() throws NoSuchAlgorithmException {
		kpg = KeyPairGenerator.getInstance("RSA");
		myPair = kpg.generateKeyPair();
		byte[] pubKeyBytes = myPair.getPublic().getEncoded();
		Core.pubKey = new String(Base64.getEncoder().encode(pubKeyBytes));
	}
	
	/**
	 * Encrypts a string using a generated key, and generates a key pair if not there
	 * @param str
	 * @return
	 * @throws InvalidKeyException
	 * @throws IllegalBlockSizeException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchPaddingException
	 */
	public String encrypt(String str, PublicKey pk) {
		try {
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, pk);
			return new String(cipher.doFinal(str.getBytes()), "ISO-8859-1");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	public String decrypt(String in) {
		try {
			Cipher decipher = Cipher.getInstance("RSA");
			decipher.init(Cipher.DECRYPT_MODE, myPair.getPrivate());
			String output = new String(decipher.doFinal(in.getBytes("ISO-8859-1")));
			return output;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
}
