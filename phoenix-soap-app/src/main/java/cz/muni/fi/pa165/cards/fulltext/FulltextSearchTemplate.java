/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.fulltext;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.hibernate.search.FullTextFilter;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 *
 * @author ph4r05
 */
public class FulltextSearchTemplate {
    private static final Logger log = LoggerFactory.getLogger(FulltextSearchTemplate.class);
    
    @PersistenceContext
    private EntityManager em;
    
    /**
     * Fields to search in (within searchEntity).
     */
    private String[] searchFields=null;
    
    /**
     * Fulltext search entity
     */
    private java.lang.Class searchEntity=null;

    /**
     * Sort object to sort results of fulltext search query.
     * If null nothing happen.
     */
    private Sort searchSort=null;
    
    /**
     * Query filters to apply before query
     */
    private List<FulltextFilterEntity> queryFilters;
    
    public void setEm(EntityManager em) {
        this.em = em;
    }
    
    public FulltextSearchTemplate() {
    }

    /**
     * Constructor with entity manager defined
     * @param em 
     */
    public FulltextSearchTemplate(EntityManager em) {
        this.em = em;
    }   
    
    /**
     * Performs fulltext search on initialized template object. 
     * searchEntity and searchFields must be filled in!
     * 
     * Warning! Is not checked whether searchEntity contains searchFields. 
     * Exception should be thrown.
     * @param fulltextQuery
     * @return 
     */
    public List fulltextSearch(final String fulltextQuery){
        // check if fields are valid
        if (this.searchEntity==null || !(this.searchEntity instanceof Class)){
            throw new IllegalStateException("searchEntity is not valid");
        }
        
        if (this.searchFields==null || this.searchFields.length==0){
            throw new IllegalStateException("searchFields is not valid or empty");
        }
        
        return this.fulltextSearch(fulltextQuery, searchFields, searchEntity);
    }
    
    /**
     * Performs fulltext search on initialized template object. 
     * searchEntity must be filled in!
     * 
     * Warning! Is not checked whether searchEntity contains searchFields. 
     * Exception should be thrown.
     * @param fulltextQuery
     * @param fields
     * @return 
     */
    public List fulltextSearch(final String fulltextQuery, String[] fields){
        if (this.searchEntity==null || !(this.searchEntity instanceof Class)){
            throw new IllegalStateException("searchEntity is not valid");
        }
        
        return this.fulltextSearch(fulltextQuery, fields, searchEntity);
    }
        
    /**
     * Performs fulltext search on entities entity on attributes fields
     * 
     * Warning! Is not checked whether entity contains fields. 
     * Exception should be thrown.
     * 
     * Resets query filters after query
     * @param fulltextQuery
     * @param fields
     * @param entity
     * @return 
     */
    public List fulltextSearch(final String fulltextQuery, String[] fields, Class entity){ 
        Assert.notNull(fulltextQuery, "retezec pro vyhledavani nesmi byt null!");        
        
        // get fulltext entity manager, JPA entityManager is used
        FullTextEntityManager ftEm = Search.getFullTextEntityManager(this.em);

        // create parser for specified fields to search in, use standard analyzer
        QueryParser parser = new MultiFieldQueryParser(org.apache.lucene.util.Version.LUCENE_31,
                fields, 
                new StandardAnalyzer(org.apache.lucene.util.Version.LUCENE_31));

        // lucene query
        org.apache.lucene.search.Query luceneQuery;

        // test query = strip all whitespaces and asterix. If empty string remains, query=search for all
        // for example query "**** * **  *" will returns all records
        if (StringUtils.trimAllWhitespace(StringUtils.deleteAny(fulltextQuery, "*")).isEmpty()) {
            log.debug("MatchAllDocsQuery");
            luceneQuery = new MatchAllDocsQuery();
        } else {
            // restrictive query was entered
            try {
                // process fulltext query with lucene engine
                luceneQuery = parser.parse(fulltextQuery);
                log.debug("fulltextQuery for: " + fulltextQuery);
            } catch (Exception e) {
                this.resetFilters();
                // invalid query here. Exception is converted to runtime exception for now
                // TODO: refactor this exception
                log.error("Parse exception! Query: " + fulltextQuery, e);
                throw new RuntimeException("Fulltext query exception: " + fulltextQuery, e);
            }
        }
        
        // standard JPA query via fulltext interface
        FullTextQuery query = (FullTextQuery) ftEm.createFullTextQuery(
                luceneQuery,
                entity);
        
        if (this.queryFilters!=null && this.queryFilters.isEmpty()==false){
            this.applyFilters(query, queryFilters);
        }

        // sort if is sort-er correctly set
        if (this.searchSort != null && this.searchSort instanceof Sort){
            query.setSort(this.searchSort);
        }

        List resultList = query.getResultList();
        this.resetFilters();
        return resultList;
    }

    /**
     * Returns search entity to perform fulltext search on.
     * @return 
     */
    public Class getSearchEntity() {
        return searchEntity;
    }

    /**
     * Sets search entity to perform fulltext search on.
     * Resets search fields if is set to new variable in order to avoid 
     * errors. Thus entity must be set BEFORE fields.
     * @param searchEntity 
     */
    public void setSearchEntity(Class searchEntity2) {
        Class oldEntity = this.searchEntity;
        this.searchEntity = searchEntity2;
        
        if (oldEntity!=null && !(oldEntity.equals(this.searchEntity))){
            this.searchFields=null;
        }
    }
    
    /**
     * Applies filters to fulltext query
     * 
     * @param query
     * @param filters 
     */
    public void applyFilters(FullTextQuery query, List<FulltextFilterEntity> filters){
        if (filters==null){
            throw new NullPointerException("Cannot process empty filter list");
        }
        
        if (this.queryFilters!=null && this.queryFilters.isEmpty()==false){
            Iterator<FulltextFilterEntity> iterator = this.queryFilters.iterator();
            
            for(; iterator.hasNext() ;){
                FulltextFilterEntity filter = iterator.next();
                if (filter==null) {
                    throw new NullPointerException("filter entity cannot be null");
                }
                
                if (filter.isEnable()){
                    // enable filter
                    query.enableFullTextFilter(filter.getFilterName()).setParameter(filter.getParameter(), filter.getValue());
                } else {
                    // disable filter
                    query.disableFullTextFilter(filter.getFilterName());
                }
            }
        }
    }

    /**
     * Returns sorter for results of fulltext search query.
     * If null, nothing happen.
     */
    public Sort getSearchSort() {
        return searchSort;
    }

    /**
     * Sets sorter for results of fulltext search query.
     * If null, nothing happen.
     * @param searchSort 
     */
    public void setSearchSort(Sort searchSort) {
        this.searchSort = searchSort;
    }

    /**
     * Returns fulltext fields to search in within defined searchEntity
     * @return 
     */
    public String[] getSearchFields() {
        return searchFields;
    }

    /**
     * Sets fulltext fields to search in within defined searchEntity
     * @param searchFields 
     */
    public void setSearchFields(String[] searchFields) {
        this.searchFields = searchFields;
    }

    public List<FulltextFilterEntity> getQueryFilters() {
        return queryFilters;
    }

    /**
     * Query filters are used during query
     * @param queryFilters 
     */
    public void setQueryFilters(List<FulltextFilterEntity> queryFilters) {
        this.queryFilters = queryFilters;
    }
    
    public void resetFilters(){
        this.queryFilters = new LinkedList<FulltextFilterEntity>();
    }
}
