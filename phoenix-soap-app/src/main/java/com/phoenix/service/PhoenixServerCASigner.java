/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Signature generator for Phoenix from Server CA
 * @author ph4r05
 */
@Service
public class PhoenixServerCASigner {
    private static final Logger log = LoggerFactory.getLogger(PhoenixServerCASigner.class);
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required=true)
    private TrustVerifier trustManager;
    
    //private static final String keystoreResource = "serverCA.jks";
    private static final String keystoreResource = "signing-ca-1.p12";
    private static final String keystorePass = "marklar";
    private static final String keystoreAlias = "signing-ca-1";
    private RSAPrivateCrtKeyParameters caPrivateKey;
    private X509Certificate caCert;
    
    /**
     * Initializes Server CA keystores
     */
    public void initCA(){
        /**
         * At first obtain byte stream from resources - serverCA
         */ 
        InputStream inStream = null;
        try {
            // get input stream for CA key store
            inStream = TrustVerifier.class.getClassLoader().getResourceAsStream(keystoreResource);

            // open keystore and load data
            KeyStore caKs = KeyStore.getInstance("PKCS12");
            caKs.load(inStream, keystorePass.toCharArray());
            
            // load the key entry from the keystore
            Key key = caKs.getKey(keystoreAlias, keystorePass.toCharArray());
            if (key == null) {
                    throw new RuntimeException("Got null key from keystore!"); 
            }
            
            RSAPrivateCrtKey privKey = (RSAPrivateCrtKey) key;
            caPrivateKey = new RSAPrivateCrtKeyParameters(
                    privKey.getModulus(), 
                    privKey.getPublicExponent(), 
                    privKey.getPrivateExponent(),
                    privKey.getPrimeP(), 
                    privKey.getPrimeQ(), 
                    privKey.getPrimeExponentP(), 
                    privKey.getPrimeExponentQ(), 
                    privKey.getCrtCoefficient());
            
            // and get the certificate
            caCert = (X509Certificate) caKs.getCertificate(keystoreAlias);
            
            if (caCert == null) {
                    throw new RuntimeException("Got null cert from keystore!"); 
            }
            
            log.info("Successfully loaded CA key and certificate. CA DN is '" + caCert.getSubjectDN().getName() + "'");
            caCert.verify(this.trustManager.getPrimaryCA().getPublicKey());
            log.info("Successfully verified CA certificate with Primary CA.");
            
            //
            // Code below is disabled - verification with own public key.
            // This is not self signed! It was signed by root CA
            //
            //caCert.verify(caCert.getPublicKey());
            //log.info("Successfully verified CA certificate with its own public key.");
            
            // tidy up
            inStream.close();
        } catch (Exception ex) {
            log.warn("Exception during CA initialization", ex);
        } finally {
            try {
                if(inStream!=null){
                    inStream.close();
                }
            } catch (IOException ex) {
                log.warn("Cannot close input stream", ex);
            }
        }
    }
    
    
    
    
}
