package com.phoenix.utils;

import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * Created by dusanklinec on 28.04.15.
 */
public class MiscUtils {
    public static int collectionSize(Collection<?> c){
        if (c == null){
            return -1;
        }

        return c.size();
    }

    public static boolean collectionIsEmpty(Collection<?> c){
        return c==null || c.isEmpty();
    }

    public static String join(Collection<String> c, String glue){
        if (collectionIsEmpty(c)) return "";

        final StringBuilder sb = new StringBuilder();
        final int cSize = collectionSize(c);
        int ctr = 0;
        for(String cStr : c){
            sb.append(cStr);

            ctr += 1;
            if (ctr < cSize){
                sb.append(glue);
            }
        }

        return sb.toString();
    }

    public static String generateMD5Hash(byte[] data) throws NoSuchAlgorithmException {
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("MD5");

        byte[] digest = sha.digest(data);
        return encodeHex(digest);
    }

    /**
     * Turns an array of bytes into a String representing each byte as an
     * unsigned hex number.
     *
     * @param bytes an array of bytes to convert to a hex-string
     * @return generated hex string
     */
    public static String encodeHex(byte[] bytes) {
        StringBuilder buf = new StringBuilder(bytes.length * 2);
        int i;

        for (i = 0; i < bytes.length; i++) {
            if (((int)bytes[i] & 0xff) < 0x10) {
                buf.append("0");
            }
            buf.append(Long.toString((int)bytes[i] & 0xff, 16));
        }
        return buf.toString();
    }

    /**
     * Generates HA1 password field from user SIP and password
     * @param sip
     * @param password
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String getHA1(String sip, String password) throws NoSuchAlgorithmException{
        // split sip by @
        String arr[] = sip.split("@", 2);
        if (arr==null || arr.length!=2) {
            throw new IllegalArgumentException("Invalid SIP format");
        }

        return getHA1(arr[0], arr[1], password);
    }

    public static String getHA1(String username, String domain, String password) throws NoSuchAlgorithmException{
        return generateMD5Hash((username + ":" + domain + ":" + password).getBytes());
    }
}
