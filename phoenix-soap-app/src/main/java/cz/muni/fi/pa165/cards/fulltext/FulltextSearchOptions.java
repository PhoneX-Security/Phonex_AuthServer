/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.fulltext;

import cz.muni.fi.pa165.cards.db.User;
import org.apache.lucene.search.Sort;

/**
 * FulltextSearch options for searching in categories and cards
 *  Supports:
 *      - owner binding
 *      - public only
 *      - pagination
 *      - sorter
 * @author ph4r05
 */
public class FulltextSearchOptions {
    private User userToRestrictOn;
    private Boolean onlyPublic;
    private boolean paginationEnabled;
    private Integer recordsPerPage;
    private Integer page;
    private Sort resultSorter;

    public Boolean getOnlyPublic() {
        return onlyPublic;
    }

    public void setOnlyPublic(Boolean onlyPublic) {
        this.onlyPublic = onlyPublic;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public boolean isPaginationEnabled() {
        return paginationEnabled;
    }

    public void setPaginationEnabled(boolean paginationEnabled) {
        this.paginationEnabled = paginationEnabled;
    }

    public Integer getRecordsPerPage() {
        return recordsPerPage;
    }

    public void setRecordsPerPage(Integer recordsPerPage) {
        this.recordsPerPage = recordsPerPage;
    }

    public Sort getResultSorter() {
        return resultSorter;
    }

    public void setResultSorter(Sort resultSorter) {
        this.resultSorter = resultSorter;
    }

    public User getUserToRestrictOn() {
        return userToRestrictOn;
    }

    public void setUserToRestrictOn(User userToRestrictOn) {
        this.userToRestrictOn = userToRestrictOn;
    }
}
