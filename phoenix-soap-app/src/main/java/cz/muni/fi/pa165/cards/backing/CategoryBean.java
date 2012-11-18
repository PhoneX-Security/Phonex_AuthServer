package cz.muni.fi.pa165.cards.backing;

import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchOptions;
import cz.muni.fi.pa165.cards.helpers.LocUtil;
import cz.muni.fi.pa165.cards.managers.CategoryManager;
import cz.muni.fi.pa165.cards.managers.UserManager;
import cz.muni.fi.pa165.cards.models.CategoryFlattenWrapper;
import cz.muni.fi.pa165.cards.models.CategoryViewModel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.application.FacesMessage.Severity;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;

/**
 *
 * @author ph4r05
 */
@ManagedBean(name="categoryBean")
@SessionScoped
public class CategoryBean implements Serializable {
    private static final long serialVersionUID = 112312354;
    private static final Logger log = LoggerFactory.getLogger(CategoryBean.class);

    @Autowired
    protected transient UserManager userManager;
    
    @Autowired
    protected transient CategoryManager categoryManager;
    
    @Autowired
    protected UserBean userBean;
    
    @PersistenceContext
    protected EntityManager em;
    
    protected Map<String, ArrayList<SelectItem>> selectItems;
    protected ArrayList<CategoryFlattenWrapper> categoriesListWrap;
    protected User loggedUser = null;
    
    protected Category currentSelectedCategory = null;
    protected CategoryViewModel currentCategoryModel = null;
    protected transient CategoryViewModel newParent = null;
    protected transient CategoryViewModel newCategory = null;
    
    private String fulltextSearchQuery;
    private boolean searchInDescription=false;
    private transient List<CategoryFlattenWrapper> searchResult;
    private long curCategoryId=-1;
    private int itemsPerPage=15;
    private int page=1;
    private boolean showAll=false;
    
    private static final int DELETE_TYPE_RECURSIVE=1;
    private static final int DELETE_TYPE_HEAD=2;
    
    private static final String SELECT_ITEMS_CATEGORIES="categories";
    private static final String SELECT_ITEMS_CATEGORIES_WRAPPED="categories_wrapped";
    private static final String SELECT_ITEMS_PARENT_CHANGE="parent_change";
    
    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    public void setCategoryManager(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }
    
    public void setEm(EntityManager em) {
        this.em = em;
    }

    public void setUserBean(UserBean userBean) {
        this.userBean = userBean;
    }
    
    @PostConstruct
    public void initBean(){
        log.debug("PostConstructed CategoryBean initialized");
        this.changedSelectedCategoryEvent(null);
        this.fulltextSearchQuery="";
        this.searchResult = new ArrayList<CategoryFlattenWrapper>();
    }
    
    /**
     * Check if user is logged in and can be extracted using userBean.
     * True if yes, then is user stored in loggedUser attribute.
     * @return 
     */
    public boolean loadLoggedUser(){
        // init category list
        if (this.userBean.isLoggedIn()==false){
            // not logged in
            return false;
        }
        
        // logged in user
        this.loggedUser = this.userBean.getLoggedUser();
        if (this.loggedUser==null){
            return false;
        }
        
        return true;
    }
    
