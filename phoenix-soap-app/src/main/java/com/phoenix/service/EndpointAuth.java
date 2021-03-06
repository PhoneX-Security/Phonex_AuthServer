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
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX500NameUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManager;
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
     * Checks only one side SSL (without client certificate required)
     * @param context
     * @param request
     * @throws CertificateException 
     */
    public void checkOneSideSSL(MessageContext context, HttpServletRequest request) throws CertificateException{
        // at first obtain cipher suite from servlet request
        String cipherSuite = (String) request.getAttribute("javax.servlet.request.cipher_suite");
        if (cipherSuite == null || cipherSuite.isEmpty()){
            throw new CertificateException("CertSuite is null, not using secured channel");
        }
        
        // probably OK now
    }

    /**
     * Returns certificate chain from connection.
     * If there is none, null is returned
     */
    public X509Certificate[] getCertificateChainFromConnection(MessageContext context, HttpServletRequest request){
        try {
            // at first obtain cipher suite from servlet request
            String cipherSuite = (String) request.getAttribute("javax.servlet.request.cipher_suite");
            if (cipherSuite != null) {
                // get certificate chain from cert - to verify trust
                X509Certificate certChain[] = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
                return certChain;
            } else return null;
        } catch(Exception e){
            return null;
        }
    }
    
    /**
     * Entry method for checking client's certificate - signed by CA, validity.
     * If it is OK, method passes. Otherwise CertificateException is thrown. 
     * @param context 
     */
    public void check(MessageContext context, HttpServletRequest request) throws CertificateException{
        // string builder - for logging purposes, build string trace of operations done
        StringBuilder sb = new StringBuilder();
        
        // at first obtain cipher suite from servlet request
        String cipherSuite = (String) request.getAttribute("javax.servlet.request.cipher_suite");
        if (cipherSuite != null) {
            sb.append("CryptoSuite is not null;").append(NL);
            
            // get certificate chain from cert - to verify trust
            X509Certificate certChain[] = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
            if (certChain != null) {
                sb.append("Certificates in chain: ").append(certChain.length).append(NL);
                
                // dump every cerfiticate in chain
                for (int i = 0; i < certChain.length; i++) {
                    sb.append("CERT: ").append(i).append(NL);
                    sb.append("Cert_IssuerDN: ").append(certChain[i].getIssuerDN()).append(NL);
                    sb.append("Cert_Serial: ").append(certChain[i].getSerialNumber()).append(NL);
                    sb.append("Cert_SubjDN: ").append(certChain[i].getSubjectDN()).append(NL);
                }
                
                // Certificate trust verification with trust manager.
                // Verify certificate chain, take into account both server CA, and root CA
                sb.append("Cert chain verification: ").append(NL);
                try {
                    this.trustManager.checkClientTrusted(certChain, "auth");
                    sb.append("certificate check passed (chain is valid)...").append(NL);
                    
                    log.debug("Certificate result: " + sb.toString());
                } catch (Throwable ex) {
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
    
    /**
     * Extract user SIP from certificate CN
     * @return 
     */
    public String getSIPFromCertificate(MessageContext context, HttpServletRequest request) throws CertificateException{
        try {
            // get certificate chain from cert - to verify trust
            X509Certificate certChain[] = getCertChain(context, request);
            // client certificate SHOULD be stored first here, so assume it
            return getCNfromX500Name(JcaX500NameUtil.getSubject(certChain[0]));
        } 
        catch(Exception ex){
            throw new CertificateException("Problem with client certificate, cannot get user ID", ex);
        }
    }

    /**
     * Obtains certificate chain from current request
     * @param context
     * @param request
     * @return
     * @throws CertificateException 
     */
    public X509Certificate[] getCertChain(MessageContext context, HttpServletRequest request) throws CertificateException{
        try {
            // get certificate chain from cert - to verify trust
            return (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
        } 
        catch(Exception ex){
            throw new CertificateException("Problem with client certificate, cannot get user ID", ex);
        }
    }
    
    /**
     * Gets IP address from request.
     * @param request
     * @return 
     */
    public String getIp(HttpServletRequest request){
        String ip = null;
        
        try {
            ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } catch(Exception e){
            
        }
        
        return ip;
    }
    
    /**
     * Extracts CN from X500Name
     * @param name
     * @return 
     */
    public String getCNfromX500Name(X500Name x500name) throws CertificateException{
         try {
            RDN cn = x500name.getRDNs(BCStyle.CN)[0];
            return IETFUtils.valueToString(cn.getFirst().getValue());
        } 
        catch(Exception ex){
            throw new CertificateException("Problem with client certificate, cannot get user ID", ex);
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
    
    /**
     * Unwraps hibernate session from JPA 2
     * @return 
     */
    public Session getHibernateSession(){
         HibernateEntityManager hem = em.unwrap(HibernateEntityManager.class);
         return hem.getSession();
    }
}
