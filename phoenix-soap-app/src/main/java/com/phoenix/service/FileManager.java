/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import javax.annotation.PostConstruct;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.bouncycastle.util.encoders.Base64;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

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
     * @param nonce2
     * @param type
     * @return 
     */
    public String generateFileName(String nonce2, String type){
        return nonce2+"_"+type;
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
