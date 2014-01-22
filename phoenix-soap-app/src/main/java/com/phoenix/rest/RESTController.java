/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.rest;

import com.google.common.io.Files;
import com.phoenix.db.DHKeys;
import com.phoenix.db.StoredFiles;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.rest.json.TestReturn;
import com.phoenix.rest.json.UploadReturnV1;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.FileManager;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.TrustVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.Date;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.encoders.Base64;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * Basic REST controller.
 * @author ph4r05
 */
@Controller
public class RESTController {
    private static final Logger log = LoggerFactory.getLogger(RESTController.class);
    
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
   
    @PostConstruct
    public void postInit(){
        log.info("REST controller postInit() called");
    }
    
    /**
     * Simple demonstration URL to test JSON output converter and dependency injection.
     * @param request
     * @param response
     * @return 
     */
    @RequestMapping(value="/simple", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody TestReturn simple(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        response.setStatus(HttpServletResponse.SC_OK);
        TestReturn tr = new TestReturn();
        tr.setVersion(1);
        tr.setErrorCode(200);
        tr.setMessage("Hello world! " + fmanager.getTempDir() + ";  " + fmanager);
        return tr;
    }
    
    /**
     * Main file upload processing method, using POST HTTP method.
     * File sending is implemented in this way.
     * 
     * @param version
     * @param nonce2    nonce2 obtained from getKey protocol
     * @param user      sender
     * @param dhpub     final message for key-agreement protocol. Complex type.
     * @param hashmeta  hash of meta file, verifies correct upload
     * @param hashpack  hash of pack file, verifies correct upload
     * @param metafile
     * @param packfile
     * @param request
     * @param response
     * @return 
     * @throws IOException 
     * @throws java.security.cert.CertificateException 
     */
    @RequestMapping(value = "/rest/upload", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public  @ResponseBody UploadReturnV1 processUpload(
            @RequestParam("version") int version,
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
        
        // Prepare JSON response body
        final UploadReturnV1 ret = new UploadReturnV1();  
        ret.setErrorCode(-1);
        
        // Read DHpub value from Base64
        final byte[] dhpubByte = Base64.decode(dhpub.getBytes());
        
        // Test nonce2 && user && caller validity in database
        try {
            // Has to obtain targer local user at first - needed for later query,
            // verification the user exists and so on.
            Subscriber owner = this.dataService.getLocalUser(user);
            if (owner==null){
                return ret;
            }
            
            // Query to fetch DH key from database
            String queryStats = "SELECT dh FROM DHKeys dh "
                    + " WHERE "
                    + "     dh.owner=:s "
                    + "     AND dh.forUser=:c "
                    + "     AND dh.used=:u "
                    + "     AND dh.uploaded:up "
                    + "     AND dh.expired=:e "
                    + "     AND dh.expires>:n "
                    + "     AND dh.nonce2=:nonc"
                    + " ORDER BY dh.expires ASC";
            TypedQuery<DHKeys> query = em.createQuery(queryStats, DHKeys.class);
            query.setParameter("s", owner)
                    .setParameter("c", caller)
                    .setParameter("u", Boolean.TRUE)
                    .setParameter("up", Boolean.FALSE)
                    .setParameter("e", Boolean.FALSE)
                    .setParameter("n", new Date())
                    .setParameter("nonc", nonce2)
                    .setMaxResults(1);
            
            DHKeys key = query.getSingleResult();
            if (key==null){
                ret.setErrorCode(-2);
                return ret;
            }
            
            // Metadata input stream
            final InputStream metaInputStream = metafile.getInputStream();
            final InputStream packInputStream = packfile.getInputStream();
            // OR transfering metafile to temporary files menawhile
            // metafile.transferTo(null);

            final File tempMeta = fmanager.createTempFileEx(nonce2, FileManager.FTYPE_META);
            final File tempPack = fmanager.createTempFileEx(nonce2, FileManager.FTYPE_PACK);
            final FileOutputStream fosMeta = new FileOutputStream(tempMeta);
            final FileOutputStream fosPack = new FileOutputStream(tempPack);
            // Use buffering provided by Apache Commons IO
            IOUtils.copy(metaInputStream, fosMeta);
            IOUtils.copy(packInputStream, fosPack);

            // If file saving to temporary file was successfull, mark DHkeys as 
            // uploaded to disable nonce2 file upload again.
            // Finally move uploaded files to final destinations.
            final String rHashMeta = FileManager.sha256(tempMeta);
            final String rHashPack = FileManager.sha256(tempPack);
            if (rHashMeta.equals(hashmeta)==false || rHashPack.equals(hashpack)==false){
                // Invalid hash, remove uploaded files and signalize error.
                tempMeta.delete();
                tempPack.delete();

                ret.setErrorCode(-2);
                ret.setMessage("Hashes of uploaded files do not match");
                return ret;
            }
            
            // Files are uploaded, DHPub key is correctly used in database.
            // 1. Move files from temporary to permanent location
            final File permMeta = fmanager.getPermFile(nonce2, FileManager.FTYPE_META);
            final File permPack = fmanager.getPermFile(nonce2, FileManager.FTYPE_PACK);
            try {
                Files.move(tempMeta, permMeta);
                Files.move(tempPack, permPack);
                
                // 2. Store DHpub key info
                key.setUploaded(true);
                dataService.persist(key);

                // 3. Create a new StoredFile record
                Calendar cal = Calendar.getInstance(); 
                cal.add(Calendar.MONTH, 1);

                StoredFiles sf = new StoredFiles();
                sf.setCreated(new Date());
                sf.setDhpublic(dhpubByte);
                sf.setExpires(cal.getTime());
                sf.setHashMeta(hashmeta);
                sf.setHashPack(hashpack);
                sf.setNonce2(nonce2);
                sf.setOwner(owner);
                sf.setProtocolVersion(version);
                sf.setSender(caller);
                sf.setSizeMeta(permMeta.length());
                sf.setSizePack(permPack.length());
                dataService.persist(sf, true);

                ret.setErrorCode(0);
                ret.setMessage("File stored successfully");
                return ret;
            
            } catch(Exception e){
                log.error("Problem with moving files to permanent storage and finalization.", e);
                return ret;
            } finally {
                tempMeta.delete();
                tempPack.delete();
                permMeta.delete();
                permPack.delete();
            }
        } catch(Exception e){
            log.error("Exception in uploading files to storage.", e);
            
            ret.setErrorCode(-1);
            return ret;
        }
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
    @RequestMapping(value = "/rest/download/{nonce2}/{filetype}", method=RequestMethod.GET)
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
