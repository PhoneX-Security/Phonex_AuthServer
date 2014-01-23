/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service;

import com.phoenix.db.StoredFiles;
import com.phoenix.db.opensips.Subscriber;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import org.bouncycastle.util.encoders.Base64;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * File manager service - for file transfer feature.
 * 
 * @author ph4r05
 */
@Service
@Repository
public class FileManager {
    private static final Logger log = LoggerFactory.getLogger(FileManager.class);
    public static final String FTYPE_META="meta";
    public static final String FTYPE_PACK="pack";
    public static final String PATH_VALID_REGEX="[a-zA-Z0-9_\\-+=@]*";
    public static final long PACK_FILE_SIZE_LIMIT = 1024*1024*100; // 100MB
    public static final long META_FILE_SIZE_LIMIT = 1024*1024*5; // 5MB
    public static final int  MAX_NUMBER_FILES = 5; // Maximum number of stored files for one subscriber per one user.
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private X509TrustManager trustManager;
    
    @Value("#[ft.tempDir]")
    private String tempDir;
    
    @Value("#[ft.fileDir]")
    private String fileDir;
    
    private File tempFile;
    private File fileFile;
    
    public FileManager() {
    }

    /**
     * Callback called after Spring Container initializes this object.
     * All dependencies should be autowired prior this call.
     */
    @PostConstruct
    public void init(){
        log.info("PostContruct called on file manager; tempDir=" + tempDir + "; this="+this);
        
        // Sanity checking
        if (tempDir==null || fileDir==null 
                || tempDir.isEmpty() || fileDir.isEmpty()
                || tempDir.contains("#[ft.tempDir]")
                || fileDir.contains("#[ft.fileDir]")){
            log.error("File folders are not properly initialized."
                    + " Please check the configuration; "
                    + " tempDir=["+tempDir+"]"
                    + " fileDir=["+fileDir+"]");
            
            throw new RuntimeException("Folders not properly initialized.");
        }
        
        // Check if defined folders exist
        try {
            this.tempFile = new File(tempDir);
            this.fileFile = new File(fileDir);
            initDirectory(this.tempFile);
            initDirectory(this.fileFile);
        } catch(Exception e){
            log.error("Exception in checking directory structure", e);
            throw new RuntimeException("Exception in checking directory structure", e);
        }
    }
    
    /**
     * Checks provided directory for existence, tries to create if does not 
     * exist. Throws exception on fail.
     * 
     * @param dir 
     */
    private void initDirectory(File dir){
        if (dir.exists()==false){
            dir.mkdirs();
        }
        
        if (dir.exists()==false || dir.isDirectory()==false){
            throw new RuntimeException("Unable to create a directory ["+dir+"].");
        }

        if (dir.canWrite()==false || dir.canRead()==false){
            throw new RuntimeException("Directory ["+dir+"] is not readable/writable.");
        }
    }
    
    /**
     * Creates a random file in temporary directory.
     * 
     * @param prefix
     * @param suffix
     * @return
     * @throws IOException 
     */
    public File createTempFile(String prefix, String suffix) throws IOException{
        return File.createTempFile(prefix, suffix, tempFile);
    }
    
    /**
     * Creates a random file in temporary directory.
     * Counting with file naming convention.
     * 
     * @param nonce2
     * @param type
     * @return
     * @throws IOException 
     */
    public File createTempFileEx(String nonce2, String type) throws IOException{
        return File.createTempFile(generateFileName(nonce2, type)+"_"+System.currentTimeMillis(), ".tmp", tempFile);
    }
    
    /**
     * Returns file object initialized to the permanent file storage 
     * pointing on particular file defined by nonce2 and type.
     * 
     * @param nonce2
     * @param type
     * @return 
     */
    public File getPermFile(String nonce2, String type){
        return new File(fileFile + File.separator + generateFileName(nonce2, type) + ".dat");
    }
    
    /**
     * Generates basic file name defined by its nonce2 identifier and type.
     * Basic security test is performed on input parameters in order to 
     * avoid simple attacks with path injection (character "/" in nonce2/type, etc...)
     * 
     * @param nonce2
     * @param type
     * @return 
     */
    public String generateFileName(String nonce2, String type){
        if (nonce2==null 
                || nonce2.length()==0 
                || nonce2.length()>512
                || nonce2.matches(PATH_VALID_REGEX)==false){
            throw new SecurityException("Nonce2 is invalid");
        }
        
        if (type==null 
                || type.length()==0 
                || type.length()>32
                || type.matches(PATH_VALID_REGEX)==false){
            throw new SecurityException("type is invalid");
        }
        
        return nonce2+"_"+type;
    }
    
    /**
     * Returns maximal file size for given owner.
     * Prepared for configurable and per-user settings, but at this moment 
     * only static maximal limits are provided. 
     * 
     * @param ftype
     * @param owner
     * @return 
     */
    public long getMaxFileSize(String ftype, Subscriber owner){
        return getMaxFileSize(ftype, owner, null);
    }
    
    /**
     * Returns maximal file size for given owner.
     * Prepared for configurable and per-user settings, but at this moment 
     * only static maximal limits are provided. 
     * 
     * @param ftype
     * @param owner
     * @param user
     * @return 
     */
    public long getMaxFileSize(String ftype, Subscriber owner, String user){
       if (FTYPE_META.equals(ftype)){
           return META_FILE_SIZE_LIMIT;
       } else if (FTYPE_PACK.equals(ftype)){
           return PACK_FILE_SIZE_LIMIT;
       } else {
           throw new RuntimeException("Unknown file type");
       }
    }
    
