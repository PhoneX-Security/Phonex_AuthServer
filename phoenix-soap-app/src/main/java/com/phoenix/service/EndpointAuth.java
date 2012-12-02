/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ws.context.MessageContext;

/**
 * Verifies user certificate and permissions to access endpoint services
 * @author ph4r05
 */
@Service
public class EndpointAuth {
    private static final Logger log = LoggerFactory.getLogger(EndpointAuth.class);
    private static final String NL = "\r\n";
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required=true)
    private X509TrustManager trustManager;
    
    /**
     * Entry method for checking
     * @param context 
     */
    public void check(MessageContext context, HttpServletRequest request) throws CertificateException{
        StringBuilder sb = new StringBuilder();
        String cipherSuite = (String) request.getAttribute("javax.servlet.request.cipher_suite");
        if (cipherSuite != null) {
            sb.append("CryptoSuite is not null;").append(NL);
            X509Certificate certChain[] = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
            if (certChain != null) {
                sb.append("Certificates in chain: ").append(certChain.length).append(NL);
                for (int i = 0; i < certChain.length; i++) {
                    //sb.append("Client Certificate [").append(i).append("] = ").append(certChain[i].toString()).append(";").append(NL);
                    sb.append("CERT: ").append(i).append(NL);
                    sb.append("Cert_IssuerDN: ").append(certChain[i].getIssuerDN()).append(NL);
                    sb.append("Cert_Serial: ").append(certChain[i].getSerialNumber()).append(NL);
                    sb.append("Cert_SubjDN: ").append(certChain[i].getSubjectDN()).append(NL);
                }

                sb.append("Cert chain verification: ").append(NL);
                try {
                    this.trustManager.checkClientTrusted(certChain, "auth");
                    sb.append("certificate check passed...").append(NL);
                    
                    log.info("Certificate result: " + sb.toString());
                } catch (Exception ex) {
                    sb.append("certificate check failed!!!!").append(NL);
                    sb.append(ex.getMessage());
                    
                    log.info(sb.toString());
                    log.warn("Exception during cert processing", ex);
                    // throw
                    throw new CertificateException(ex);
                }
            } else {
                sb.append("CertChain is null").append(NL);
                throw new CertificateException("CertChain is null, not using client cert");
            }
        } else {
            sb.append("CipherSuite is null; \r\n");
            throw new CertificateException("CertSuite is null, not using secured channel");
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    public X509TrustManager getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }
}
