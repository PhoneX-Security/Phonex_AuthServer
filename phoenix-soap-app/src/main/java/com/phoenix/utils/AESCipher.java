/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.utils;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES cipher in CBC mode, special cipher text format. 
 * AES works in 265bit mode, PKCS5 padding.
 * 
 * 
 * @author ph4r05
 *
 */
public class AESCipher {
	// which cipher to use?
	public static final String AES="AES/CBC/PKCS5Padding";
	
	// salt size in bytes
	public static final int SALT_SIZE = 4;
	
	// AES key size - fix to 256
	public static final int AES_KEY_SIZE = 32;
	
	// how many iterations should key derivation perform?
	public static final int KEY_GEN_ITERATIONS = 8;
	
    /**
     * Encrypt plaintext with given key
     * 
     * @param plaintext
     * @param key
     * @return
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidKeySpecException 
     * @throws InvalidParameterSpecException 
     */
	public static byte[] encrypt(byte[] plaintext, char[] password) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{
		Random rand = new Random();
		//SecureRandom srand = new SecureRandom();
		
		// generate salt
		byte[] salt = new byte[SALT_SIZE];
		rand.nextBytes(salt);
		
		// derive AES encryption key using password and salt
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
		SecretKey tmp = factory.generateSecret(spec);
		SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
		
		// do encryption
		Cipher cipher = Cipher.getInstance(AES);
		cipher.init(Cipher.ENCRYPT_MODE, secret);
		AlgorithmParameters params = cipher.getParameters();
		
		byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
		byte[] ciphertext = cipher.doFinal(plaintext);
		
		byte[] result = new byte[salt.length + iv.length + ciphertext.length];
		System.arraycopy(salt, 		 0, result, 0, 				salt.length);
		System.arraycopy(iv, 		 0, result, salt.length, 		iv.length);
		System.arraycopy(ciphertext,     0, result, salt.length + iv.length, 	ciphertext.length);
		return result;
	}
	
	/**
     * Decrypt ciphertext, assume structure salt:iv:ciphertext. 4:16:x bytes
     * 
     * @param plaintext
     * @param key
     * @return
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidKeySpecException 
     * @throws InvalidParameterSpecException 
     */
	public static byte[] decrypt(byte[] cipherblock, char[] password) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException{
		// split passed cipherblock to parts
		if (cipherblock.length <= (SALT_SIZE+16)){
			throw new IllegalArgumentException("cipher block is too small");
		}
		
		int cipherLen = cipherblock.length - SALT_SIZE - 16;
		byte[] salt = new byte[SALT_SIZE];
		byte[] iv = new byte[16];
		byte[] ciphertext = new byte[cipherLen];
		System.arraycopy(cipherblock, 0, 		salt, 		0, 	SALT_SIZE);
		System.arraycopy(cipherblock, SALT_SIZE,  	iv, 		0, 	16);
		System.arraycopy(cipherblock, SALT_SIZE + 16, 	ciphertext,     0,      ciphertext.length);
		
		// derive AES encryption key using password and salt
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
		KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
		SecretKey tmp = factory.generateSecret(spec);
		SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
		
		Cipher cipher = Cipher.getInstance(AES);
		cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		return cipher.doFinal(ciphertext);
	}
}