    /**
     * Returns maximal count of the uploaded files for one user.
     * Prepared for configurable and per-user settings, but at this moment 
     * only static maximal limits are provided. 
     * 
     * @param owner
     * @param user
     * @return 
     */
    public int getMaxFileCount(Subscriber owner, String user){
        return MAX_NUMBER_FILES;
    }
    
    /**
     * Returns list of a stored files for a given subscriber.
     * 
     * @param owner
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public List<StoredFiles> getStoredFiles(Subscriber owner){
        String queryStats = "SELECT sf FROM StoredFiles sf WHERE sf.owner=:s ";
        TypedQuery<StoredFiles> query = em.createQuery(queryStats, StoredFiles.class);
        query.setParameter("s", owner);
        return query.getResultList();
    }
    
    /**
     * Returns list of a stored files for a given subscriber uploaded by a given user.
     * 
     * @param owner
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public List<StoredFiles> getStoredFilesFromUser(Subscriber owner, String sender){
        String queryStats = "SELECT sf FROM StoredFiles sf WHERE sf.owner=:s AND sf.sender=:u";
        TypedQuery<StoredFiles> query = em.createQuery(queryStats, StoredFiles.class);
        query.setParameter("s", owner)
                .setParameter("u", sender);
        return query.getResultList();
    }
    
    /**
     * Returns particular stored file for a given subscriber.
     * 
     * @param owner
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public StoredFiles getStoredFile(Subscriber owner, String nonce2){
        String queryStats = "SELECT sf FROM StoredFiles sf "
                + " WHERE "
                + "     sf.owner=:s "
                + "     AND sf.nonce2=:c ";
        TypedQuery<StoredFiles> query = em.createQuery(queryStats, StoredFiles.class);
        query.setParameter("s", owner)
                .setParameter("nonc", nonce2)
                .setMaxResults(1);
        return query.getSingleResult();
    }
    
    /**
     * Deletes all links to a given file from database and file system.
     * 
     * @param nonce2 
     * @return  
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int deleteFiles(String nonce2){
        int ret=0;
        
        // Delete files from file system
        try {
            File permMeta = this.getPermFile(nonce2, FTYPE_META);
            File permPack = this.getPermFile(nonce2, FTYPE_PACK);
            
            ret |= permMeta.delete() ? 0x1 : 0;
            ret |= permPack.delete() ? 0x2 : 0;
        } catch(Exception e){
            log.warn("Exception in deleting files from FS", e);
        }
        
        // Delete stored files record in database
        String query = "SELECT sf FROM StoredFiles sf WHERE sf.nonce2=:n";
        try {
            StoredFiles sf = em.createQuery(query, StoredFiles.class)
                    .setParameter("n", nonce2)
                    .getSingleResult();            
            em.remove(sf);
            em.flush();
            ret |= 0x4;
        } catch(Exception ex){
            log.info("Problem during removing stored file from database with nonce2["+nonce2+"]", ex);
        }
        
        return ret;
    }
    
    /**
     * Performs basic FS cleanup.
     * Removes files from temporary directory older than 1 day, removes 
     * permanent files older than 3 months.
     */
    public void cleanupFS(){
        log.info("Going to cleanup file system storage for files.");
        cleanupDirectory(this.tempFile, 60*60*24);
        cleanupDirectory(this.fileFile, 60*60*24*31*3);
        log.info("Cleanup finished.");
    }
    
    /**
     * Directory cleanup for files older than <timeout> seconds than now.
     * @param dir
     * @param timeout 
     */
    protected void cleanupDirectory(File dir, int timeout){
        if (dir==null){
            throw new NullPointerException("Null directory");
        }
        
        if (dir.exists()==false || dir.isDirectory()==false){
            throw new IllegalArgumentException("Provided file is not a directory or does not exist.");
        }
        
        //Calendar cal = Calendar.getInstance();
        //cal.add(Calendar.SECOND, (-1)*timeout);
        long limit = System.currentTimeMillis() - (timeout*1000); 
        
        File[] listOfFiles = dir.listFiles();
        for (File f : listOfFiles) {
            if (f.isFile()==false) continue;
            if (f.lastModified() >= limit) continue;
            
            f.delete();
            log.info("Deleted file ["+f.getName()+"]");
        }
    }
    
    /**
     * Computes SHA256 hash of a given file.
     * @param file
     * @return 
     */
    public static String sha256(File file){
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA256");
            FileInputStream fis = new FileInputStream(file);
            DigestInputStream dis = new DigestInputStream(fis, sha);
            
            byte[] buffer = new byte[65536]; // 64kB buffer
            while (dis.read(buffer) != -1){}
            
            byte[] hash = sha.digest();
            return new String(Base64.encode(hash));
            
        } catch(IOException e){
            throw new IllegalArgumentException("Cannot compute SHA256 digest of the file ["+file+"]", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Cannot compute SHA256 digest of the file ["+file+"]", e);
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

    public X509TrustManager getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getFileDir() {
        return fileDir;
    }

    public void setFileDir(String fileDir) {
        this.fileDir = fileDir;
    }    
}
