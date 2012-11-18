/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.fulltext;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.TermQuery;
import org.hibernate.search.annotations.Factory;
import org.hibernate.search.annotations.Key;
import org.hibernate.search.filter.FilterKey;
import org.hibernate.search.filter.StandardFilterKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner filter for objects having indexed field "ownedBy_Id"
 * @author ph4r05
 */
public class OwnerFilterFactory  {
    private static final Logger log = LoggerFactory.getLogger(OwnerFilterFactory.class);
    
    private Long ownedBy_Id;

    /**
     * injected parameter
     */
    public void setOwnedBy_Id(Long ownedBy_Id) {
        this.ownedBy_Id = ownedBy_Id;
    }

    public Long getOwnedBy_Id() {
        return ownedBy_Id;
    }

    @Key
    public FilterKey getKey() {        
        StandardFilterKey key = new StandardFilterKey();
        key.addParameter( ownedBy_Id );
        return key;
    }

    @Factory
    public Filter getFilter() {
        log.info("Filtering: " + ownedBy_Id);
        
        Query query = new TermQuery( new Term("ownedBy_Id", ownedBy_Id.toString() ) );
        return new CachingWrapperFilter( new QueryWrapperFilter(query) );
    }
}
