/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.rest;

import com.google.protobuf.Message;
import com.phoenix.db.DHKeys;
import com.phoenix.db.StoredFiles;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.rest.json.TestReturn;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.files.FileManager;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.pres.PresenceManager;
import com.phoenix.service.TrustVerifier;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.phonex.soap.protobuff.ServerProtoBuff;
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
    
    @Autowired(required = true)
    private PresenceManager pmanager;
    
    // owner SIP obtained from certificate
    private String owner_sip;
    
    private static final int DEFAULT_BUFFER_SIZE = 20480; // ..bytes = 20KB.
    private static final long DEFAULT_EXPIRE_TIME = 604800000L; // ..ms = 1 week.
    private static final String MULTIPART_BOUNDARY = "MULTIPART_BYTERANGES";

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
     * Serialized protocol buffer message using Base64.
     * @param msg
     * @return 
     */
    public String returnProtoBuff(com.google.protobuf.Message msg){
        final byte[] resp = msg.toByteArray();
        return new String(Base64.encode(resp));
    }
    
    /**
     * Builds protocol buffer message using given builder and serializes
     * it in Base64.
     * 
     * @param builder
     * @return 
     */
    public String returnProtoBuff(com.google.protobuf.GeneratedMessage.ExtendableBuilder builder){
        Message msg = builder.build();
        return returnProtoBuff(msg);
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
    @RequestMapping(value = "/rest/upload", method=RequestMethod.POST, produces=MediaType.TEXT_PLAIN_VALUE)// produces=MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    //public  @ResponseBody UploadReturnV1 processUpload(
    public  @ResponseBody String processUpload(
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
        
        checkInputStringPathValidity(user, 44);        
        checkInputStringBase64Validity(nonce2, 44);
        checkInputStringBase64Validity(hashmeta, 300);
        checkInputStringBase64Validity(hashpack, 300);        
        
        // Some SSL check at first
        String caller = this.authRemoteUserFromCert(request);
        log.info("Remote user connected (processUpload): " + caller);
        
        // Prepare JSON response body
        //final UploadReturnV1 ret = new UploadReturnV1();  
        ServerProtoBuff.RESTUploadPost.Builder ret = ServerProtoBuff.RESTUploadPost.newBuilder();
        ret.setVersion(1);
        ret.setErrorCode(-1);
        ret.setNonce2(nonce2);
        
        // Read DHpub value from Base64
        final byte[] dhpubByte = Base64.decode(dhpub.getBytes());
        
        // Test nonce2 && user && caller validity in database
        try {
            // Has to obtain targer local user at first - needed for later query,
            // verification the user exists and so on.
            Subscriber owner = this.dataService.getLocalUser(user);
            if (owner==null){
                log.debug("processUpload: No such user: [" + user + "]");
                return returnProtoBuff(ret);
            }
            
            // Query to fetch DH key from database.
            // Corresponding DH key has to exist on the server side in order to
            // upload the file. It gives unique permission to upload the file.
            String queryStats = "SELECT dh FROM DHKeys dh "
                    + " WHERE "
                    + "     dh.owner=:s "
                    + "     AND dh.forUser=:c "
                    + "     AND dh.used=:u "
                    + "     AND dh.uploaded=:up "
                    + "     AND dh.expired=:e "
                    + "     AND dh.expires>:n "
                    + "     AND dh.nonce2=:nonc ";
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
                ret.setMessage("No such key");
                return returnProtoBuff(ret);
            }
            
            // Get number of stored files, may be limited...
            List<StoredFiles> storedFilesFromUser = fmanager.getStoredFilesFromUser(owner, caller);
            if (storedFilesFromUser!=null && storedFilesFromUser.size() >= fmanager.getMaxFileCount(owner, user)){
                log.info("Cannot upload new files for user ["+owner.getUsername()+"] from sender ["+caller+"], quota exceeded.");
                
                ret.setErrorCode(-8);
                ret.setMessage("Quota exceeded");
                return returnProtoBuff(ret);
            }
            
            // File size limit, if the file is too big, file has to be rejected.
            if (metafile.getSize() > fmanager.getMaxFileSize(FileManager.FTYPE_META, owner)
                    || packfile.getSize() > fmanager.getMaxFileSize(FileManager.FTYPE_PACK, owner)){
                ret.setErrorCode(-10);
                ret.setMessage("Files are too big");
                return returnProtoBuff(ret);
            }
            
            // Metadata input stream
            final InputStream metaInputStream = metafile.getInputStream();
            final InputStream packInputStream = packfile.getInputStream();
            // OR transfering metafile to temporary files menawhile
            // metafile.transferTo(null);

            File tempMeta = null;
            File tempPack = null;
            File permMeta = null;
            File permPack = null;
            try {
                tempMeta = fmanager.createTempFileEx(nonce2, FileManager.FTYPE_META);
                tempPack = fmanager.createTempFileEx(nonce2, FileManager.FTYPE_PACK);
                final FileOutputStream fosMeta = new FileOutputStream(tempMeta);
                final FileOutputStream fosPack = new FileOutputStream(tempPack);
                // Use buffering provided by Apache Commons IO
                IOUtils.copy(metaInputStream, fosMeta);
                IOUtils.copy(packInputStream, fosPack);
                fosMeta.close();
                fosPack.close();

                // Check real file size after upload.
                if (tempMeta.length() > fmanager.getMaxFileSize(FileManager.FTYPE_META, owner)
                        || tempMeta.length() > fmanager.getMaxFileSize(FileManager.FTYPE_PACK, owner)){
                    tempMeta.delete();
                    tempPack.delete();
                    
                    ret.setErrorCode(-10);
                    ret.setMessage("Files are too big");
                    return returnProtoBuff(ret);
                }
                
                // If file saving to temporary file was successfull, mark DHkeys as 
                // uploaded to disable nonce2 file upload again.
                // Finally move uploaded files to final destinations.
                final String rHashMeta = FileManager.sha256(tempMeta);
                final String rHashPack = FileManager.sha256(tempPack);
                if (rHashMeta.equals(hashmeta)==false || rHashPack.equals(hashpack)==false){
                    // Invalid hash, remove uploaded files and signalize error.
                    tempMeta.delete();
                    tempPack.delete();

                    ret.setErrorCode(-3);
                    ret.setMessage("Hashes of uploaded files do not match");
                    return returnProtoBuff(ret);
                }

                // Files are uploaded, DHPub key is correctly used in database.
                // 1. Move files from temporary to permanent location
                permMeta = fmanager.getPermFile(nonce2, FileManager.FTYPE_META);
                permPack = fmanager.getPermFile(nonce2, FileManager.FTYPE_PACK);
                FileManager.move(tempMeta, permMeta);
                FileManager.move(tempPack, permPack);
                
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
                
                // Get all new nonces - notify user about new files
                String ownerSip = PhoenixDataService.getSIP(owner);
                List<String> nc = fmanager.getStoredFilesNonces(owner);
                pmanager.notifyNewFiles(ownerSip, nc);

                ret.setErrorCode(0);
                ret.setMessage("File stored successfully");
                return returnProtoBuff(ret);
            
            } catch(Exception e){
                log.error("Problem with moving files to permanent storage and finalization.", e);
                if (permMeta!=null) permMeta.delete();
                if (permPack!=null) permPack.delete();
                
                ret.setMessage("Store problem");
                return returnProtoBuff(ret);
            } finally {
                if (tempMeta!=null) tempMeta.delete();
                if (tempPack!=null) tempPack.delete();
            }
        } catch(Exception e){
            log.error("Exception in uploading files to storage.", e);
            
            ret.setErrorCode(-1);
            ret.setMessage("Exception");
            return returnProtoBuff(ret);
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
     * @throws java.io.IOException 
     */
    @RequestMapping(value = "/rest/download/{nonce2}/{filetype}", method=RequestMethod.GET)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public void processDownload(
            @PathVariable String nonce2, 
            @PathVariable String filetype, 
            HttpServletRequest request,
            HttpServletResponse response) throws CertificateException, IOException {
        
        checkInputStringBase64Validity(nonce2, 44);
        checkInputStringPathValidity(filetype, 10);
        
        // Local verified user is needed
        Subscriber owner = this.authUserFromCert(request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("User connected: " + owner);
        
        try {
            //
            // Checking if given file exists in database.
            //
            StoredFiles sf = fmanager.getStoredFile(owner, nonce2);
            if (sf==null){
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            // File exists in database, verify now existence on file system
            File wfile = fmanager.getPermFile(nonce2, filetype);
            if (wfile.exists()==false || wfile.isDirectory() || wfile.canRead()==false){
                log.warn("File inconsistency detected. "
                        + " File is in DB but not readable from storage. "
                        + " nonce2=["+nonce2+"] "
                        + " file=["+wfile+"]");
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            // Stream file to the user, if there is Range defined in the headers
            // try to serve a given portion of the file.
            processRequest(request, response, wfile, true);
            
            // For ByteArrayHttpMessageConverter
            //response.setContentType("application/octet-stream");
            // copy it to response's OutputStream
            //IOUtils.copy(fis, response.getOutputStream());
            //response.flushBuffer();
            
        } catch(Exception e){
            log.info("Error writing file to output stream. ", e);
            throw new RuntimeException("IOError writing file to output stream");
        }
    }

    /**
     * Process the actual request.
     * Inspiration took from: http://balusc.blogspot.in/2009/02/fileservlet-supporting-resume-and.html
     * @param request The request to be processed.
     * @param response The response to be created.
     * @param content Whether the request body should be written (GET) or not (HEAD).
     * @throws IOException If something fails at I/O level.
     */
    private void processRequest(HttpServletRequest request, HttpServletResponse response, File file, boolean content) throws IOException
    {
        // Prepare some variables. The ETag is an unique identifier of the file.
        String fileName = file.getName();
        long length = file.length();
        long lastModified = file.lastModified();
        String eTag = fileName + "_" + length + "_" + lastModified;
        long expires = System.currentTimeMillis() + DEFAULT_EXPIRE_TIME;


        // Validate request headers for caching ---------------------------------------------------

        // If-None-Match header should contain "*" or ETag. If so, then return 304.
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader("ETag", eTag); // Required in 304.
            response.setDateHeader("Expires", expires); // Postpone cache with 1 week.
            return;
        }

        // If-Modified-Since header should be greater than LastModified. If so, then return 304.
        // This header is ignored if any If-None-Match header is specified.
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader("ETag", eTag); // Required in 304.
            response.setDateHeader("Expires", expires); // Postpone cache with 1 week.
            return;
        }


        // Validate request headers for resume ----------------------------------------------------

        // If-Match header should contain "*" or ETag. If not, then return 412.
        String ifMatch = request.getHeader("If-Match");
        if (ifMatch != null && !matches(ifMatch, eTag)) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }

        // If-Unmodified-Since header should be greater than LastModified. If not, then return 412.
        long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
        if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
            return;
        }


        // Validate and process range -------------------------------------------------------------

        // Prepare some variables. The full Range represents the complete file.
        Range full = new Range(0, length - 1, length);
        List<Range> ranges = new ArrayList<Range>();

        // Validate and process Range and If-Range headers.
        String range = request.getHeader("Range");
        if (range != null) {

            // Range header should match format "bytes=n-n,n-n,n-n...". If not, then return 416.
            if (!range.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")) {
                response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            // If-Range header should either match ETag or be greater then LastModified. If not,
            // then return full file.
            String ifRange = request.getHeader("If-Range");
            if (ifRange != null && !ifRange.equals(eTag)) {
                try {
                    long ifRangeTime = request.getDateHeader("If-Range"); // Throws IAE if invalid.
                    if (ifRangeTime != -1 && ifRangeTime + 1000 < lastModified) {
                        ranges.add(full);
                    }
                } catch (IllegalArgumentException ignore) {
                    ranges.add(full);
                }
            }

            // If any valid If-Range header, then process each part of byte range.
            if (ranges.isEmpty()) {
                for (String part : range.substring(6).split(",")) {
                    // Assuming a file with length of 100, the following examples returns bytes at:
                    // 50-80 (50 to 80), 40- (40 to length=100), -20 (length-20=80 to length=100).
                    long start = sublong(part, 0, part.indexOf("-"));
                    long end = sublong(part, part.indexOf("-") + 1, part.length());

                    if (start == -1) {
                        start = length - end;
                        end = length - 1;
                    } else if (end == -1 || end > length - 1) {
                        end = length - 1;
                    }

                    // Check if Range is syntactically valid. If not, then return 416.
                    if (start > end) {
                        response.setHeader("Content-Range", "bytes */" + length); // Required in 416.
                        response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        return;
                    }

                    // Add range.
                    ranges.add(new Range(start, end, length));
                }
            }
        }


        // Prepare and initialize response --------------------------------------------------------

        // Get content type by file name and set default GZIP support and content disposition.
        String contentType = request.getServletContext().getMimeType(fileName);
        boolean acceptsGzip = false;
        String disposition = "inline";

        // If content type is unknown, then set the default value.
        // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // If content type is text, then determine whether GZIP content encoding is supported by
        // the browser and expand content type with the one and right character encoding.
        if (contentType.startsWith("text")) {
            String acceptEncoding = request.getHeader("Accept-Encoding");
            acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
            contentType += ";charset=UTF-8";
        } 

        // Else, expect for images, determine content disposition. If content type is supported by
        // the browser, then set to inline, else attachment which will pop a 'save as' dialogue.
        else if (!contentType.startsWith("image")) {
            String accept = request.getHeader("Accept");
            disposition = accept != null && accepts(accept, contentType) ? "inline" : "attachment";
        }

        // Initialize response.
        response.reset();
        response.setBufferSize(DEFAULT_BUFFER_SIZE);
        response.setHeader("Content-Disposition", disposition + ";filename=\"" + fileName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", eTag);
        response.setDateHeader("Last-Modified", lastModified);
        response.setDateHeader("Expires", expires);


        // Send requested file (part(s)) to client ------------------------------------------------

        // Prepare streams.
        RandomAccessFile input = null;
        OutputStream output = null;

        try {
            // Open streams.
            input = new RandomAccessFile(file, "r");
            output = response.getOutputStream();

            if (ranges.isEmpty() || full.equals(ranges.get(0))) {

                // Return full file.
                Range r = full;
                response.setContentType(contentType);
                response.setHeader("Content-Range", "bytes " + r.start + "-" + r.end + "/" + r.total);

                if (content) {
                    if (acceptsGzip) {
                        // The browser accepts GZIP, so GZIP the content.
                        response.setHeader("Content-Encoding", "gzip");
                        output = new GZIPOutputStream(output, DEFAULT_BUFFER_SIZE);
                    } else {
                        // Content length is not directly predictable in case of GZIP.
                        // So only add it if there is no means of GZIP, else browser will hang.
                        response.setHeader("Content-Length", String.valueOf(r.length));
                    }

                    // Copy full range.
                    copy(input, output, r.start, r.length);
                }

            } else if (ranges.size() == 1) {

                // Return single part of file.
                Range r = ranges.get(0);
                response.setContentType(contentType);
                response.setHeader("Content-Range", "bytes " + r.start + "-" + r.end + "/" + r.total);
                response.setHeader("Content-Length", String.valueOf(r.length));
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.

                if (content) {
                    // Copy single part range.
                    copy(input, output, r.start, r.length);
                }

            } else {

                // Return multiple parts of file.
                response.setContentType("multipart/byteranges; boundary=" + MULTIPART_BOUNDARY);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206.

                if (content) {
                    // Cast back to ServletOutputStream to get the easy println methods.
                    ServletOutputStream sos = (ServletOutputStream) output;

                    // Copy multi part range.
                    for (Range r : ranges) {
                        // Add multipart boundary and header fields for every range.
                        sos.println();
                        sos.println("--" + MULTIPART_BOUNDARY);
                        sos.println("Content-Type: " + contentType);
                        sos.println("Content-Range: bytes " + r.start + "-" + r.end + "/" + r.total);

                        // Copy single part range of multi part range.
                        copy(input, output, r.start, r.length);
                    }

                    // End with multipart boundary.
                    sos.println();
                    sos.println("--" + MULTIPART_BOUNDARY + "--");
                }
            }
        } finally {
            // Gently close streams.
            close(output);
            close(input);
        }
    }
    
    
    // Helpers (can be refactored to public utility class) ----------------------------------------
    /**
     * Returns true if the given accept header accepts the given value.
     * @param acceptHeader The accept header.
     * @param toAccept The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    private static boolean accepts(String acceptHeader, String toAccept) {
        String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
        Arrays.sort(acceptValues);
        return Arrays.binarySearch(acceptValues, toAccept) > -1
            || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
            || Arrays.binarySearch(acceptValues, "*/*") > -1;
    }

    /**
     * Returns true if the given match header matches the given value.
     * @param matchHeader The match header.
     * @param toMatch The value to be matched.
     * @return True if the given match header matches the given value.
     */
    private static boolean matches(String matchHeader, String toMatch) {
        String[] matchValues = matchHeader.split("\\s*,\\s*");
        Arrays.sort(matchValues);
        return Arrays.binarySearch(matchValues, toMatch) > -1
            || Arrays.binarySearch(matchValues, "*") > -1;
    }

    /**
     * Returns a substring of the given string value from the given begin index to the given end
     * index as a long. If the substring is empty, then -1 will be returned
     * @param value The string value to return a substring as long for.
     * @param beginIndex The begin index of the substring to be returned as long.
     * @param endIndex The end index of the substring to be returned as long.
     * @return A substring of the given string value as long or -1 if substring is empty.
     */
    private static long sublong(String value, int beginIndex, int endIndex) {
        String substring = value.substring(beginIndex, endIndex);
        return (substring.length() > 0) ? Long.parseLong(substring) : -1;
    }

    /**
     * Copy the given byte range of the given input to the given output.
     * @param input The input to copy the given range to the given output for.
     * @param output The output to copy the given range from the given input for.
     * @param start Start of the byte range.
     * @param length Length of the byte range.
     * @throws IOException If something fails at I/O level.
     */
    private static void copy(RandomAccessFile input, OutputStream output, long start, long length)
        throws IOException
    {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;

        if (input.length() == length) {
            // Write full range.
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        } else {
            // Write partial range.
            input.seek(start);
            long toRead = length;

            while ((read = input.read(buffer)) > 0) {
                if ((toRead -= read) > 0) {
                    output.write(buffer, 0, read);
                } else {
                    output.write(buffer, 0, (int) toRead + read);
                    break;
                }
            }
        }
    }

    /**
     * Close the given resource.
     * @param resource The resource to be closed.
     */
    private static void close(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException ignore) {
                // Ignore IOException. If you want to handle this anyway, it might be useful to know
                // that this will generally only be thrown when the client aborted the request.
            }
        }
    }
    
    /**
     * This class represents a byte range.
     */
    protected class Range {
        long start;
        long end;
        long length;
        long total;

        /**
         * Construct a byte range.
         * @param start Start of the byte range.
         * @param end End of the byte range.
         * @param total Total length of the byte source.
         */
        public Range(long start, long end, long total) {
            this.start = start;
            this.end = end;
            this.length = end - start + 1;
            this.total = total;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (int) (this.start ^ (this.start >>> 32));
            hash = 97 * hash + (int) (this.end ^ (this.end >>> 32));
            hash = 97 * hash + (int) (this.length ^ (this.length >>> 32));
            hash = 97 * hash + (int) (this.total ^ (this.total >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Range other = (Range) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            if (this.length != other.length) {
                return false;
            }
            if (this.total != other.total) {
                return false;
            }
            return true;
        }
    }
    
    /**
     * Checks input string for validity with regex.
     * If string contains illegal characters exception is thrown.
     * @param toCheck
     * @param regex
     * @param maxSize
     */
    protected void checkInputStringValidity(String toCheck, String regex, int maxSize){
        if (toCheck==null || toCheck.isEmpty()) return;
        // MaxSize or Regex violation
        if (toCheck.length() > maxSize || toCheck.matches(regex)==false) {
            
            log.warn("Illegal string passed to the check ["+toCheck.substring(0, toCheck.length() > 140 ? 140 : toCheck.length())+"]");
            throw new SecurityException("Illegal input parameter");
        }
    }
    
    /**
     * Checks input string for validity with regex.
     * If string contains illegal characters exception is thrown.
     * @param toCheck
     * @param maxSize
     */
    protected void checkInputStringPathValidity(String toCheck, int maxSize){
        checkInputStringValidity(toCheck, FileManager.PATH_VALID_REGEX, maxSize);
    }
    
    /**
     * Checks input string for validity with regex.
     * If string contains illegal characters exception is thrown.
     * @param toCheck
     * @param maxSize
     */
    protected void checkInputStringBase64Validity(String toCheck, int maxSize){
        checkInputStringValidity(toCheck, "[a-zA-Z0-9_\\-+=@\\.\\/]*", maxSize);
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

    public PresenceManager getPmanager() {
        return pmanager;
    }

    public void setPmanager(PresenceManager pmanager) {
        this.pmanager = pmanager;
    }
}
