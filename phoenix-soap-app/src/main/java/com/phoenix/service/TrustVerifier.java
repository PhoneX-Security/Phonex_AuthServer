/*
 * Copyright 2012 The Spring Web Services Framework.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.phoenix.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.net.ssl.X509TrustManager;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 *
 * @author ph4r05
 */
@Service
@Scope(value = "singleton")
public class TrustVerifier implements X509TrustManager {

    X509Certificate primaryCA = null;
    CertificateFactory cf = null;
    
    public TrustVerifier() {
        init();
    }
    
    @PostConstruct
    final public void init(){
        // already initialized?
        if (primaryCA!=null){
            return;
        }
        
        InputStream inStream = null;
        try {
            // certificate factory according to X.509 std
            cf = CertificateFactory.getInstance("X.509");
            
            // Loading the CA cert from resource
            //URL u = getClass().getResource("ca.crt");
            //inStream = new FileInputStream(u.getFile());
            inStream = TrustVerifier.class.getClassLoader().getResourceAsStream("ca.crt");
            
            // fill in primary CA
            primaryCA = (X509Certificate) cf.generateCertificate(inStream);
            
            // tidy up
            inStream.close();
        } catch (Exception ex) {
            Logger.getLogger(TrustVerifier.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if(inStream!=null){
                    inStream.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(TrustVerifier.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return null;
    }

    @Override
    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
        this.checkTrusted(certs, authType);
    }

    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
        this.checkTrusted(certs, authType);
    }
    
    public void checkTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
        if (certs == null || certs.length == 0) {
            throw new IllegalArgumentException("null or zero-length certificate chain");
        }

        if (authType == null || authType.length() == 0) {
            throw new IllegalArgumentException("null or zero-length authentication type");
        }

        try {
            for (X509Certificate cert : certs) {
                cert.verify(primaryCA.getPublicKey());
            }
        } catch (Exception ex) {
            Logger.getLogger(TrustVerifier.class.getName()).log(Level.SEVERE, null, ex);
            throw new CertificateException("Certificate verification failed", ex);
        }

        //If we end here certificate is trusted. Check if it has expired.  
        try {
            for (X509Certificate cert : certs) {
                cert.checkValidity();
            }
        } catch (Exception e) {
            throw new CertificateException("Certificate not trusted. It has expired", e);
        }
    }
}
