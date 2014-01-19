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
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.BlockCipherPadding;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * AES cipher in CBC mode, special cipher text format. AES works in 265bit mode,
 * PKCS5/PKCS7 padding.
 *
 * @author ph4r05
 *
 */
public class AESCipher {
    // which cipher to use?
    public static final String AES = "AES/CBC/PKCS5Padding";
    // salt size in bytes
    public static final int SALT_SIZE = 12;
    // AES key size - fix to 256
    public static final int AES_KEY_SIZE = 32;
    // how many iterations should key derivation perform?
    public static final int KEY_GEN_ITERATIONS = 1024;

    /**
     * Encrypt plaintext with given key
     *
     * @param plaintext
     * @param password
     * @param rand
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
    public static byte[] encrypt(byte[] plaintext, char[] password, Random rand) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException {
        // generate salt
        byte[] salt = new byte[SALT_SIZE];
        rand.nextBytes(salt);
        
        // derive AES encryption key using password and salt
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        // generate initialization vector of the size of one AES block
        byte iv[] = new byte[16];
        rand.nextBytes(iv);
        IvParameterSpec ivspec = new IvParameterSpec(iv);

        // do encryption
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] result = new byte[salt.length + iv.length + ciphertext.length];
        System.arraycopy(salt, 		 0, result, 0, 							salt.length);
        System.arraycopy(iv, 		 0, result, salt.length, 				iv.length);
        System.arraycopy(ciphertext, 0, result, salt.length + iv.length, 	ciphertext.length);
        return result;
    }

    /**
     * Decrypt ciphertext, assume structure salt:iv:ciphertext. 4:16:x bytes
     *
     * @param cipherblock
     * @param password
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
    public static byte[] decrypt(byte[] cipherblock, char[] password) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, InvalidParameterSpecException {
        // split passed cipherblock to parts
        if (cipherblock.length <= (SALT_SIZE + 16)) {
            throw new IllegalArgumentException("cipher block is too small");
        }

        int cipherLen = cipherblock.length - SALT_SIZE - 16;
        byte[] salt = new byte[SALT_SIZE];
        byte[] iv = new byte[16];
        byte[] ciphertext = new byte[cipherLen];
        System.arraycopy(cipherblock, 0, salt, 0, SALT_SIZE);
        System.arraycopy(cipherblock, SALT_SIZE, iv, 0, 16);
        System.arraycopy(cipherblock, SALT_SIZE + 16, ciphertext, 0, ciphertext.length);

        // derive AES encryption key using password and salt
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance(AES, new BouncyCastleProvider());
        cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }

    public static byte[] decrypt2(byte[] cipherblock, char[] password) throws Exception {
        // split passed cipherblock to parts
        if (cipherblock.length <= (SALT_SIZE + 16)) {
            throw new IllegalArgumentException("cipher block is too small");
        }

        int cipherLen = cipherblock.length - SALT_SIZE - 16;
        byte[] salt = new byte[SALT_SIZE];
        byte[] iv = new byte[16];
        byte[] ciphertext = new byte[cipherLen];
        System.arraycopy(cipherblock, 0, salt, 0, SALT_SIZE);
        System.arraycopy(cipherblock, SALT_SIZE, iv, 0, 16);
        System.arraycopy(cipherblock, SALT_SIZE + 16, ciphertext, 0, ciphertext.length);


        // derive AES encryption key using password and salt
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password, salt, KEY_GEN_ITERATIONS, AES_KEY_SIZE * 8);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
        byte[] key = secret.getEncoded();

        // setup cipher parameters with key and IV
        KeyParameter keyParam = new KeyParameter(key);
        CipherParameters params = new ParametersWithIV(keyParam, iv);

        // setup AES cipher in CBC mode with PKCS7 padding
        BlockCipherPadding padding = new PKCS7Padding();
        BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding);
        cipher.reset();
        cipher.init(false, params);

        // create a temporary buffer to decode into (it'll include padding)
        byte[] buf = new byte[cipher.getOutputSize(ciphertext.length)];
        int len = cipher.processBytes(ciphertext, 0, ciphertext.length, buf, 0);
        len += cipher.doFinal(buf, len);

        // remove padding
        byte[] out = new byte[len];
        System.arraycopy(buf, 0, out, 0, len);

        return out;
    }
}
