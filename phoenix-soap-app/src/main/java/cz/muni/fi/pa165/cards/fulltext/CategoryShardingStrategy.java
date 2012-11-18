/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.fulltext;

import cz.muni.fi.pa165.cards.db.User;
import java.io.Serializable;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.hibernate.search.FullTextFilter;
import org.hibernate.search.filter.FullTextFilterImplementor;
import org.hibernate.search.store.DirectoryProvider;
import org.hibernate.search.store.IndexShardingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sharding strategy to support multiple users in shared environment
 * @see {@link http://docs.jboss.org/hibernate/search/3.4/reference/en-US/html_single/#search-configuration-directory-sharding}
 * 
 * @deprecated yet, not used in fulltext search
 * @author ph4r05
 */
public class CategoryShardingStrategy implements IndexShardingStrategy {

    private static final Logger log = LoggerFactory.getLogger(CategoryShardingStrategy.class);
    // stored DirectoryProviders in a array indexed by customerID
    private DirectoryProvider<?>[] providers;

    @Override
    public void initialize(Properties properties, DirectoryProvider<?>[] providers) {
        this.providers = providers;
        log.info("Sharding strategy initialized with providers");
    }

    @Override
    public DirectoryProvider<?>[] getDirectoryProvidersForAllShards() {
        return providers;
    }

    @Override
    public DirectoryProvider<?> getDirectoryProviderForAddition(
            Class<?> entity, Serializable id, String idInString, Document document) {
        Integer customerID = Integer.parseInt(document.getField("ownedBy_Id").stringValue());
        return providers[customerID];
    }

    @Override
    public DirectoryProvider<?>[] getDirectoryProvidersForDeletion(
            Class<?> entity, Serializable id, String idInString) {
        return getDirectoryProvidersForAllShards();
    }

    /**
     * Optimization; don't search ALL shards and union the results; in this case, we 
     * can be certain that all the data for a particular customer Filter is in a single
     * shard; simply return that shard by customerID.
     * 
     * Select shard depending on currenly selected items.
     */
    @Override
    public DirectoryProvider<?>[] getDirectoryProvidersForQuery(
            FullTextFilterImplementor[] filters) {

        log.info("Sharding strategy called for query");
        FullTextFilter filter = getFilter(filters, "owner");
        if (filter == null) {
            return getDirectoryProvidersForAllShards();
        } else {
            Object parameter = filter.getParameter("ownedBy_Id");
            if (parameter != null) {
                return new DirectoryProvider[]{providers[Integer.parseInt(
                            filter.getParameter("ownedBy_Id").toString())]};
            }

            return getDirectoryProvidersForAllShards();
        }
    }

    private FullTextFilter getFilter(FullTextFilterImplementor[] filters, String name) {
        for (FullTextFilterImplementor filter : filters) {
            if (filter.getName().equals(name)) {
                return filter;
            }
        }
        return null;
    }
}