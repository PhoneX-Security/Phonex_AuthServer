/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.files;

import com.phoenix.db.StoredFiles;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.EndpointAuth;
import static com.phoenix.soap.PhoenixEndpoint.getDate;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.apache.commons.io.FileUtils;
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
    public static final String PATH_VALID_REGEX="[a-zA-Z0-9_\\-+=@\\.]*";
    public static final long PACK_FILE_SIZE_LIMIT = 1024*1024*100; // 100MB
    public static final long META_FILE_SIZE_LIMIT = 1024*1024*5; // 5MB
    public static final int  MAX_NUMBER_FILES = 50; // Maximum number of stored files for one subscriber per one user.
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
        
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
        nonce2 = getFilenameFromBase64(nonce2);
        
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
     * Returns list of a stored files for a given subscriber.
     * 
     * @param owner
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public List<String> getStoredFilesNonces(Subscriber owner){
        String queryStats = "SELECT sf.nonce2 FROM StoredFiles sf WHERE sf.owner=:s ";
        TypedQuery<String> query = em.createQuery(queryStats, String.class);
        query.setParameter("s", owner);
        return query.getResultList();
    }
    
    /**
     * Returns list of a stored files for a given subscriber uploaded by a given user.
     * 
     * @param owner
     * @param sender
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
     * @param nonce2
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public StoredFiles getStoredFile(Subscriber owner, String nonce2){
        String queryStats = "SELECT sf FROM StoredFiles sf "
                + " WHERE "
                + "     sf.owner=:s "
                + "     AND sf.nonce2=:c ";
        
        try {
            TypedQuery<StoredFiles> query = em.createQuery(queryStats, StoredFiles.class);
            query.setParameter("s", owner)
                    .setParameter("c", nonce2)
                    .setMaxResults(1);
            List<StoredFiles> lst = query.getResultList();
            return (lst==null || lst.isEmpty()) ? null : lst.get(0);
        } catch(Exception ex){
            log.info("Exception in obtaining stored file, nonce="+nonce2, ex);
        }
        
        return null;
    }
    
    /**
     * Removes DHkeys that are 3 days older than they expiration date.
     * @return number of affected records. 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int expireOldDHKeys(){
        int result = 0;
        try {
            Date expirationDate = new Date(System.currentTimeMillis() + 1000L*60L*60L*24L*3L);    
            final String olderThanQueryString = "DELETE FROM DHKeys d WHERE d.expires < :de";
            Query delQuery = em.createQuery(olderThanQueryString);
            delQuery.setParameter("de", expirationDate);
            result = delQuery.executeUpdate();
            
            log.info("Expired DH keys=" + result);
        } catch(Exception ex){
            log.error("Exception during expiriation old DH keys procedure", ex);
        }
        
        return result;
    }
    
    /**
     * Deletes all records with expiration time before now().
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int expireRecords(){
        int ret = 0;
        
        TypedQuery<StoredFiles> sfQuery = em.createQuery("SELECT sf FROM StoredFiles sf WHERE sf.expires<:now", StoredFiles.class);
        sfQuery.setParameter("now", new Date());

        List<StoredFiles> sfList = sfQuery.getResultList();
        if (sfList == null || sfList.isEmpty()){
            return ret;
        }
        
        log.info("Expired DB records=" + sfList.size());
        deleteFilesList(sfList);
        ret += sfList.size();
        
        return ret;
    }
    
    /**
     * Deletes all database records without file counterparts.
     * 
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int deleteOrphans(){
        int ret = 0;
        
        // At first get number of all stored files.
        // Files loading from database will be done in steps in order to be robust
        // when database grows.
        TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(sf) FROM StoredFiles sf", Long.class);
        Long sfCount = countQuery.getSingleResult();
        if (sfCount==null || sfCount==0){
            return ret;
        }
        
        // Load files with step 250, using SQL LIMIT clause.
        log.info("DeleteOrphans() called, sfCount=" + sfCount);
        final int step = 250;
        int curOffset = 0;
        
        for(; curOffset < sfCount; curOffset += step){
            TypedQuery<StoredFiles> sfQuery = em.createQuery("SELECT sf FROM StoredFiles sf", StoredFiles.class);
            
            sfQuery.setFirstResult(curOffset);
            sfQuery.setMaxResults(step);
            
            List<StoredFiles> sfList = sfQuery.getResultList();
            if (sfList == null){
                break;
            }
            
            // Check existence of the physical files 
            int deleted=0;
            for(StoredFiles sf : sfList){
                final File permMeta = getPermFile(sf.getNonce2(), FTYPE_META);
                final File permPack = getPermFile(sf.getNonce2(), FTYPE_PACK);
                if (permMeta.exists() && permPack.exists()) continue;
                
                log.debug("DeleteOrphans(); nonce2["+sf.getNonce2()+"] has missing physical file.");
                
                deleted+=1;
                if (permMeta.exists())
                    permMeta.delete();
                if (permPack.exists())
                    permPack.delete();
                em.remove(sf);
            }
            
            if (deleted>0){
                em.flush();
            }
            
            sfCount -= deleted;
            curOffset -= deleted;
        }
        
        return ret;
    }
    
    /**
     * Deletes the list of files.
     * @param nonces
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int deleteFiles(List<String> nonces){
        if (nonces==null) throw new NullPointerException("NonceList cannot be empty");
        for(String nonce : nonces){
            deleteFiles(nonce);
        }
        
        return 0;
    }
    
    /**
     * Deletes the list of files.
     * @param sfList
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int deleteFilesList(List<StoredFiles> sfList){
        if (sfList==null) throw new NullPointerException("NonceList cannot be empty");
        for(StoredFiles sf : sfList){
            if (sf==null) continue;
            
            log.debug("Deleting file: " + sf.getNonce2());
            deleteFiles(sf);
        }
        
        return sfList.size();
    }
    
    /**
     * Deletes all traces about file given reference to stored file.
     * @param sf
     * @return 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public int deleteFiles(StoredFiles sf){
        return deleteFiles(sf.getNonce2());
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
            
            if (permMeta.exists()){
                ret |= permMeta.delete() ? 0x1 : 0;
            }
            
            if (permPack.exists()){
                ret |= permPack.delete() ? 0x2 : 0;
            }
        } catch(Exception e){
            log.warn("Exception in deleting files from FS", e);
        }
        
        // Delete stored files record in database
        String query = "SELECT sf FROM StoredFiles sf WHERE sf.nonce2=:n";
        try {
            StoredFiles sf = em.createQuery(query, StoredFiles.class)
                    .setParameter("n", nonce2)
                    .getSingleResult();
            if (sf!=null){
                em.remove(sf);
                em.flush();
                ret |= 0x4;
            }
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
        cleanupDirectory(this.tempFile, 60L*60L*24L);
        cleanupDirectory(this.fileFile, 60L*60L*24L*31L*3L);
        log.info("Cleanup finished.");
    }
    
    /**
     * Directory cleanup for files older than <timeout> seconds than now.
     * @param dir
     * @param timeout 
     */
    protected void cleanupDirectory(File dir, long timeout){
        if (dir==null){
            throw new NullPointerException("Null directory");
        }
        
        if (dir.exists()==false || dir.isDirectory()==false){
            throw new IllegalArgumentException("Provided file is not a directory or does not exist.");
        }
        
        //Calendar cal = Calendar.getInstance();
        //cal.add(Calendar.SECOND, (-1)*timeout);
        long now = System.currentTimeMillis();
        long limit = now - (timeout*1000L); 
        
        File[] listOfFiles = dir.listFiles();
        for (File f : listOfFiles) {
            if (f.isFile()==false) continue;
            
            try {
                long lastModif = f.lastModified();
                if (lastModif >= limit) continue;

                log.info(String.format("Going to delete file [%s], lastModif=%d vs. limit=%d vs. now=%d", 
                        f.getName(), lastModif, limit, now));
                
                f.delete();
            } catch(Exception e){
                log.info("Exception during deleting file.", e);
            }
        }
    }
    
    /**
     * Computes SHA256 hash of a given file.
     * @param file
     * @return 
     */
    public static String sha256(File file){
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
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
    
    /**
     * Moves the file from one path to another. This method can rename a file or
     * move it to a different directory, like the Unix {@code mv} command.
     *
     * @param from the source file
     * @param to the destination file
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if {@code from.equals(to)}
     */
    public static void move(File from, File to) throws IOException {
        if (from==null || to==null) 
            throw new NullPointerException("Files cannot be null");
        if (from.equals(to))
            throw new IllegalArgumentException(String.format("Source %s and destination %s must be different", from, to));
        
        if (!from.renameTo(to)) {
            FileUtils.copyFile(from, to);
            if (!from.delete()) {
                if (!to.delete()) {
                    throw new IOException("Unable to delete " + to);
                }
                throw new IOException("Unable to delete " + from);
            }
        }
    }
    
    /**
     * Moves the file from one path to another. It is performed by copy & remove.
     *
     * @param from the source file
     * @param to the destination file
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if {@code from.equals(to)}
     */
    public static void moveBycopy(File from, File to) throws IOException {
        if (from==null || to==null) 
            throw new NullPointerException("Files cannot be null");
        if (from.equals(to))
            throw new IllegalArgumentException(String.format("Source %s and destination %s must be different", from, to));
        
        FileUtils.copyFile(from, to);
        if (!from.delete()) {
            if (!to.delete()) {
                throw new IOException("Unable to delete " + to);
            }
            throw new IOException("Unable to delete " + from);
        }
    }
    
    /**
     * Converts base64 string to a file name (removes /).
     * Substitution: 
     * / --> _ +
     * --> -
     *
     * @param based
     * @return
     */
    public static String getFilenameFromBase64(String based) {
        return based.replace("/", "_").replace("+", "-");
    }
    
    /**
     * Converts base64 path-compatible string to base64.
     * Substitution: 
     * / --> _ +
     * --> -
     *
     * @param based
     * @return
     */
    public static String getBase64FromPathCompatible(String based) {
        return based.replace("_", "/").replace("-", "+");
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
