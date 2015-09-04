package com.phoenix.utils;

import org.apache.http.conn.ssl.TrustStrategy;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created by dusanklinec on 04.09.15.
 */
public class TrustAllTrustStrategy implements TrustStrategy {
    public static final TrustAllTrustStrategy INSTANCE = new TrustAllTrustStrategy();

    public TrustAllTrustStrategy() {
    }

    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        return true;
    }
}