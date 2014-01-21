/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.rest;

import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.FileManager;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.TrustVerifier;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Basic REST controller.
 * @author ph4r05
 */
@Controller
@RequestMapping("/phonex")
public class RESTController {
    private static final Logger log = LoggerFactory.getLogger(RESTController.class);
    private static final String NAMESPACE_URI = "http://phoenix.com/hr/schemas";
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private TrustVerifier trustManager;
    
    @Autowired(required = true)
    private PhoenixDataService dataService;
    
    @Autowired(required = true)
    private FileManager fmanager;
    
    // owner SIP obtained from certificate
    private String owner_sip;

    public RESTController() {
    }
    
     /**
     * Authenticate user from its certificate, returns subscriber data.
     * @param request
     * @return
     * @throws CertificateException 
     */
    public Subscriber authUserFromCert(HttpServletRequest request) throws CertificateException {
        try {
            auth.check(null, request);
                    
            // auth passed, now extract SIP
            String sip = auth.getSIPFromCertificate(null, request);
            if (sip==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            log.info("Request came from user: [" + sip + "]");
            this.owner_sip = sip;
            Subscriber subs = this.dataService.getLocalUser(sip);
            if (subs==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            // Is user is deleted/disabled, no further operation is allowed
            if (subs.isDeleted()){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            return subs;
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }
    
    /**
     * Authenticate remote user.
     * 1. check certificate validity & signature by CA
     * 2. extract SIP string from certificate
     * 
     * @param request
     * @return
     * @throws CertificateException 
     */
    public String authRemoteUserFromCert(HttpServletRequest request) throws CertificateException {
        try {
            auth.check(null, request);
                    
            // auth passed, now extract SIP
            String sip = auth.getSIPFromCertificate(null, request);
            if (sip==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            return sip;
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }
    
    /**
     * Checks whether user is using HTTPS (client certificate not required)
     * @param request
     * @throws CertificateException 
     */
    public void checkOneSideSSL(HttpServletRequest request) throws CertificateException {
        try {
            auth.checkOneSideSSL(null, request);
        } catch (CertificateException ex) {
            log.info("One side SSL check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }
    
    /**
     * Main file upload processing method, using POST HTTP method.
     * File sending is implemented in this way.
     * 
     * @param nonce2    nonce2 obtained from getKey protocol
     * @param user      sender
     * @param dhpub     final message for key-agreement protocol. Complex type.
     * @param hashmeta  hash of meta file, verifies correct upload
     * @param hashpack  hash of pack file, verifies correct upload
     * @param metafile
     * @param packfile
     * @param request
     * @param response
     * @throws IOException 
     * @throws java.security.cert.CertificateException 
     */
    @RequestMapping(value = "/upload", method=RequestMethod.POST)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public void processUpload(
            @RequestParam("nonce2") String nonce2,
            @RequestParam("user") String user,
            @RequestParam("dhpub") String dhpub,
            @RequestParam("hashmeta") String hashmeta,
            @RequestParam("hashpack") String hashpack,
            @RequestParam("metafile") MultipartFile metafile,
            @RequestParam("packfile") MultipartFile packfile, 
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, CertificateException {
        
        // Some SSL check at first
        String caller = this.authRemoteUserFromCert(request);
        log.info("Remote user connected: " + caller);
        
        // Test nonce2 && user && caller validity in database
        // TODO: finish this...
        
        // Metadata input stream
        InputStream metaInputStream = metafile.getInputStream();
        
        // OR transfering metafile to temporary files menawhile
        // metafile.transferTo(null);
        
        // OR use buffering provided by Apache Commons IO
        //IOUtils.copy(metaInputStream, file_output_stream);
        
        // If file saving to temporary file was successfull, mark DHkeys as 
        // uploaded to disable nonce2 file upload again.
        // Finally move uploaded files to final destinations.
        
        // TODO: finish this...
        
        // TODO: finish this implementation
        
        
    }
    
    /**
     * Main file download method for local user.
     * 
     * @param nonce2
     * @param filetype
     * @param request
     * @param response
     * @throws java.security.cert.CertificateException 
     */
    @RequestMapping(value = "/download/{nonce2}/{filetype}", method=RequestMethod.GET)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public void processDownload(
            @PathVariable String nonce2, 
            @PathVariable String filetype, 
            HttpServletRequest request,
            HttpServletResponse response) throws CertificateException, IOException {
        
        // Local verified user is needed
        Subscriber owner = this.authUserFromCert(request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("User connected: " + owner);
        
        // File downloading logic
        // TODO: finish this implementation
        response.sendError(404);
        
        try {
            // Open file input stream for sending to the client.
            FileInputStream fis = null;

            // For ByteArrayHttpMessageConverter
            response.setContentType("application/octet-stream");
            // copy it to response's OutputStream
            IOUtils.copy(fis, response.getOutputStream());
            response.flushBuffer();
            
        } catch(Exception e){
            log.info("Error writing file to output stream. ", e);
            throw new RuntimeException("IOError writing file to output stream");
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EndpointAuth getAuth() {
        return auth;
    }

    public void setAuth(EndpointAuth auth) {
        this.auth = auth;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    public TrustVerifier getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(TrustVerifier trustManager) {
        this.trustManager = trustManager;
    }

    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }

    public String getOwner_sip() {
        return owner_sip;
    }

    public void setOwner_sip(String owner_sip) {
        this.owner_sip = owner_sip;
    }

    public FileManager getFmanager() {
        return fmanager;
    }

    public void setFmanager(FileManager fmanager) {
        this.fmanager = fmanager;
    }
}