    /**
     * Initialize category itemLists for combo boxes
     */
    public void initCategories(){
        // init to avoid null pointer exceptions
        this.categoriesListWrap = new ArrayList<CategoryFlattenWrapper>(8);
        this.selectItems = new HashMap<String, ArrayList<SelectItem>>();
        // init hashmap
        this.selectItems.put(SELECT_ITEMS_CATEGORIES, new ArrayList<SelectItem>());
        this.selectItems.put(SELECT_ITEMS_PARENT_CHANGE, new ArrayList<SelectItem>());
        
        // init category list
        if (this.loadLoggedUser()==false){
            log.warn("Cannot get user");
            return;
        }
        
        //Category rootForUser = this.categoryManager.getCategoryTreeForUser(this.loggedUser);
        Category rootForUser = this.categoryManager.getRootForUser(this.loggedUser);
        
        this.categoryManager.unLazyCategoryChildren(rootForUser);
        
        List<CategoryFlattenWrapper> flattenHierarchyExtended = this.categoryManager.flattenHierarchyExtended(rootForUser);
        if (flattenHierarchyExtended.isEmpty()){
            // hierarchy is null, nothing to do
            return;
        }
        
        // generate paths
        this.categoryManager.generatePath2root(flattenHierarchyExtended);
        // generate list
        this.selectItems.put(SELECT_ITEMS_CATEGORIES, this.categoryManager.generateSelectItemFromCategories(flattenHierarchyExtended));
        this.selectItems.put(SELECT_ITEMS_CATEGORIES_WRAPPED, this.categoryManager.generateSelectItemFromCategories(flattenHierarchyExtended, true));
        // generate parent list - remove selected category subtree
        List<CategoryFlattenWrapper> removedCategorySubtree = this.removeCategorySubtree(flattenHierarchyExtended, currentCategoryModel);
        this.selectItems.put(SELECT_ITEMS_PARENT_CHANGE, this.categoryManager.generateSelectItemFromCategories(removedCategorySubtree));
        
        log.info("Categories initialized ");
    }

    /**
     * Removes category subtree from list
     * (Used when changing category subtree, category obviously cannot have new parent 
     * from ist own subtree)
     * 
     * Category comparison based on ID
     * @param collection
     * @param cat
     * @return 
     */
    public List<CategoryFlattenWrapper> removeCategorySubtree(
            List<CategoryFlattenWrapper> collection, CategoryViewModel cat){
        if (collection==null){
            throw new NullPointerException("Category or collection is null");
        }
        
        if (cat==null || cat.getId()==null){
            // now ok, do not remove anything
            return collection;
        }
        
        // level which has target category in collection
        int level=-1;
        // actual state of algorithm, erasing phase = erasing all subcategories until
        // node with same level as original category occurrs
        boolean erasingPhase=false;
        Iterator<CategoryFlattenWrapper> iterator = collection.iterator();
        for(int i=0; iterator.hasNext(); i++){
            CategoryFlattenWrapper curWrapper = iterator.next();
            
            // erasing phase?
            if (erasingPhase==false){
                // only detection phase
                if (cat.getId().equals(curWrapper.getCat().getId())){
                    // category found
                    // switch to erasing phase of algorithm
                    erasingPhase=true;

                    // mark level to return to
                    level = curWrapper.getLevel();
                    // remove inclusive
                    iterator.remove();
                    continue;
                }
            } else {
                // erase phase, look for categories with defined level.
                // if found, stop algorithm
                if (curWrapper.getLevel() <= level){
                    // stoping on this category, exit algorithm
                    break;
                } else {
                    // continue, remove this element
                    iterator.remove();
                }
            }
        }
        
        return collection;
    }

    /**
     * Returns true if some category is currently selected and is not null
     * @return 
     */
    public boolean isCategorySelected(){
        return this.currentSelectedCategory!=null;
    }
    
    /**
     * Restores fulltext search according to state
     */
    public void restoreFulltextSearch(){
        log.info("Restoring fulltext search state");
        if (this.showAll){
            this.getAllCategories();
        } else {
            this.fulltextSearch();
        }
    }
    
    public void resetFulltextSearch(){
        log.info("Reseting fulltext search state");
        this.searchResult = new ArrayList<CategoryFlattenWrapper>();
        this.fulltextSearchQuery = "";
        this.showAll=false;
    }
    
    /**
     * Event triggered when category tree changes
     */
    public void categoryTreeChanged(){
        log.info("Category tree changed");
        //this.resetFulltextSearch();
        this.restoreFulltextSearch();
    }
    
