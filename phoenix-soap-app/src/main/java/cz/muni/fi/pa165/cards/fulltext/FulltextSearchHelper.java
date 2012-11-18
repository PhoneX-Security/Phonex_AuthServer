/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.fulltext;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.apache.commons.io.FileUtils;
import org.hibernate.CacheMode;
import org.hibernate.search.MassIndexer;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.impl.FullTextEntityManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helps to organize and perform maintenance operations with fulltext search 
 * engine based on lucene.
 * @author ph4r05
 */
public class FulltextSearchHelper {
    private static final Logger log = LoggerFactory.getLogger(FulltextSearchHelper.class);
    
    @PersistenceContext
    private EntityManager em;
    
    private String indexDirectory;
    
    public void setEm(EntityManager em) {
        this.em = em;
    }

    public String getIndexDirectory() {
        return indexDirectory;
    }

    public void setIndexDirectory(String indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public FulltextSearchHelper() {
    }

    public FulltextSearchHelper(EntityManager em) {
        this.em = em;
    }
    
    /**
     * Deletes index directory, recursively
     */
    public void deleteIndexDirectory() throws IOException{
        
        File indexDirectoryDir=new File(this.indexDirectory);
        boolean exists = indexDirectoryDir.exists();
        
        if (exists){
            FileUtils.deleteDirectory(indexDirectoryDir);
            log.info("Fulltext index was deleted");
        }
    }
    
    /**
     * Creates index directory if it does not exists
     */
    public void touchDirectory(){
        File indexDirectoryDir=new File(this.indexDirectory);
        if (indexDirectoryDir.exists()==false){
            indexDirectoryDir.mkdir();
            log.info("New fulltext index was created");
        }
    }
    
    /**
     * Rebuild all fulltext indexes defined on all entities.
     * Default behavior is to build it in this thread (blocking)
     */
    public void rebuildIndex(){
        this.rebuildIndex(true);
    }
    
    /**
     * Rebuild all fulltext indexes defined on all entities
     * 
     * Warning!
     * For rebuilding indexes is data needed to be already stored in database.
     * (Cannot insert data in one transaction, then rebuild in single transaction)
     * 
     * @see {@link http://relation.to/Bloggers/HibernateSearch32FastIndexRebuild}
     * 
     * @param batchSizeLoad         number of records to be loaded in one batch
     * @param threadCountForFetch   threads to spawn for subsequent fetch
     * @param threadCountForLoad    threads to spawn for data loading
     * @param threadCountForWriter  threads to spawn for index writing
     * @param blocking              if false, method waits untill rebuilding is finished.
     */
    public void rebuildIndex(int batchSizeLoad, int threadCountForFetch, 
            int threadCountForLoad, int threadCountForWriter, boolean blocking){
        
        FullTextEntityManager m = new FullTextEntityManagerImpl(this.em);
        
        try {
            MassIndexer massIndexer = m.createIndexer();
            //massIndexer.startAndWait();
            
                   massIndexer.purgeAllOnStart( true ) // true by default, highly recommended
                   //.optimizeAfterPurge( true ) // true is default, saves some disk space
                   .optimizeOnFinish( true ) // true by default
                   .batchSizeToLoadObjects( batchSizeLoad )
                   .threadsForSubsequentFetching( threadCountForFetch )
                   .threadsToLoadObjects( threadCountForLoad )
                   .batchSizeToLoadObjects( batchSizeLoad )
                   .threadsForIndexWriter( threadCountForWriter )
                   .cacheMode(CacheMode.NORMAL); // defaults to CacheMode.IGNORE
            
            if (blocking){
               massIndexer.startAndWait();
            } else {
               massIndexer.start();
            }            
        } catch (InterruptedException ex) {
            log.error("Exception during fulltext rebuilding", ex);
        }
    }
    
    /**
     * Rebuild all fulltext indexes defined on all entities
     * @param boolean blocking
     *  if yes, then index rebuilding is performed in calling thread. Otherwise is
     *  spawned another thread to do it in background.
     * 
     * Warning!
     * For rebuilding indexes is data needed to be already stored in database.
     * (Cannot insert data in one transaction, then rebuild in single transaction)
     */
    public void rebuildIndex(boolean blocking){                   
        this.rebuildIndex(30, 8, 4, 3, blocking);
    }
    
    /**
     * Rebuild fulltext index on all entities given
     * @param col2index 
     */
    public void rebuildIndexOn(Collection<?> col2index){
        if (col2index==null){
            throw new NullPointerException("Cannot be null");
        }
        
        FullTextEntityManager m = new FullTextEntityManagerImpl(this.em);
        Iterator<?> iterator = col2index.iterator();
        while(iterator.hasNext()){
            Object next = iterator.next();
            m.index(next);
        }
    }
}
