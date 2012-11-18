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
 *
 * @author ph4r05
 */
public class CategoryFilterFactory {

    private static final Logger log = LoggerFactory.getLogger(CategoryFilterFactory.class);
    private Boolean privateCategory;

    /**
     * injected parameter
     */
    public void setPrivateCategory(Boolean privateCategory) {
        this.privateCategory = privateCategory;
    }

    public Boolean getPrivateCategory() {
        return privateCategory;
    }

    @Key
    public FilterKey getKey() {
        StandardFilterKey key = new StandardFilterKey();
        key.addParameter(privateCategory);
        return key;
    }

    @Factory
    public Filter getFilter() {
        log.debug("Filtering private: " + (privateCategory ? "true" : "false"));

        Query query = new TermQuery(new Term("privateCategory", privateCategory.toString()));
        return new CachingWrapperFilter(new QueryWrapperFilter(query));
    }
}
