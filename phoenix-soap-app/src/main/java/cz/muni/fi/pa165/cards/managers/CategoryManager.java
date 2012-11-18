package cz.muni.fi.pa165.cards.managers;

import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.fulltext.FulltextFilterEntity;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchHelper;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchOptions;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchTemplate;

import cz.muni.fi.pa165.cards.models.CategoryFlattenWrapper;
import cz.muni.fi.pa165.cards.models.CategoryModelWrapper;
import cz.muni.fi.pa165.cards.models.CategoryViewModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.faces.model.SelectItem;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Class managing categories and hierarchy system.
 * 
 * @author ph4r05
 */
@Repository
@Transactional
public class CategoryManager {
    private static final long serialVersionUID = 112312357;
    public static final int FLATTEN_STRATEGY_INORDER = 1;
    
    @PersistenceContext
    private EntityManager em;
    
    @Autowired
    private JdbcTemplate template;
    
    @Autowired
    private FulltextSearchHelper ftsh;
    
    private FulltextSearchTemplate ftst;
    
    private static final Logger log = LoggerFactory.getLogger(CategoryManager.class);

    public void setTemplate(JdbcTemplate template) {
        this.template = template;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    public void setFtsh(FulltextSearchHelper ftsh) {
        this.ftsh = ftsh;
    }

    public void setFtst(FulltextSearchTemplate ftst) {
        this.ftst = ftst;
    }

    /**
     * Performs validation before inserting new category to avoid inconsistency
     * (multiple roots for single user, parent of different user). Can be called 
     * from another modules when working with user inputs.
     * 
     * Returns 0 if can save category
     * 
     * @param cat Category to be validated
     * @return 
     */
    public int canSaveCategory(Category cat) {
        if (cat == null) {
            // cannot save null category
            return 2;
        }

        // check if has category filled in ID
        if (cat.getId() != null) {
            // category has filled in ID, if is not managed, persisting such
            // entity would cause error, do not allow this
            if (this.em.contains(cat) == false) {
                // entity is not managed. EM.persist() does not allow to define own
                // primary key here.
                return 3;
            }
        }

        // inspect category owner here
        User catOwner = cat.getOwner();
        if (catOwner == null) {
            // category cannot have empty owner. Has to belong to somebody
            return 4;
        }

        Category parent = cat.getParent();
        if (parent == null) {
            // if parent is null, check whether current user has some root category
            // if yes, then this is error, single user can have only single root
            Category rootForUser = this.getRootForUser(catOwner, false);

            // check if this category is actually saved
            if (cat.getId() == null && rootForUser != null) {
                // here somebody wants to store new root for user, but user has
                // already some root defined
                return 5;
            }

            // check for root collision
            if (rootForUser != null && cat.equals(rootForUser) == false) {
                // entity is already managed, but user has another root, return error
                return 6;
            }
        } else {
            // check if parent has same owner
            if (catOwner.equals(parent.getOwner()) == false) {
                // category cannot have parent from different user tree, inconsistency
                return 7;
            }
        }

        return 1;
    }

    /**
     * Saves defined category to db
     * 
     * @param cat
     */
    @Transactional
    public void saveCategory(Category cat) {
        if (cat == null) {
            throw new NullPointerException("Cannot save empty string");
        }

        em.persist(cat);
        em.flush();
    }

    /**
     * Deletes category, remove all sub-categories. Should be transactional to
     * keep DB in consistent state
     * 
     * @param cat
     */
    @Transactional
    public void deleteCategory(Category cat) {
        if (cat == null) {
            throw new NullPointerException("Cannot delete null category");
        }

        // delete whole category sub-tree rooted at cat
        this.deleteAllSubcategories(cat);

        // delete parent association
        this.changeCategoryParent(cat, null, false);

        // finally remove category from database
        em.remove(cat);
    }

    /**
     * Reloads fresh copy of category dropping modifications away
     * 
     * @param cat
     */
    @Transactional
    public void reloadCategory(Category cat) {
        this.em.refresh(cat);
    }
    
    /**
     * Detaches object from EntityManager
     * @param cat 
     */
    public void detach(Category cat){
        this.em.detach(cat);
    }

    /**
     * Abstracts change category parent routine. If category hierarchy is
     * implemented by own way, this handles special cases.
     * 
     * WARNING! New category parent cannot be from category subtree. Scenario:
     * A->B; B->C; If newParent(A)=B, cycle will be induced. Need to search if B
     * NOT IN path2root(newParent(A))
     * 
     * By default such change is validated.
     * 
     * @param object
     * @param newParent
     */
    @Transactional
    public void changeCategoryParent(Category object, Category newParent) {
        this.changeCategoryParent(object, newParent, true);
    }
    
    /**
     * Abstracts change category parent routine. If category hierarchy is
     * implemented by own way, this handles special cases.
     * 
     * WARNING! New category parent cannot be from category subtree. Scenario:
     * A->B; B->C; If newParent(A)=B, cycle will be induced. Need to search if B
     * NOT IN path2root(newParent(A))
     * 
     * @param object
     * @param newParent
     * @param validate  If true some consistency checks are performed. Particularly
     *  is checked whether holds that there can be only one root category per user.
     *  When deleting category object, newParent=null thus validate should be false.
     */
    @Transactional
    public void changeCategoryParent(Category object, Category newParent, boolean validate) {
        if (object == null) {
            // on null category has no meaning
            return;
        }
        
        // category has to have an owner
        if (object.getOwner()==null){
            throw new IllegalArgumentException("Category to change parent "
                    + "has empty owner - illegal state");
        }

        // verify if new parent is not from subtree of object
        // verify parent has same owner as this
        if (newParent != null) {
            List<Category> path2root = this.getPath2root(newParent);
            if (CategoryManager.isCategoryInCollection(object, path2root)) {
                throw new IllegalArgumentException(
                        "Cannot change parent to node from own subtree");
            }

            // parent owner test
            if (newParent.getOwner()==null){
                throw new IllegalArgumentException("Cannot change parent to "
                        + "category without owner");
            }
            
            // owner equals?
            if (object.getOwner().equals(newParent.getOwner())==false){
                throw new IllegalArgumentException("Cannot change parent to"
                        + " category with different owner");
            }
        } else if(validate) {
            // verify if current user has already some root category
            // if yes, this operation would cause inconsistency
            // get root for current owner and compare them
            Category currentRoot = this.getRootForUser(object.getOwner(), false);
            if (currentRoot.equals(object)==false){
                throw new IllegalArgumentException("User already has one root category"
                        + ", only one root category is allowed");
            }
        }

        // now uses simple hierarchy provided by JPA, thus is only needed to
        // change parent.
        object.setParent(newParent);
    }

    /**
     * Flatten hierarchy for tree rooted at root = dump all categories here to
     * collection. Default flatten strategy is inorder
     * 
     * @param root
     * @return
     */
    public List<Category> flattenHierarchy(Category root) {
        return this.flattenHierarchy(root, FLATTEN_STRATEGY_INORDER);
    }

    /**
     * Flatten hierarchy for tree rooted at root = dump all categories here to
     * collection.
     * 
     * @param root
     * @param flattenStrategy
     * @return
     */
    public List<Category> flattenHierarchy(Category root, int flattenStrategy) {
        LinkedList<Category> col = new LinkedList<Category>();

        // nullcheck
        if (root == null) {
            throw new NullPointerException(
                    "Cannot perform operation on null category");
        }

        // no children - return single (termination condition)
        if (root.emptyChildren()) {
            col.add(root);
            return col;
        }

        // recursive build flattening
        if (flattenStrategy == FLATTEN_STRATEGY_INORDER) {
            // at first add root element
            col.add(root);
            // now add recursively all children
            Iterator<Category> iterator = root.getChildren().iterator();
            while (iterator.hasNext()) {
                Category child = iterator.next();
                
                if (child==null){
                    // strange here, log it
                    continue;
                }

                // recursively compute flattening for this node
                List<Category> childFlattening = this.flattenHierarchy(child,
                        flattenStrategy);

                // append to list
                col.addAll(childFlattening);
            }
        } else {
            throw new IllegalArgumentException("Unknown traversal strategy");
        }
        return col;
    }
    
    /**
     * Flatten hierarchy for tree rooted at root = dump all categories here to
     * collection. Dump more useful info for building model (level, order, first, last).
     * 
     * @param root
     * @return
     */
    public List<CategoryFlattenWrapper> flattenHierarchyExtended(Category root){
        return this.flattenHierarchyExtended(root, 0, 0, 1);
    }
    
    /**
     * Flatten hierarchy for tree rooted at root = dump all categories here to
     * collection. Dump more useful info for building model (level, order, first, last).
     * 
     * @param root
     * @param level
     * @return
     */
    protected List<CategoryFlattenWrapper> flattenHierarchyExtended(Category root, int level, int curOrder, int count) {
        LinkedList<CategoryFlattenWrapper> col = new LinkedList<CategoryFlattenWrapper>();

        // nullcheck
        if (root == null) {
            throw new NullPointerException(
                    "Cannot perform operation on null category");
        }

        // no children - return single (termination condition)
        if (root.emptyChildren()) {
            col.add(new CategoryFlattenWrapper(root, level, curOrder, 0, curOrder==0, curOrder==(count-1)));
            return col;
        }

        // recursive build flattening
        int childrenCount = root.getChildren().size();
        // at first add root element
        col.add(new CategoryFlattenWrapper(root, level, curOrder, childrenCount, curOrder == 0, curOrder == (count-1)));
        // now add recursively all children
        Iterator<Category> iterator = root.getChildren().iterator();
        for(int i=0; iterator.hasNext(); i++) {
            Category child = iterator.next();
            if (child == null) {
                continue;
            }
            // recursively compute flattening for this node
            List<CategoryFlattenWrapper> childFlattening = 
                    this.flattenHierarchyExtended(child, level+1, i, childrenCount);

            // append to list
            col.addAll(childFlattening);
        }
        
        return col;
    }

    /**
     * Move (reassociate) all direct children categories to another given
     * category. After this operation passed category will have empty children
     * set. Usualy performed before category deletion when is needed to preserve
     * sub-categories
     * 
     * To keep category tree in consistent state it is not allowed to move all
     * subcategories to node within cat subtree.
     * 
     * Warning! All reassociated entities are persisted.
     * 
     * @param cat
     */
    @Transactional
    public void moveSubCategories(Category cat, Category newParent) {
        // null test
        if (cat == null || newParent == null) {
            return;
        }

        // if move to same category, ignore
        if (cat.equals(newParent)) {
            return;
        }

        // scenario -> what if I will want to move all subcategories to
        // category below cat tree.
        List<Category> path2root = this.getPath2root(newParent);
        if (path2root == null) {
            throw new RuntimeException("Path2root cannot be null");
        }

        // if is cat in path2root for newParent => newParent is in subtree of
        // category
        if (CategoryManager.isCategoryInCollection(cat, path2root)) {
            throw new IllegalArgumentException(
                    "Cannot move subcategories to another category "
                    + "in subtree. Would cause inconsistent state");
        }

        if (cat.getChildren() == null || cat.getChildren().isEmpty()) {
            // on undefined/empty children container has method no meaning
            return;
        }

        // copy first list
        List<Category> subcatsCopy = new ArrayList<Category>(cat.getChildren());

        Iterator<Category> iterator = subcatsCopy.iterator();
        while (iterator.hasNext()) {
            Category next = iterator.next();

            // Set parent should take care about reassociating bi-directional
            // assocciations.
            this.changeCategoryParent(next, newParent);
            this.saveCategory(next);
        }
    }

    /**
     * Deletes all sub-categories for given category. Change SHOULD propagate to
     * whole sub-tree rooted at [cat] (Cascade definition)
     * 
     * Warning! Changes are persisted!
     * 
     * Since cannot modify children list directly, do it with setting parent to
     * entities (to be removed) and then delete.
     * 
     * @param cat
     */
    @Transactional
    public void deleteAllSubcategories(Category cat) {
        if (cat == null) {
            throw new NullPointerException("Cannot delete subcategories on empty object");
        }

        // copy first list
        List<Category> subcatsCopy = new ArrayList<Category>(cat.getChildren());

        Iterator<Category> iterator = subcatsCopy.iterator();
        while (iterator.hasNext()) {
            Category node = iterator.next();

            this.changeCategoryParent(node, null, false);
            this.em.remove(node);
        }
    }

    /**
     * Return root category for user, if does not exists, new one is created
     * 
     * @param owner 
     * @return
     * 
     * @throws IllegalArgumentException if owner is null
     */
    public Category getRootForUser(User owner) {
        return this.getRootForUser(owner, true);
    }

    /**
     * Return root category for user, if does not exists, new one is created
     * 
     * @param owner 
     * @param createOnNull  if YES, default root category is created if does not exist
     * @return
     * 
     * @throws IllegalArgumentException if owner is null
     */
    public Category getRootForUser(User owner, boolean createOnNull) {
        // check user validity
        if (owner == null) {
            throw new IllegalArgumentException("Cannot return category for null user");
        }

        Category cat = null;

        // criteria builder
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object> criteriaQuery = cb.createQuery();
        Root<Category> from = criteriaQuery.from(Category.class);

        CriteriaQuery<Object> select = criteriaQuery.select(from);
        Predicate restrictions = cb.and(cb.equal(from.get("owner"), owner),
                cb.isNull(from.get("parent")));

        select.where(restrictions);

        TypedQuery<Object> typedQuery = em.createQuery(select);
        List resultList = typedQuery.getResultList();

        // Query query =
        // em.createQuery("SELECT c FROM Category c WHERE owner=:owr AND (parent IS NULL) ");
        // query.setParameter("owr", userid);
        // query.setMaxResults(1);

        // List resultList = query.getResultList();
        if (resultList.size() > 0) {
            cat = (Category) resultList.get(0);
        }
        // cat = (Category) query.getSingleResult();

        // create new one?
        if (cat == null) {
            if (createOnNull == false) {
                // if is not wanted to create new root, return just null
                return null;
            }

            try {
                cat = new Category();
                cat.setName("/");
                cat.setDescription("");
                cat.setParent(null);
                cat.setOwner(owner);
                cat.setPrivateCategory(false);
                cat.setId(null);

                this.saveCategory(cat);
            } catch (Exception e) {
                System.out.println("Persist exception: "
                        + e.getLocalizedMessage() + "; toString: "
                        + e.toString());
            }
        }
        return cat;
    }

    /**
     * Loads whole category tree for user. Lazy loaded objects
     * should be fully initialized and thus prepared for view tier.
     * 
     * @param userid
     * @return
     */
    public Category getCategoryTreeForUser(User owner) {
        Category cat = this.getRootForUser(owner);
        this.unLazyCategoryChildren(cat);
        return cat;
    }

    /**
     * Initializes lazy loaded category (analogy to Hibernate's
     * initialize) to have all required data loaded in view phase, when can be
     * entityManager closed and getting lazy associated data not fully loaded
     * could cause LazyInitializationException.
     * 
     * This is not required if is used OpenSessionInView filter
     * 
     * @param category
     */
    public void unLazyCategoryChildren(Category category) {
        if (category == null || category.getChildren() == null
                || category.emptyChildren()) {
            return;
        }

        Iterator<Category> iterator = category.getChildren().iterator();
        while (iterator.hasNext()) {
            Category node = iterator.next();

            this.unLazyCategoryChildren(node);
        }
    }

    /**
     * Finds category by its ID
     * 
     * @param id
     * @return
     */
    public Category findById(long id) {
        return em.find(Category.class, id);
    }
    
    /**
     * Load new db managed entity from category object by id
     * @param cat
     * @return 
     */
    public Category findById(Category cat) {
        if (cat==null){
            throw new NullPointerException("Cannot load by empty cat");
        }
        return this.findById(cat.getId());
    }
    
    /**
     * Load new db managed entity from categoryViewModel object by id
     * @param cat
     * @return 
     */
    public Category findById(CategoryViewModel cat) {
        if (cat==null){
            throw new NullPointerException("Cannot load by empty cat");
        }
        return this.findById(cat.getId());
    }

    /**
     * Returns all categories for given user in collection
     * 
     * @param userid
     * @return
     */
    public Collection<Category> findAll(User owner) {
        return em.createQuery("select c from Category c WHERE c.owner=:owner").setParameter("owner", owner).getResultList();
    }

    /**
     * Returns path to root for given category node as list. Passed category is
     * included in this list.
     * 
     * List is sorted in this way: 1. element is input element (cat) 2. element
     * is parent of cat last element is root node
     * 
     * @param cat
     * @return
     */
    public List<Category> getPath2root(Category cat) {
        List<Category> catList = new LinkedList<Category>();

        // current category for iterating to parent
        Category curCat = cat;
        while (curCat != null && curCat instanceof Category) {
            catList.add(curCat);
            curCat = curCat.getParent();
        }

        return catList;
    }

    /**
     * Helper methods which will tell whether is given category in collection
     * 
     * Warning! TODO: Compares category according to ID, not using equals method
     * (some entities could be lazy loaded, thus equals on lazy loaded entity,
     * but same, would return false)
     */
    public static boolean isCategoryInCollection(Category cat,
            Collection<Category> collection) {
        // nullpointer
        if (cat == null) {
            throw new NullPointerException("Category is null");
        }

        if (collection == null) {
            throw new NullPointerException("Collection is null");
        }

        Iterator<Category> iterator = collection.iterator();
        while (iterator.hasNext()) {
            Category next = iterator.next();
            if (next == null) {
                continue;
            }

            // id-based compare
            if (next.getId().equals(cat.getId())) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Removes category from collection
     * 
     * Warning! TODO: Compares category according to ID, not using equals method
     * (some entities could be lazy loaded, thus equals on lazy loaded entity,
     * but same, would return false)
     * 
     * TODO: write test for this
     * 
     * @param cat
     * @param collection
     * @return True if element was found and removed. False otherwise
     */
    public static boolean removeCategoryFromCollection(Category cat,
            Collection<Category> collection) {
        // nullpointer
        if (cat == null) {
            throw new NullPointerException("Category is null");
        }

        if (collection == null) {
            throw new NullPointerException("Collection is null");
        }

        boolean found = false;
        Iterator<Category> iterator = collection.iterator();
        while (iterator.hasNext()) {
            Category next = iterator.next();
            if (next == null) {
                continue;
            }

            // id-based compare
            if (next.getId().equals(cat.getId())) {
                iterator.remove();
                found = true;
                break;
            }
        }

        return found;
    }

    /**
     * Removes all private categories from collection, returns number of deleted
     * categories.
     * 
     * @param collection
     * @return
     */
    public static int removePrivateFromList(Collection<Category> collection) {
        // nullpointer
        if (collection == null) {
            throw new NullPointerException("Collection is null");
        }

        int deletedCount = 0;
        Iterator<Category> iterator = collection.iterator();
        while (iterator.hasNext()) {
            Category next = iterator.next();
            if (next == null) {
                continue;
            }

            // is private?
            if (next.getPrivateCategory()) {
                deletedCount += 1;
                iterator.remove();
            }
        }

        return deletedCount;
    }

    /**
     * Removes private subtree from path2root 
     * Assumed order of elements is the same as returned in getPath2root()
     * @param path2root
     * @return 
     */
    public static int removePrivateSubtreeFromPath(List<Category> path2root){
        // nullpointer
        if (path2root == null) {
            throw new NullPointerException("Collection is null");
        }

        // need to reverse, we traverse path from root
        // if is private element reached, remaining items are stripped out
        boolean stripping=false;
        int deleted = 0;
        Collections.reverse(path2root);
        
        Iterator<Category> iterator = path2root.iterator();
        while (iterator.hasNext()) {
            Category next = iterator.next();
            if (stripping){
                iterator.remove();
                deleted+=1;
                continue;
            }
            
            if (next == null) {
                continue;
            }

            // is private?
            if (next.getPrivateCategory()) {
                stripping=true;
                deleted+=1;
                iterator.remove();
                continue;
            }
        }
        
        Collections.reverse(path2root);
        return deleted;
    }
    
    /**
     * Returns true if collection contains at least one private category
     * 
     * @param collection
     * @return
     */
    public static boolean containsPrivateCategory(
            Collection<Category> collection) {
        // nullpointer
        if (collection == null) {
            throw new NullPointerException("Collection is null");
        }

        Iterator<Category> iterator = collection.iterator();
        while (iterator.hasNext()) {
            Category next = iterator.next();
            if (next == null) {
                continue;
            }

            if (next.getPrivateCategory()) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Rebuilds index, by default on all categories stored in db
     */
    public void rebuildIndex(){
        this.rebuildIndex(false, true);
    }

    /**
     * Rebuild fulltext index
     * @param  rebuildPerOne    rebuilds index manually by selecting each 
     *  entity independetly and passing to fulltext engine
     * @param blocking  if true current thread waits for finishing reindexing, 
     *  otherwise is spawned another async worker in different thread.
     */
    public void rebuildIndex(boolean rebuildPerOne, boolean blocking) {
        if (rebuildPerOne) {
            List resultList = this.em.createQuery("SELECT c FROM Category c").getResultList();
            this.ftsh.rebuildIndexOn(resultList);
        } else {
            this.ftsh.rebuildIndex(blocking);
        }
    }

    /**
     * Performs fulltext lucene search on categories and its text values
     * 
     * @param fulltextQuery
     * @param inName
     *            search in name?
     * @param inDescription
     *            search in description?
     * @return
     */
    public List<Category> fulltextSearch(final String fulltextQuery,
            boolean inName, boolean inDescription) {
        return this.fulltextSearch(fulltextQuery, inName, inDescription, null);
    }
    
    /**
     * Performs fulltext lucene search on categories and its text values
     * 
     * @param fulltextQuery
     * @param inName
     *            search in name?
     * @param inDescription
     *            search in description?
     * @param options for fulltext search (available to specify another criteria
     *  for searching (restrictions/filters, sorters, ...)
     * @return
     */
    public List<Category> fulltextSearch(final String fulltextQuery,
            boolean inName, boolean inDescription, FulltextSearchOptions options) {
        if (this.ftst == null) {
            // create instance on first fulltext search
            this.ftst = new FulltextSearchTemplate(em);
            this.ftst.setSearchEntity(Category.class);
            this.ftst.setSearchFields(new String[]{"name", "description"});
        }

        // change search fields according to setting
        if (inName && inDescription) {
            this.ftst.setSearchFields(new String[]{"name", "description"});
        } else if (inName) {
            this.ftst.setSearchFields(new String[]{"name"});
        } else if (inDescription) {
            this.ftst.setSearchFields(new String[]{"description"});
        } else {
            // nothing to search for
            return new LinkedList<Category>();
        }
        
        // process additional options
        if (options!=null){
            List<FulltextFilterEntity> filters = new LinkedList<FulltextFilterEntity>();
            
            // only for particular user 
            if (options.getUserToRestrictOn()!=null){
                filters.add(new FulltextFilterEntity("owner", true, "ownedBy_Id", options.getUserToRestrictOn().getId()));
            }

            // only public
            if (options.getOnlyPublic()!=null){
                filters.add(new FulltextFilterEntity("category", true, "privateCategory", Boolean.valueOf(!options.getOnlyPublic())));
            }
            
            // set query filters if not empty
            if (filters.isEmpty()==false){
                this.ftst.setQueryFilters(filters);
            }
        }
        
        return this.ftst.fulltextSearch(fulltextQuery);
    }

    /**
     * Performs fulltext lucene search on categories and its text values
     * 
     * @param fulltextQuery
     * @return
     */
    public List<Category> fulltextSearch(final String fulltextQuery) {
        return this.fulltextSearch(fulltextQuery, true, true);
    }

    public EntityManager getEm() {
        return em;
    }
    
    public JdbcTemplate getTemplate() {
        return template;
    }

    public FulltextSearchHelper getFtsh() {
        return ftsh;
    }

    public FulltextSearchTemplate getFtst() {
        return ftst;
    }
    
    /**
     * Returns string representation for path 2 root.
     * @param cat
     * @return 
     */
    public String generateTextualPath2root(Category cat){
        return this.generateTextualPath2root(this.getPath2root(cat));
    }
    
    /**
     * Returns string representation for path 2 root. From root to node.
     * 
     * @param cat List of nodes on path from node to root (order is the same as returns
     *  getPath2root() method.
     * @return 
     */
    public String generateTextualPath2root(List<Category> path2root){
        Collections.reverse(path2root);
        int pathSize = path2root.size();
        StringBuilder sb = new StringBuilder();
        Iterator<Category> iteratorCat = path2root.iterator();
        for(int j=0; iteratorCat.hasNext(); j++){
            Category curParent = iteratorCat.next();
            sb.append(curParent.getName());
            if (j<(pathSize-1)){
                sb.append(" > ");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Generates path2root for nodes to display in table
     * @param col 
     */
    public void generatePath2root(List<CategoryFlattenWrapper> col){
        if (col==null){
            throw new NullPointerException("Null collection passed");
        }
        
        Iterator<CategoryFlattenWrapper> iterator = col.iterator();
        for(int i=0; iterator.hasNext(); i++){
            CategoryFlattenWrapper curWrap = iterator.next();
            
            // has some category inside?
            if (curWrap.getCat()==null) continue;
            curWrap.setPath2root(this.generateTextualPath2root(curWrap.getCat()));
        }
    }
    
    /**
     * Converts CategoryFlattenWrapper List (used for online data processing)
     * to CategoryModelWrapper (used offline, minimizes memory used in serialization/posts).
     * 
     * Copies all data from wrapper, category is converted to CategoryViewModel
     * @param col 
     */
    public List<CategoryModelWrapper> convertToModelList(List<CategoryFlattenWrapper> col){
        if (col==null){
            throw new NullPointerException("Cannot convert empty list");
        }
        
        List<CategoryModelWrapper> toReturn = new LinkedList<CategoryModelWrapper>();
        Iterator<CategoryFlattenWrapper> iterator = col.iterator();
        while(iterator.hasNext()){
            CategoryFlattenWrapper flWrap = iterator.next();
            if (flWrap==null){
                log.error("Flatten wrapper is null in convertToModelList");
                continue;
            }
            
            CategoryViewModel catModel = new CategoryViewModel();
            catModel.initFrom(flWrap.getCat());
            
            CategoryModelWrapper mdlWrap = new CategoryModelWrapper(catModel);
            mdlWrap.copyFromCategoryWrapper(flWrap);
            
            toReturn.add(mdlWrap);
        }
        
        return toReturn;
    }
    
    /**
     * Generates selectItem list from categories for views. 
     * 
     * By default CategoryViewModel is added as object value to SelectItems.
     * @param flattenHierarchyExtended
     * @return 
     */
    public ArrayList<SelectItem> generateSelectItemFromCategories(
            List<CategoryFlattenWrapper> flattenHierarchyExtended){
        return this.generateSelectItemFromCategories(flattenHierarchyExtended, false);
    }
    
    /**
     * Generates selectItem list from categories for views
     * @param flattenHierarchyExtended
     * @param addWrapper if true adds wrapper as object to select item, otherwise 
     *  CategoryViewModel is added
     * @return 
     */
    public ArrayList<SelectItem> generateSelectItemFromCategories(
            List<CategoryFlattenWrapper> flattenHierarchyExtended, boolean addWrapper){
        if (flattenHierarchyExtended.isEmpty()){
            // hierarchy is null, nothing to do
            return new ArrayList<SelectItem>();
        }
        
        ArrayList<SelectItem> listSelectItem = new ArrayList<SelectItem>(flattenHierarchyExtended.size());
        Iterator<CategoryFlattenWrapper> iterator = flattenHierarchyExtended.iterator();
        
        // need to find maximal nesting level for category tree rendering
        int maxNestingLevel=-1;
        for(int i=0; iterator.hasNext(); i++){
            CategoryFlattenWrapper catWrapper = iterator.next();
            if (catWrapper.getLevel() > maxNestingLevel){
                maxNestingLevel = catWrapper.getLevel();
            }
        }
        
        // initialize structure for tree rendering
        ArrayList<Boolean> lastOpened = new ArrayList<Boolean>(maxNestingLevel);
        for(int i=0; i<=maxNestingLevel; i++){
            lastOpened.add(Boolean.valueOf(true));
        }
        
        // reset iterator
        iterator = flattenHierarchyExtended.iterator();
        for(int i=0; iterator.hasNext(); i++){
            CategoryFlattenWrapper catWrapper = iterator.next();
            // item label builder
            StringBuilder sb = new StringBuilder(
                    catWrapper.getLevel()*2 + catWrapper.getCat().getName().length() + 10);
            
            // strPrefix calculation based on map
            // is current level closing? Set proper value
            lastOpened.set(catWrapper.getLevel(), Boolean.valueOf(!catWrapper.isLast()));
            
            for(int j=0; j<catWrapper.getLevel(); j++){
                if (lastOpened.get(j)){
                    sb.append("|.");
                } else {
                    sb.append("..");
                }
            }
            
            sb.append(catWrapper.isLast() ? "└":"├").append("─");
            sb.append(catWrapper.getChildrenCount()>0 ? "┬":"─").append("\u25BA");
            sb.append(catWrapper.getCat().getName());
            
            CategoryViewModel catModel = new CategoryViewModel();
            catModel.initFrom(catWrapper.getCat());
            
            Object obj2selectItem = catModel;
            if (addWrapper){
                CategoryModelWrapper newCategoryModelWrapper = new CategoryModelWrapper(catModel);
                newCategoryModelWrapper.copyFromCategoryWrapper(catWrapper);
                
                obj2selectItem = newCategoryModelWrapper;
            }
            
            SelectItem selectItem = 
                    new SelectItem(obj2selectItem, 
                            sb.toString(), 
                            catWrapper.getCat().getDescription(), 
                            false, false, false);            
            listSelectItem.add(selectItem);
        }
            
        return listSelectItem;
    }
    
}
