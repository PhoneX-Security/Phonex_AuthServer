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
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorResult;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
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
    
    /**
     * Trust store 
     */
    private KeyStore trustStoreCA = null;
    private PKIXParameters params = null;   // cerification parameters
    private X509Certificate[] trustedCerts = null;    
    
    private static final String rootCAresource = "ca.crt";
    private static final String serverCAresource = "signing-ca-1.crt";
    private static final String trustedStore = "trust.jks";
    private static final String trustedStorePass = "Eedee4uush3E";
    
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
            
            // everything is OK now...
            // now try to load server CA
            this.loadServerCACrt();
            this.loadTrustStoreCrt();
            
            log.debug("All certificates loaded properly (rootCA, serverCA, trustStore)");
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
    
    /**
     * Load global trust store
     */
    final public void loadTrustStoreCrt(){
        // already initialized?
        InputStream inStream = null;
        try {
            // Loading the CA cert from resource
            inStream = TrustVerifier.class.getClassLoader().getResourceAsStream(trustedStore);
            
            // Load key store
            trustStoreCA = KeyStore.getInstance("JKS");
            trustStoreCA.load(inStream, trustedStorePass.toCharArray());
            log.debug("Trust store loaded; key store=" + trustStoreCA.toString() + "; size=" + trustStoreCA.size());
            
            // Set validation parameters
            params = new PKIXParameters(trustStoreCA);
            params.setRevocationEnabled(false); // to avoid exception on empty CRL
            log.debug("Verification params loaded; params=" + params.toString());
            
            // Load all trusted certificates
            ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>();
            Enumeration<String> aliases = trustStoreCA.aliases();
            while(aliases.hasMoreElements()){
                final String alias = aliases.nextElement();
                X509Certificate certificate = (X509Certificate) trustStoreCA.getCertificate(alias);
                certs.add(certificate);
            }
            
            trustedCerts = new X509Certificate[certs.size()];
            certs.toArray(trustedCerts);
            
        } catch (Exception ex) {
            log.warn("Problem with loading trust store", ex);
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
        return trustedCerts;
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
        
        
        // Validate certificate path
        try {
            // Build certificate path
            List<X509Certificate> certList = new ArrayList<X509Certificate>();
            certList.addAll(Arrays.asList(certs));
            
            // Create cert path for further verification
            CertPath certPath = cf.generateCertPath(certList);
            
            // Validate cert path against trusted store
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            CertPathValidatorResult result = validator.validate(certPath, params);
        } catch(Exception e){
            throw new CertificateException("Certificate not validated", e);
        }
        
        // if here, chain is valid
    }

    /**
     * Check trust for cert chain
     * 
     * @param certs
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate[] certs) throws CertificateException{
        checkClientTrusted(certs, "default");
    }
    
    /**
     * Check trust for one cert
     * 
     * @param cert
     * @throws CertificateException 
     */
    public void checkTrusted(java.security.cert.X509Certificate cert) throws CertificateException{
        java.security.cert.X509Certificate[] arr = new X509Certificate[] {cert};
        checkTrusted(arr, "default");
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

    public KeyStore getTrustStoreCA() {
        return trustStoreCA;
    }

    public PKIXParameters getParams() {
        return params;
    }

    public X509Certificate[] getTrustedCerts() {
        return trustedCerts;
    }

    public CertificateFactory getCf() {
        return cf;
    }
    
    
}
