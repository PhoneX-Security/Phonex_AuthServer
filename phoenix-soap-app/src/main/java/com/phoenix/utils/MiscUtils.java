package com.phoenix.utils;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;

import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;
import org.json.JSONObject;

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

    /**
     * Closes closeable object silently.
     * @param toClose
     */
    public static void closeSilently(Closeable toClose){
        if (toClose == null){
            return;
        }
        try {
             toClose.close();
        } catch (Exception e){

        }
    }

    public static String generateMD5HashBase64Encoded(byte[] data) throws NoSuchAlgorithmException {
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("MD5");

        byte[] digest = sha.digest(data);
        return new String(Base64.encode(digest));
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

    /**
     * Generates HA1B password field from user SIP and password
     * @param sip
     * @param password
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String getHA1b(String sip, String password) throws NoSuchAlgorithmException {
        // split sip by @
        String arr[] = sip.split("@", 2);
        if (arr==null || arr.length!=2) {
            throw new IllegalArgumentException("Invalid SIP format");
        }

        return getHA1(sip, arr[1], password);
    }

    public static String getHA1(String username, String domain, String password) throws NoSuchAlgorithmException{
        return generateMD5Hash((username + ":" + domain + ":" + password).getBytes());
    }

    /**
     * Tries to extract json parameter as an integer.
     * @param json
     * @param key
     * @return
     * @throws JSONException
     */
    public static Boolean tryGetAsBoolean(JSONObject json, String key) throws JSONException {
        final Object obj = json.get(key);
        if (obj == null){
            return null;
        }

        if(!obj.equals(Boolean.FALSE) && (!(obj instanceof String) || !((String)obj).equalsIgnoreCase("false"))) {
            if(!obj.equals(Boolean.TRUE) && (!(obj instanceof String) || !((String)obj).equalsIgnoreCase("true"))) {
                final Integer asInt = tryGetAsInteger(json, key);
                if (asInt == null){
                    return null;
                }

                return asInt!=0;

            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Tries to extract json parameter as an integer.
     * @param json
     * @param key
     * @return
     * @throws JSONException
     */
    public static Integer tryGetAsInteger(JSONObject json, String key) throws JSONException {
        final Object obj = json.get(key);

        if (obj instanceof String){
            try {
                return Integer.parseInt((String) obj);
            } catch(Exception e){
                return null;
            }
        }

        try {
            return obj instanceof Number ? ((Number) obj).intValue() : (int) json.getDouble(key);
        } catch(Exception e){
            return null;
        }
    }

    /**
     * Tries to extract json parameter as a long.
     * @param json
     * @param key
     * @return
     * @throws JSONException
     */
    public static Long tryGetAsLong(JSONObject json, String key) throws JSONException {
        final Object obj = json.get(key);

        if (obj instanceof String){
            try {
                return Long.parseLong((String) obj);
            } catch(Exception e){
                return null;
            }
        }

        try {
            return obj instanceof Number ? ((Number) obj).longValue() : (long) json.getDouble(key);
        } catch(Exception e){
            return null;
        }
    }

    public static long getAsLong(JSONObject json, String key) throws JSONException {
        final Long toret = tryGetAsLong(json, key);
        if (toret == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }

        return toret;
    }

    public static int getAsInteger(JSONObject json, String key) throws JSONException {
        final Integer toret = tryGetAsInteger(json, key);
        if (toret == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }

        return toret;
    }

    public static boolean getAsBoolean(JSONObject json, String key) throws JSONException {
        final Boolean toret = tryGetAsBoolean(json, key);
        if (toret == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }

        return toret;
    }


    /**
     * Reads whole input stream to a byte array.
     *
     * @param is
     * @return
     * @throws IOException
     */
    public static byte[] readInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * To convert the InputStream to String we use the Reader.read(char[]
     * buffer) method. We iterate until the Reader return -1 which means
     * there's no more data to read. We use the StringWriter class to
     * produce the string.
     * @param is
     * @return
     * @throws IOException
     */
    public static String convertStreamToStr(InputStream is) throws IOException {
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
        }
    }

    public static byte[] bitStringToByteArray(boolean[] bitString){
        BitSet bits = new BitSet(bitString.length);
        for (int i = 0; i < bitString.length; i++) {
            if (bitString[i]) {
                bits.set(i);
            }
        }

        byte[] bytes = bits.toByteArray();
        if (bytes.length * 8 >= bitString.length) {
            return bytes;
        } else {
            return Arrays.copyOf(bytes, bitString.length / 8 + (bitString.length % 8 == 0 ? 0 : 1));
        }
    }

    public static String tryGetCertificateIssuerId(X509Certificate cert){
        try {
            return encodeHex(bitStringToByteArray(cert.getIssuerUniqueID()));
        } catch(Exception ex){
            return null;
        }
    }

    public static String tryGetCertificateIssuerDn(X509Certificate cert){
        try {
            return cert.getIssuerDN().toString();
        } catch(Exception ex){
            return null;
        }
    }
}
