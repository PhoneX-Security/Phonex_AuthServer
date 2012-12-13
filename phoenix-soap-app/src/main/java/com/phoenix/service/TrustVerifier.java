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

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.annotation.PostConstruct;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 *
 * @author ph4r05
 */
@Service
@Scope(value = "singleton")
public class TrustVerifier implements X509TrustManager {
    private static final Logger log = LoggerFactory.getLogger(TrustVerifier.class);

    /**
     * ROOT CA certificate
     */
    private X509Certificate primaryCA = null;
    
    /**
     * Servers CA certificate
     */
    private X509Certificate serverCA = null;
    
    private static final String rootCAresource = "ca.crt";
    private static final String serverCAresource = "signing-ca-1.crt";
    
    private CertificateFactory cf = null;
    
    public TrustVerifier() {
        init();
    }
    
    /**
     * Loads all certificates from resources to memory.
     */
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
            inStream = TrustVerifier.class.getClassLoader().getResourceAsStream(rootCAresource);
            
            // fill in primary CA
            primaryCA = (X509Certificate) cf.generateCertificate(inStream);
            
            // tidy up
            inStream.close();
            
            // everything is OK now...
            // now try to load server CA
            this.loadServerCACrt();
            
            log.debug("All certificates loaded properly (rootCA, serverCA)");
        } catch (Exception ex) {
            log.warn("Problem with loading primary CA certificate", ex);
        } finally {
            try {
                if(inStream!=null){
                    inStream.close();
                }
            } catch (IOException ex) {
                log.warn("Cannot close inpit stream for main CA", ex);
            }
        }
    }
    
    /**
     * Loads server CA certificate
     */
    final public void loadServerCACrt(){
        // already initialized?
        InputStream inStream = null;
        try {
            // Loading the CA cert from resource
            //URL u = getClass().getResource("ca.crt");
            //inStream = new FileInputStream(u.getFile());
            inStream = TrustVerifier.class.getClassLoader().getResourceAsStream(serverCAresource);
            
            // fill in primary CA
            serverCA = (X509Certificate) cf.generateCertificate(inStream);
            
            // serverCA has to be valid against master CA
            serverCA.verify(this.primaryCA.getPublicKey());
            
            // tidy up
            inStream.close();
        } catch (Exception ex) {
            log.warn("Problem with loading server CA certificate", ex);
        } finally {
            try {
                if(inStream!=null){
                    inStream.close();
                }
            } catch (IOException ex) {
                log.warn("Cannot close inpit stream for server CA", ex);
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
    
    /**
     * Checks if all certificates in cert chain are valid and there is trusted element in chain.
     * @param certs
     * @param authType
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
        if (certs == null || certs.length == 0) {
            throw new IllegalArgumentException("null or zero-length certificate chain");
        }

        if (authType == null || authType.length() == 0) {
            throw new IllegalArgumentException("null or zero-length authentication type");
        }
        
        // Check if every certificate in chain is still valid. Every has to be valid!
        // TODO: check Certificate Revocation List!
        try {
            for (X509Certificate cert : certs) {
                cert.checkValidity();
            }
        } catch (Exception e) {
            throw new CertificateException("Certificate not trusted. It has expired", e);
        }
        
        // Certificates are valid, now check if it is trusted...
        // First certificate should be user cert -> check against server cert.
        // If is valid -> chainIsVaid=true and break checking
        // If not -> remote user probably, check signature against ROOT CA
        // If failed -> proceed to next certificate, but check only with master
        boolean chainIsValid=false;
        Exception lastException = null;
        for(int curCert = 0; curCert < certs.length; curCert++){
            X509Certificate cert = certs[curCert];
            
            // if first certificate, check against server CA.
            // We don't want transitive server CA trust.
            if (curCert==0){
                try {
                    // check with server CA
                    cert.verify(serverCA.getPublicKey());
                    // check passed -> trust
                    chainIsValid=true;
                    break;
                } catch (Exception ex) {
                    lastException=ex;
                    log.debug("Certificate verification failed, curChain: " + curCert + "; Cert: " + cert, ex);
                }
            }
            
            // if we are on more than first certificate, check signature in chain
            // meaning checking wether first certificate is signed by second in chain,
            // more genericly if (i-1)-th certificate is signed by i-th certificate
            if (curCert>0){
                try {
                    certs[curCert-1].verify(cert.getPublicKey());
                } catch (Exception ex) {
                    chainIsValid=false;
                    lastException=ex;
                    log.debug("Certificate [i-1,i] verification failed"
                            + "; i=" + curCert
                            + "; (i-1)-th: " + certs[curCert-1] 
                            + "; Cert: " + cert, ex);
                    break;
                }
            }
            
            // now try to verify current certificate signature ROOT-CA
            try {
                // check with root CA
                cert.verify(primaryCA.getPublicKey());
                // check passed -> trust
                chainIsValid=true;
                break;
            } catch (Exception ex) {
                lastException=ex;
                log.debug("Certificate verification failed, curChain: " + curCert + "; Cert: " + cert, ex);
            }
        }
        
        // is chain valid?
        if (chainIsValid==false){
            log.info("Certificate verification failed", lastException);
            throw new CertificateException("Certificate verification failed", lastException);
        }
        
        // if here, chain is valid
    }

    /**
     * Returns root CA certificate
     * @return 
     */
    public X509Certificate getPrimaryCA() {
        return primaryCA;
    }

    /**
     * Returns server CA certificate - with this cert are all client certs signed.
     * @return 
     */
    public X509Certificate getServerCA() {
        return serverCA;
    }
}