    /**
     * Called when successfully changed current category - updates model
     */
    public void changedSelectedCategoryEvent(Category newCategory){
        log.info("Category changed event");
        this.currentSelectedCategory = newCategory;
        this.currentCategoryModel = null;
        this.newParent = null;
        this.newCategory = new CategoryViewModel();
        
        // init model
        this.currentCategoryModel = new CategoryViewModel();
        if(this.currentSelectedCategory!=null){
            // keep synchronized
            this.currentCategoryModel.initFrom(this.currentSelectedCategory);
        }
        // reinit category trees
        this.initCategories();

        // output
        System.out.println("Event, changed category: " + (this.currentSelectedCategory==null ? "null" : this.currentSelectedCategory.toString()));
    }
    
    /**
     * Current selected category was changed (to edit)
     * @return 
     */
    public String changedCurrentCategory(){
        log.info("changedCurrentCategory");
        if (this.currentCategoryModel!=null && this.currentCategoryModel.getId()!=null){
            System.out.println("Category changed2: " + this.currentCategoryModel);
        } else {
            System.out.println("Category changed2: null");
            this.changedSelectedCategoryEvent(null);
            // delete fulltext results
            return "OK";
        }
        
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("Logged out");
            return "LOGGED_OUT";
        }
        
        try{
            long parsedInt = this.currentCategoryModel.getId();
            // load category from database, check user
            Category cat = this.categoryManager.findById(parsedInt);
            if (cat==null){
                log.error("Inconsistency, currrently selected category cannot be loaded.");
                System.out.println("Inconsistency, currrently selected category cannot be loaded.");
                this.changedSelectedCategoryEvent(null);
                return "NULL";
            }
            
            // user check
            if (this.loggedUser.getId().equals(cat.getOwner().getId())==false){
                log.error("Inconsistency, somebody tries to load different categories.");
                System.out.println("Inconsistency, somebody tries to load different categories.");
                this.changedSelectedCategoryEvent(null);
                return "HACK";
            }
            
            this.changedSelectedCategoryEvent(cat);
            // delete fulltext results
            this.resetFulltextSearch();
            return  "OK";
        } catch(Exception e){
            log.error("Exception occurred when changing current selected category", e);
            
            // add message
            this.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("cannotChangeCategory"), e.getLocalizedMessage());
            
            return "EXCEPTION";
        }
    }

    /**
     * Change parent for currently selected category
     * SelectedCategory = currentSelectedCategory
     * @return 
     */
    public String changeParent(){
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // current category selected?
        if (this.currentSelectedCategory==null){
            System.out.println("NO_SELECTED");
            return "NO_SELECTED";
        }
        
        // cannot change category to root
        if (this.newParent==null){
            // error message here!
            System.out.println("NULL_PARENT");
            return "NULL_PARENT";
        }
        
        // load both categories, compare and try to do some changes
        try {
            Category curCat = this.categoryManager.findById(this.currentSelectedCategory.getId());
            Category newParentCat = this.categoryManager.findById(this.newParent.getId());
            
            // check on null
            if (curCat==null || newParentCat==null){
                throw new NullPointerException("Empty categories");
            }
            
            // logged user to both?
            if (curCat.getOwner().getId().equals(this.loggedUser.getId()) == false
                    || newParentCat.getOwner().getId().equals(this.loggedUser.getId()) == false){
                throw new IllegalStateException("Security - owner different from categories");
            }
            
            // validation is performed by CategoryManager
            this.categoryManager.changeCategoryParent(curCat, newParentCat);
            // reset current category parent
            this.newParent=null;
            // refresh current model
            this.currentCategoryModel.initFrom(curCat);
            // reinit category lists and models
            this.initCategories();
            
            System.out.println("OK");
            return "OK";
        } catch(Exception e){
            log.error("Cannot change category parent", e);
            // add message
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("categoryChangeParentFail"), e.getLocalizedMessage());
            
            return "EXCEPTION";
        }
    }
    
    /**
     * Action called when updating plain category data
     * @return 
     */
    public String updateCategory(){
         // user check
        System.out.println("Updating category");
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // current category selected?
        if (this.currentSelectedCategory==null){
            System.out.println("NO_SELECTED");
            return "NO_SELECTED";
        }
        
        // check current model 
        if (this.currentCategoryModel==null){
            System.out.println("EMPTY");
            return "EMPTY";
        }
        
        // get data category according to selected from database
        // load both categories, compare and try to do some changes
        try {
            Category curCat = this.categoryManager.findById(this.currentSelectedCategory.getId());
            
            // check on null
            if (curCat==null){
                throw new NullPointerException("Empty categories");
            }
            
            // logged user to both?
            if (curCat.getOwner().getId().equals(this.loggedUser.getId()) == false){
                throw new IllegalStateException("Security - owner different from categories");
            }
            
            // change details
            this.currentCategoryModel.copyTo(curCat);
            this.categoryManager.saveCategory(curCat);
            // refresh current model
            this.currentCategoryModel.initFrom(curCat);
            // reinit category lists and models
            this.changedSelectedCategoryEvent(curCat);
            this.categoryTreeChanged();
            System.out.println("OK");
            
            // add message
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                    LocUtil.getMessageFromDefault("categoryUpdateOK"), "");
            
            return "OK";
        } catch(Exception e){
            log.error("Update category failed", e);
            // add message
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("categoryUpdateFail"), e.getLocalizedMessage());
            
            return "EXCEPTION";
        }
    }
    
    /**
     * Action adds new category under currently selected category
     * @return 
     */
    public String addCategory(){
        System.out.println("addCategory");
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // current category selected?
        if (this.currentSelectedCategory==null){
            System.out.println("NO_SELECTED");
            return "NO_SELECTED";
        }
        
        // check current model 
        if (this.newCategory==null){
            System.out.println("EMPTY");
            return "EMPTY";
        }
        
        // get data category according to selected from database
        // load both categories, compare and try to do some changes
        try {
            Category curCat = this.categoryManager.findById(this.currentSelectedCategory.getId());
            
            // check on null
            if (curCat==null){
                throw new NullPointerException("Empty categories");
            }
            
            // logged user to both?
            if (curCat.getOwner().getId().equals(this.loggedUser.getId()) == false){
                throw new IllegalStateException("Security - owner different from categories");
            }
            
            // create a new category
            Category c = new Category();
            c.setId(null);
            this.newCategory.copyTo(c);
            c.setOwner(loggedUser);
            c.setParent(curCat);
            
            // change details
            this.categoryManager.saveCategory(c);
            // refresh new category
            this.newCategory = new CategoryViewModel();
            // reinit category lists and models
            this.changedSelectedCategoryEvent(curCat);
            this.categoryTreeChanged();
            
            // add message
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                    LocUtil.getMessageFromDefault("categoryAddOK"), "");
            
            log.debug("Category added");
            return "OK";
        } catch(Exception e){
            log.error("Error occurred during inserting new category", e);
            
            // add message
            this.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("categoryAddError"), e.getLocalizedMessage());
            
            return "EXCEPTION";
        }
    }
    
    /**
     * Deletes only currently selected category. 
     * All its children are moved to defined parent (by default parent of
     * selected category)
     * @return 
     */
    public String deleteCategoryHead(){
        return this.deleteCategory(DELETE_TYPE_HEAD);
    }
    
    /**
     * Deletes recursively category subtree rooted at selected category
     * @return 
     */
    public String deleteCategoryRecursive(){
        return this.deleteCategory(DELETE_TYPE_RECURSIVE);
    }
    
    /**
     * Action adds new category under currently selected category
     * @return 
     */
    public String deleteCategory(int deleteType){
        System.out.println("removeCategory");
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // current category selected?
        if (this.currentSelectedCategory==null){
            System.out.println("NO_SELECTED");
            return "NO_SELECTED";
        }
        
        // get data category according to selected from database
        // load both categories, compare and try to do some changes
        try {
            Category curCat = this.categoryManager.findById(this.currentSelectedCategory.getId());
            
            // check on null
            if (curCat==null){
                throw new NullPointerException("Empty categories");
            }
            
            // logged user to both?
            if (curCat.getOwner().getId().equals(this.loggedUser.getId()) == false){
                throw new IllegalStateException("Security - owner different from categories");
            }
            
            // what to do depends on delete type
            if (deleteType==DELETE_TYPE_RECURSIVE){
                // delete recurcively whole subtree
                this.categoryManager.deleteCategory(curCat);
            } else if(deleteType==DELETE_TYPE_HEAD) {
                // move all subcats to new parent
                this.categoryManager.moveSubCategories(curCat, curCat.getParent());
                // delete category
                this.categoryManager.deleteCategory(curCat);
            } else {
                return "NO_SUCH_METHOD";
            }
            
            // reset selected categories
            this.currentCategoryModel = null;
            this.currentSelectedCategory = null;
            // reinit category lists and models
            this.changedSelectedCategoryEvent(null);
            this.categoryTreeChanged();
            
            log.debug("Category deleted");
            
            // add message
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                    LocUtil.getMessageFromDefault("categoryDeletedOK"), "");
            
            return "OK";
        } catch(Exception e){
            log.error("Cannot remove category", e);
            
            // add message
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("categoryDeleteError"), e.getLocalizedMessage());
            return "EXCEPTION";
        }
    }
    
    /**
     * Selected category from table
     * @return 
     */
    public String selectNodeFromTable(){
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // is useles?
        if (this.curCategoryId<=0){
            return "NOPE";
        }
        
        // try to load and select new
        try {
            Category cat = this.categoryManager.findById(curCategoryId);
            if (cat==null){
                log.debug("Loaded category is null, id: " + curCategoryId);
                return "NULL";
            }
            
            if (this.loggedUser.equals(cat.getOwner())==false){
                log.warn("Problem with user, different from loaded, catID: " + curCategoryId
                        + "; user: " + this.loggedUser);
                return "ERROR";
            }
            
            // ok here
            this.changedSelectedCategoryEvent(cat);
            return "OK";
        } catch(Exception e){
            log.error("Something badd happened during category load", e);
            return "ERROR";
        }
    }
    
    /**
     * Fill table with all categories, alphabetically sorted
     * @return 
     */
    public String getAllCategories(){
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // reset fulltext search string (not relevant now)
        this.fulltextSearchQuery="";
        
        // get root
        Category rootForUser = this.categoryManager.getRootForUser(this.loggedUser);
        this.categoryManager.unLazyCategoryChildren(rootForUser);
        this.searchResult = this.categoryManager.flattenHierarchyExtended(rootForUser);
        this.categoryManager.generatePath2root(this.searchResult);
        this.showAll=true;
        return "OK";
    }
    
    /**
     * Action performed on fulltext search
     * @return 
     */
    public String fulltextSearch(){
        // user check
        if (this.loadLoggedUser()==false){
            System.out.println("LOGGED_OUT");
            return "LOGGED_OUT";
        }
        
        // search query
        if (this.fulltextSearchQuery.isEmpty()){
            // query is empty, reset
            this.resetFulltextSearch();
            return "RESET";
        }
        
        try {
            // define options here
            FulltextSearchOptions searchOptions = new FulltextSearchOptions();
            searchOptions.setUserToRestrictOn(loggedUser);
            
            // test emptynes of query
            List<Category> searchResultCat = 
                    this.categoryManager.fulltextSearch(this.fulltextSearchQuery, 
                    true, this.searchInDescription, searchOptions);
            
            // for each category get path2root and re-wrap
            List<CategoryFlattenWrapper> searchResultWrap = new ArrayList<CategoryFlattenWrapper>(searchResultCat.size());
            Iterator<Category> iterator = searchResultCat.iterator();
            for(int i=0; iterator.hasNext(); i++){
                Category curCat = iterator.next();
                CategoryFlattenWrapper curWrap = new CategoryFlattenWrapper(curCat);
                searchResultWrap.add(curWrap);
            }
            
            // add path2root here
            this.categoryManager.generatePath2root(searchResultWrap);
            this.searchResult = searchResultWrap;
            this.showAll=false;
        } catch(Exception e){
            log.warn("Something went wrong during fulltext searching", e);
            // send message to client
            this.sendMessage("fulltextSearchForm:fulltextSearchQueryInput", FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("fulltextQueryException"), e.getLocalizedMessage());
            
            return "ERROR";
        }
        
        return "OK";
    }
    
    /**
     * Reinitializes all fulltext indexes.
     * TODO: reannotate with spring security annotations
     * @return 
     */
    public String reinitFulltext(){
        try {
            this.categoryManager.rebuildIndex(false, false);
            
            this.sendMessage(null, FacesMessage.SEVERITY_INFO, 
                    LocUtil.getMessageFromDefault("fulltextRebuildOK"), "");
        } catch(Exception e){
            log.error("There exception occurred during fulltext index rebuilding", e);
            
            this.sendMessage(null, FacesMessage.SEVERITY_ERROR, 
                    LocUtil.getMessageFromDefault("fulltextRebuildError"), e.getLocalizedMessage());
        }
        
        return  "OK";
    }
    
    /**
     * Send message to user
     * 
     * @param sev
     * @param summary
     * @param detail 
     */
    public void sendMessage(String destination, Severity sev, String summary, String detail){
        // add message
        FacesContext.getCurrentInstance().addMessage(destination,
                new FacesMessage(sev, summary, detail));
        System.out.println("Adding message to destination: " + (destination==null ? "null" : destination));
    }
    
    public ArrayList<CategoryFlattenWrapper> getCategoriesListWrap() {
        return categoriesListWrap;
    }

    public Category getCurrentSelectedCategory() {
        return currentSelectedCategory;
    }

    public void setCurrentSelectedCategory(Category currentSelectedCategory) {
        this.currentSelectedCategory = currentSelectedCategory;
    }
    
    public CategoryViewModel getCurrentCategoryModel() {
        return currentCategoryModel;
    }

    public void setCurrentCategoryModel(CategoryViewModel currentCategoryModel) {
        this.currentCategoryModel = currentCategoryModel;
    }

    public Map<String, ArrayList<SelectItem>> getSelectItems() {
        return selectItems;
    }

    public void setSelectItems(Map<String, ArrayList<SelectItem>> selectItems) {
        this.selectItems = selectItems;
    }

    public CategoryViewModel getNewParent() {
        return newParent;
    }

    public void setNewParent(CategoryViewModel newParent) {
        this.newParent = newParent;
    }

    public CategoryViewModel getNewCategory() {
        return newCategory;
    }

    public void setNewCategory(CategoryViewModel newCategory) {
        this.newCategory = newCategory;
    }

    public String getFulltextSearchQuery() {
        return fulltextSearchQuery;
    }

    public void setFulltextSearchQuery(String fulltextSearchQuery) {
        this.fulltextSearchQuery = fulltextSearchQuery;
    }

    public boolean isSearchInDescription() {
        return searchInDescription;
    }

    public void setSearchInDescription(boolean searchInDescription) {
        this.searchInDescription = searchInDescription;
    }

    public List<CategoryFlattenWrapper> getSearchResult() {
        return searchResult;
    }

    public void setSearchResult(List<CategoryFlattenWrapper> searchResult) {
        this.searchResult = searchResult;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public long getCurCategoryId() {
        return curCategoryId;
    }

    public void setCurCategoryId(long curCategoryId) {
        this.curCategoryId = curCategoryId;
    }

    public boolean isShowAll() {
        return showAll;
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }
}
