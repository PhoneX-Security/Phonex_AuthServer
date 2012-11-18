/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.managers;

import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;

import cz.muni.fi.pa165.cards.helpers.CategoryTestDataProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.test.jdbc.SimpleJdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author ph4r05
 */
//@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="file:src/test/resources/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class CategoryManagerTest extends AbstractTransactionalJUnit4SpringContextTests {
    //private final ClassRelativeResourceLoader resourceLoader = new ClassRelativeResourceLoader(getClass());
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    
    @Autowired
    private CategoryManager categoryManager;
    
    @Autowired
    private UserManager userManager;
    
    private CategoryTestDataProvider testDataProvider;

    public void setCategoryManager(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }
    
    /**
     * Disables foreign key check and truncate table before transaction
     */
    @BeforeTransaction
    public void beforeTransaction() {
        //this.simpleJdbcTemplate.getJdbcOperations().execute("SET FOREIGN_KEY_CHECKS=0");
    }
    
    /**
     * Test constructor
     */
    public CategoryManagerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // initialize database populator
        
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // database cleanup
    }
    
    @Before
    public void setUp() {
        System.out.println("setup method");
        this.testDataProvider = new CategoryTestDataProvider();
        this.testDataProvider.setCategoryManager(categoryManager);
        this.testDataProvider.setUserManager(userManager);
        this.testDataProvider.setSimpleJdbcTemplate(simpleJdbcTemplate);
        // init database with sample data
    }
    
    @After
    public void tearDown() {
        System.out.println("teardown method");
        // reset database, cleanup
    }
    
    /**
     * Helps in testing - creates default users with given id
     * 
     * @param userid  ID of user to be created
     * @return new persisted entity
     */
    public User createDefaultUser(long userid, String name){
        return this.testDataProvider.createDefaultUser(userid, name);
    }
    
    public User getUser(long id){
        return this.userManager.getUserByID(id);
    }
    
    /**
     * Creates default category for test collection 
     * 
     * @param id
     * @param name
     * @param owner
     * @param privateCategory
     * @param parent
     * @param description
     * @return 
     */
    public Category createDefaultCategory(long id, String name, User owner, 
            short privateCategory, Category parent, String description){
        return this.testDataProvider.createDefaultCategory(id, name, owner, privateCategory, parent, description);
    }
    
    /**
     * Builds and saves initial test collection of categories
     */
    public ArrayList<User> initTestCollection(){
        return this.testDataProvider.initTestCollection();
    }

    @Test
    public void categoryManagerInjectionTest(){
        assertNotNull("Bean was not injected", categoryManager);
        assertTrue("EntityManager is not correctly injected", 
                this.categoryManager.getEm()!=null 
                && this.categoryManager.getEm() instanceof EntityManager);
    }
    
    @Test
    public void testCollectionTest(){
        ArrayList<User> users = initTestCollection();
        assertNotNull("Returned initialized test users are null", users);
        assertTrue("Size of users created should be 6, is: " + users.size(), users.size()==6); 
        
        // get 5th user
        User user5 = users.get(5);
        
        // db retrieval test
        User userTmp = this.getUser(user5.getId());
        assertEquals("Users should be equal", userTmp, user5);
        
        int rowCount = SimpleJdbcTestUtils.countRowsInTable(simpleJdbcTemplate, "Category");
        assertTrue("Number of rows in Category table after clean import doesn't match! Currently: " + rowCount + "; has to be 20!", rowCount==20);
        
        // test generated IDs for consistency with adding order
        Category tmpNode1 = this.categoryManager.findById(12);
        assertNotNull("Node with ID 12 should not be null", tmpNode1);
        assertEquals("Node with ID 12 does not match", "ROOT", tmpNode1.getName());
        
        // test generated IDs for consistency with adding order
        Category tmpNode2 = this.categoryManager.findById(20);
        assertNotNull("Node with ID 20 should not be null", tmpNode2);
        assertEquals("Node with ID 20 does not match", "ROOT|A|A|A|B", tmpNode2.getName());
        
        // there should be no more nodes (no node with id 21)
        assertNull("There should be no more nodes in DB, but node with id 21 was found", 
                this.categoryManager.findById(21));
    }
        
    @Test
    public void isCategoryInCollectionTest(){
        User tmpUser = new User();
        tmpUser.setIdC(1l);
        
        Category c1=new Category(Long.valueOf(1), "Root1", "Desc1", tmpUser);
        Category c2=new Category(Long.valueOf(2), "Root2", "Desc2", tmpUser);
        Category c3=new Category(Long.valueOf(3), "Root3", "Desc3", tmpUser);
        Category c4=new Category(Long.valueOf(4), "Root4", "Desc4", tmpUser);
        Category c5=new Category(Long.valueOf(5), "Root5", "Desc5", tmpUser);
        Category c6=new Category(Long.valueOf(6), "Root5", "Desc5", tmpUser);
        List<Category> col = new LinkedList<Category>();
        col.add(c1);
        col.add(c2);
        col.add(c3);
        col.add(c4);
        col.add(c5);
        
        // col{2,3} should be in collection
        assertTrue("category is in collection", CategoryManager.isCategoryInCollection(c1, col));
        assertTrue("category is in collection", CategoryManager.isCategoryInCollection(c3, col));
        assertTrue("category is in collection", CategoryManager.isCategoryInCollection(c5, col));
        assertTrue("category is in NOT collection", !CategoryManager.isCategoryInCollection(c6, col));   
    }
     
    @Test
    public void getRootForUserTest(){
        ArrayList<User> users = initTestCollection();
        
        // root for non-existent user
        User tmpUser4 = users.get(4);
        
        Category rootForUser4 = this.categoryManager.getRootForUser(tmpUser4);
        assertNotNull("Root for user never should be null.", rootForUser4);
        assertTrue("Root node should be instance of category", rootForUser4 instanceof Category);
        
        // try load again, now should have same id as before, since it loads already created
        Category rootForUser4a = this.categoryManager.getRootForUser(tmpUser4);
        assertNotNull("Root for user never should be null.", rootForUser4a);
        assertTrue("Root node should be instance of category", rootForUser4a instanceof Category);
        assertTrue("Root node for new user should be created only once."
                + "Second time it should return already existing node.", rootForUser4a.getId() == rootForUser4.getId());
        
        // load defined root for user
        User tmpUser3 = users.get(3);
        
        Category rootForUser3 = this.categoryManager.getRootForUser(tmpUser3);
        assertNotNull("Root for user never should be null.", rootForUser3);
        assertTrue("Root node should be instance of category", rootForUser3 instanceof Category);
        assertTrue("Name should be 'ROOT', but is: " + rootForUser3.getName(), "ROOT".equalsIgnoreCase(rootForUser3.getName()));
        assertTrue("Node should have exactly 4 children, now has: " + rootForUser3.getChildren().size(), rootForUser3.getChildren().size()==4);
        
        
        // load another root defined for user
        User tmpUser2 = users.get(2);
        
        Category rootForUser2 = this.categoryManager.getRootForUser(tmpUser2);
        assertNotNull("Root for user never should be null.", rootForUser2);
        assertTrue("Root node should be instance of category", rootForUser2 instanceof Category);
        assertTrue("Name should be 'ROOT_31', but is: " + rootForUser2.toString(), "ROOT_31".equalsIgnoreCase(rootForUser2.getName()));
        
        // children count 
        assertEquals("Root of 31 should have no child", 0, rootForUser2.getChildren().size());
        assertEquals("Root of 11 should have 4 children. Root: " + rootForUser3.toString(), 4, rootForUser3.getChildren().size());
        
        // get children with next children
        Category maxChild=null;
        Iterator<Category> iterator = rootForUser3.getChildren().iterator();
        for(int i=1, cn=rootForUser3.getChildren().size(); i<=cn && iterator.hasNext(); i++){
            Category current = iterator.next();
            if (maxChild==null || current.getChildren().size() > maxChild.getChildren().size()){
                maxChild = current;
            }
        }
        
        assertTrue("Name of element with maximum children should be 'ROOT|A', "
                + "but is: " + maxChild.toString() + "; size: " 
                + maxChild.getChildren().size(), "ROOT|A".equalsIgnoreCase(maxChild.getName()));
        assertEquals("Should have exactly 1 children here", 1, maxChild.getChildren().size());
        
        // try unlazy method now
        try {
            this.categoryManager.unLazyCategoryChildren(rootForUser3);
        } catch(Exception e){
            fail("Unlazy should not cause exception");
        }
    }
    
    @Test
    public void findByIdTest(){
        ArrayList<User> users = initTestCollection();
        
        final int nodeid=18;
        final int parentid=17;
        Category findById = this.categoryManager.findById(nodeid);
        
        // basic load check
        assertNotNull("Node with id="+nodeid+" should exist", findById);
        assertEquals("Static node load error", "ROOT|A|A|A", findById.getName());
        
        // parent check
        assertNotNull("Static node id="+nodeid+" should have parent", findById.getParent());
        assertTrue("Parent node id should be "+parentid, findById.getParent().getId() == parentid);
        assertEquals("Parent name does not match", "ROOT|A|A", findById.getParent().getName());
        assertNotNull("Parent's children list should not be null", findById.getParent().getChildren());
        assertFalse("Parent's children list should not be empty", findById.getParent().getChildren().isEmpty());
        
        // children list
        assertNotNull("Children collection should not be null", findById.getChildren());
        assertEquals("Node with id="+nodeid+" should have exactly 2 children", 2, findById.getChildren().size());
        // children test
        Iterator<Category> iterator = findById.getChildren().iterator();
        int goodCounter=0;
        while(iterator.hasNext()){
            Category next = iterator.next();
            if (next==null || !(next instanceof Category)){
                continue;
            }
            
            goodCounter+=1;
        }
        assertTrue("Some property of children does not hold", goodCounter==findById.getChildren().size());
        
        // only random test on presence
        Category node111 = this.categoryManager.findById(15);
        assertNotNull("Node should exists", node111);
        
        Category node102 = this.categoryManager.findById(12);
        assertNotNull("Node should exists", node102);
        
        // try to load non-existent node
        final int nonexid=99999;
        Category nonex=null;
        try {
            nonex = this.categoryManager.findById(nonexid);
        } catch(Exception e){
            
        }
        assertNull("Object with id="+nonexid+" should not exist", nonex);
    }
    
    @Test
    public void getPath2rootTest(){
        ArrayList<User> users = initTestCollection();
        
        // get deepest node in tree
        final int nodeid=20;
        Category node = this.categoryManager.findById(nodeid);
        assertNotNull("Node should exist", node);
        
        List<Category> path2root = this.categoryManager.getPath2root(node);
        // non null collection
        assertNotNull("Collection returned should not be null", path2root);
        // test size of collection
        assertEquals("path to root does not match", 5, path2root.size());
        // test exactly the path to root for given node. It should contain all nodes with specified ids
        Iterator<Category> iterator = path2root.iterator();
        int goodCounter=0;
        for(int i=0; iterator.hasNext(); i++){
            Category next = iterator.next();
            if (next==null || !(next instanceof Category)) {
                continue;
            }
            
            // check also order of nodes in path, should be from current node to root
            if ( (next.getId().intValue()==20 && i==0)
                    || (next.getId().intValue()==18 && i==1)
                    || (next.getId().intValue()==17 && i==2)
                    || (next.getId().intValue()==13 && i==3)
                    || (next.getId().intValue()==12 && i==4)){
                goodCounter+=1;
            }
        }
        
        assertEquals("Count of good objects in path2root "
                + "should be exactly the size of collection, in correct order"
                + "(from current node to root)", goodCounter, path2root.size());
    }
    
    @Test
    public void deleteAllSubcategoriesTest(){
        ArrayList<User> users = initTestCollection();
        
        // get root
        User tmpUser3 = users.get(3);
        final int node104id = 13;
        final int node108id = 17;
        final int node111id = 20;
        
        Category rootForUser3 = this.categoryManager.getRootForUser(tmpUser3);
        assertNotNull("Root node should not be null", rootForUser3);
        
        Category node104 = this.categoryManager.findById(node104id);
        assertNotNull("Node with id " + node104id + " should exists", node104);
        
        // current number of children
        int numOfRootChildren = rootForUser3.getChildren().size();
        
        // delete all subcategories
        this.categoryManager.deleteAllSubcategories(node104);
        // this change need to be persisted now
        this.categoryManager.saveCategory(node104);
        
        // number of children
        assertEquals("Number of children should be zero", 0, node104.getChildren().size());
        
        // root should have same number of children
        assertEquals("Number of direct children should decrement", numOfRootChildren, rootForUser3.getChildren().size());
        
        // some category should not exists
        Category nodeNull=null;
        try {
            nodeNull = this.categoryManager.findById(node108id);
        } catch(Exception e){
            
        }
        assertNull("node " + node108id + " should not exist now", nodeNull);
        
        // test node 111 - deleted by cascading
        nodeNull=null;
        try {
            nodeNull = this.categoryManager.findById(node111id);
        } catch(Exception e){
            
        }
        assertNull("node " + node111id + " should not exist now (on the end of delete chain)", nodeNull);
    }
    
    @Test
    public void changeCategoryParentTest(){
        ArrayList<User> users = initTestCollection();
        
        // get root
        User tmpUser3 = users.get(3);
        final int nodeAid = 18;
        final int nodeBid = 20;
        final int nodeRootBid = 14;
        final int nodeRootForeignId=11;
        
        Category rootForUser3 = this.categoryManager.getRootForUser(tmpUser3);
        assertNotNull("Root node should not be null", rootForUser3);
        
        Category nodeA = this.categoryManager.findById(nodeAid);
        assertNotNull("Node with id " + nodeAid + " should exists", nodeA);
        
        Category nodeAparent = nodeA.getParent();
        assertNotNull("Perent node of 109 should exists", nodeAparent);
        
        // current number of children
        int numOfRootChildren = rootForUser3.getChildren().size();
        int numOfOldParentChildren = nodeAparent.getChildren().size();
        
        // Test if is possible to change parent as node from its subtree
        // this test should have fail, thus it is needed to perform it first
        Category nodeB = this.categoryManager.findById(nodeBid);
        
        boolean goodExceptionThrown=false;
        try{
            this.categoryManager.changeCategoryParent(nodeAparent, nodeB);
        } catch(IllegalArgumentException arg){
            goodExceptionThrown=true;
        } catch(Exception e){
            
        }
        
        assertTrue("Exception should be thrown, cannot change category parent"
                + " to node from its subtree (prevent cycle)", goodExceptionThrown);
        
        // change parent
        this.categoryManager.changeCategoryParent(nodeA, rootForUser3);
        this.categoryManager.saveCategory(nodeA);
        
        // detect change in children count
        assertEquals("Children number should be increased, but is not", numOfRootChildren+1, rootForUser3.getChildren().size());
        assertNotNull("New parent should not be null", nodeA.getParent());
        assertTrue("New parent of " + nodeAid + " node should be root, but is not", nodeA.getParent().getId()==rootForUser3.getId());
        assertEquals("Children number of old parent should be decreased, but is not", numOfOldParentChildren-1, nodeAparent.getChildren().size());
        
        // try to re-load and check if is change persisted        
        Category newRoot = this.categoryManager.getRootForUser(tmpUser3);
        assertNotNull("newRoot node should not be null", newRoot);
        assertEquals("Children number of root should persist", numOfRootChildren+1, newRoot.getChildren().size());
        
        // some validation test - set another null category to persist for user having root category
        Category newFalseRoot = this.categoryManager.findById(nodeRootBid);
        assertNotNull("Node " + nodeRootBid + " node should not be null", newFalseRoot);
        try {
            this.categoryManager.changeCategoryParent(newFalseRoot, null);
            fail("Tried to change category to root for user already having "
                    + "root category and no exception was thrown, validation error");
        } catch(Exception e){
            //
        }
        
        // change parent to different user category
        Category nodeRootForeign = this.categoryManager.findById(nodeRootForeignId);
        assertNotNull("Got null when tried to load node id " + nodeRootForeignId, nodeRootForeign);
        try {
            this.categoryManager.changeCategoryParent(newFalseRoot, nodeRootForeign);
            fail("Tried to change parent to different user category tree and no"
                    + " exception was thrown, validation error");
        } catch(Exception e){
            //
        }
    }
    
    @Test
    public void findAllTest(){
        ArrayList<User> users = initTestCollection();
        
        User tmpUser2 = users.get(2);
        User tmpUser3 = users.get(3);   
        User tmpUser5 = users.get(5);
        
        Collection<Category> allcat3 = this.categoryManager.findAll(tmpUser3);
        assertNotNull("Collection of all categories for user should not be null", allcat3);
        assertEquals("Number of categories for user 11 does not match", 9, allcat3.size());
        
        Category rootForUser = this.categoryManager.getRootForUser(tmpUser3);
        boolean rootInCollection = CategoryManager.isCategoryInCollection(rootForUser, allcat3);
        assertTrue("Root for user should be among all categories for that user", rootInCollection);
        
        // load for another user
        Collection<Category> allcat2 = this.categoryManager.findAll(tmpUser2);
        assertNotNull("Collection of all categories for user should not be null", allcat2);
        assertEquals("Number of categories for user 31 does not match", 1, allcat2.size());
        
        // load for non-existent user
        Collection<Category> allcat5 = this.categoryManager.findAll(tmpUser5);
        assertNotNull("Collection of all categories for user should not be null", allcat5);
        assertEquals("Number of categories for non-existent user does not match", 0, allcat5.size());
    }
    
    @Test
    public void flattenHierarchyTest(){
        ArrayList<User> users = initTestCollection();
        
        final int nodeBid = 20;
        final int nodeRoot11id = 11;
        
        // load flattening for root node
        try {
            User tmpUser1 = users.get(3);
        
            Category rootnode = this.categoryManager.getRootForUser(tmpUser1);
            List<Category> flattenHierarchy = this.categoryManager.flattenHierarchy(rootnode);
            
            assertNotNull("Flattened hierarchy should not be null", flattenHierarchy);
            assertEquals("There should be exact number of elements here", 9, flattenHierarchy.size());
            assertTrue("In flattened hierarchy should be root itself", CategoryManager.isCategoryInCollection(rootnode, flattenHierarchy));
            
            // nulltest on elements
            Iterator<Category> iterator = flattenHierarchy.iterator();
            while(iterator.hasNext()){
                Category node = iterator.next();
                // node null test
                assertNotNull("None object in flattenedHierarchy list can be null", node);
                
                // test whether root node is in path2root of this node, should be
                List<Category> path2root = this.categoryManager.getPath2root(node);
                assertTrue("root node should be contained in path2root from node with id: " + node.getId(), CategoryManager.isCategoryInCollection(rootnode, path2root));
            }
            
            // test presence of particular node
            Category nodeB = this.categoryManager.findById(nodeBid);
            assertTrue("Node " + nodeBid + " should be in flattened hierarchy", CategoryManager.isCategoryInCollection(nodeB, flattenHierarchy));
            
            // test presence of node, which should not be here
            Category nodeRoot11 = this.categoryManager.findById(nodeRoot11id);
            assertFalse("Node " + nodeRoot11id + " should NOT be in flattened hierarchy", CategoryManager.isCategoryInCollection(nodeRoot11, flattenHierarchy));
            
        } catch(Exception e){
            // TODO Refactor
            e.printStackTrace(System.err);
            fail("This code should not throw an exception");
        }
    }
    
    @Test
    public void moveSubCategoriesTest1(){
        initTestCollection();
        
        final int nodeAid = 17;
        final int nodeBid = 19;
        
        Category nodeToWorkOn = this.categoryManager.findById(nodeAid);
        
        // load category to try to reassoc (from subtree of nodeToWorkOn - consistency test)
        Category bogusDestinationNode = this.categoryManager.findById(nodeBid);
        try {
            this.categoryManager.moveSubCategories(nodeToWorkOn, bogusDestinationNode);
            fail("Exception should be thrown when call violates consistency (tried to move subcategories to own subtree)");
        } catch (IllegalArgumentException iae){
            // OK, here should go execution flow
        } catch (Exception e) {
            // TODO refactor
            e.printStackTrace(System.err);
            fail("This kind of exception should not occurr here");
        }
    }
    
    @Test
    public void moveSubCategoriesTest2(){
        initTestCollection();
        
        final int nodeRootAAid = 17;
        final int nodeRootAid = 13;
        final int nodeRootAAABid = 20;
        Category nodeToWorkOn = this.categoryManager.findById(nodeRootAAid);
        
        // now try valid request
        Category realDestinationNode = this.categoryManager.findById(nodeRootAid);
        Category realDestinationNode4 = this.categoryManager.findById(nodeRootAid);
        try {
            this.categoryManager.moveSubCategories(nodeToWorkOn, realDestinationNode);
            // this change need to be persisted now
            this.categoryManager.saveCategory(nodeToWorkOn);
            this.categoryManager.saveCategory(realDestinationNode);
            
            Category realDestinationNode5 = this.categoryManager.findById(nodeRootAid);
            
            // examine result
            // nodeToWorkOn should have empty children list
            assertTrue("nodeToWorkOn should have empty children list after moving categories", nodeToWorkOn.getChildren().isEmpty());
            
//            // direct child of nodeToWorkOn should have different parent here
//            Category sampleCategory = this.categoryManager.findById(109);
//            assertNotNull("direct child of nodeToWorkOn should NOT have empty parent.", sampleCategory.getParent());
//            assertTrue("direct child of nodeToWorkOn should have different parent", sampleCategory.getParent().getId()==realDestinationNode.getId());
//            
            // check path2root change for lowest child
            Category lowestNode = this.categoryManager.findById(nodeRootAAABid);
            List<Category> path2root = this.categoryManager.getPath2root(lowestNode);
            assertFalse("Path2root of " + nodeRootAAABid + " should NOT contain old parent", CategoryManager.isCategoryInCollection(nodeToWorkOn, path2root));
            assertTrue("Path2root of " + nodeRootAAABid + " should contain new parent", CategoryManager.isCategoryInCollection(realDestinationNode, path2root));
//            
            // TODO
            // check that another Categories was not changed by this operation.
        } catch (Exception e){
            // TODO refactor
            e.printStackTrace(System.err);
            fail("This kind of exception should not occurr here");
        }
    }   

    /**
     * Test of saveCategory method, of class CategoryManager.
     */
    @Test
    public void testSaveCategory() {
        System.out.println("saveCategory");
        ArrayList<User> users = initTestCollection();
        
        final int nextFreeId=21;
        
        User tmpUser = users.get(5);
        Category tmpCat = new Category(null, "Test category", "Description", tmpUser);
        this.categoryManager.saveCategory(tmpCat);
        
        // load same category, id should be 21
        Category newCategory = this.categoryManager.findById(nextFreeId);
        assertNotNull("New inserted category should exists with id: " + nextFreeId, newCategory);
        assertEquals("New inserted category should equals to original", tmpCat, newCategory);
        
        // add new parent, test child association
        Category tmpCat2 = new Category(null, "Test category", "Description", tmpUser);
        tmpCat2.setParent(tmpCat);
        this.categoryManager.saveCategory(tmpCat2);
        
        // load same category, id should be 21
        Category newCategory2 = this.categoryManager.findById(nextFreeId+1);
        assertNotNull("New inserted category should exists with id: " + (nextFreeId+1), newCategory2);
        assertEquals("New inserted category should equals to original", tmpCat2, newCategory2);
        
        // size of children should be 1
        assertEquals("Size of children list should be 1", 1, newCategory.getChildren().size());
        
        // insert null
        try {
            this.categoryManager.saveCategory(null);
            fail("Tried to insert null category and no exception was thrown");
        } catch(Exception e){
            //
        }
    }

    /**
     * Test of deleteCategory method, of class CategoryManager.
     */
    @Test
    public void testDeleteCategory() {
        System.out.println("deleteCategory");
        initTestCollection();
        
        final int nodeRootAid = 13;
        final int nodeRootAAid = 17;
        final int nodeRootAAAid = 18;
        final int nodeRootAAAAid = 19;
        final int nodeRootAAABid = 20;
        
        // load category that should definitely exists (according to previous tests passed)
        Category nodeLast = this.categoryManager.findById(nodeRootAAid);
        assertNotNull("Node with id " + nodeRootAAid + " should exists", nodeLast);
        
        Category nodeLastParent = nodeLast.getParent();
        assertNotNull("Node with id " + nodeRootAAid + " should have parent", nodeLastParent);
        int numOfChildren = nodeLastParent.getChildren().size();
        
        // delete selected category
        this.categoryManager.deleteCategory(nodeLast);
        
        // check if category with id 20 exists
        Category nodeLastAgain = this.categoryManager.findById(nodeRootAAid);
        assertNull("Deleted category should not exists anymore", nodeLastAgain);
        
        // check for properly deleted subcategories
        assertNull("Deleted category should not exists anymore", 
                this.categoryManager.findById(nodeRootAAAid));
        assertNull("Deleted category should not exists anymore", 
                this.categoryManager.findById(nodeRootAAAAid));
        assertNull("Deleted category should not exists anymore", 
                this.categoryManager.findById(nodeRootAAABid));
        
        // check for parent update - what happened to children?
        assertEquals("Number of children should decrease by 1", numOfChildren-1, nodeLastParent.getChildren().size());
        
        // try to delete null category
        try {
            this.categoryManager.deleteCategory(null);
            fail("Passed null category to delete, no exception was thrown");
        } catch(Exception e) {
            //
        }
    }
    
    @Test
    public void canSaveCategoryTest(){
        System.out.println("canSaveCategory");
        ArrayList<User> users = initTestCollection();
        int canAddResult = 0;
        
        // can create category without owner?
        Category invalidCategory = new Category(null, "Test", null, null);
        canAddResult = this.categoryManager.canSaveCategory(invalidCategory);
        assertTrue("Method should deny saving category without owner", canAddResult!=1);
        
        // can create category root for user already having root category?
        invalidCategory = new Category(null, "Test", null, users.get(2));
        canAddResult = this.categoryManager.canSaveCategory(invalidCategory);
        assertTrue("Method should deny saving category as root for user already "
                + "having root category", canAddResult!=1);
        
        // can create category root for user not having root category?
        invalidCategory = new Category(null, "Test", null, users.get(5));
        canAddResult = this.categoryManager.canSaveCategory(invalidCategory);
        assertTrue("Method should permit saving category as root for user not "
                + "having root category", canAddResult==1);
        
        // parent manipulation
        invalidCategory = this.categoryManager.findById(19);
        invalidCategory.setParent(null);
        canAddResult = this.categoryManager.canSaveCategory(invalidCategory);
        assertTrue("Method should deny saving category without father for user already"
                + " having root category", canAddResult!=1);
    }
    
//
//    /**
//     * Test of moveSubCategories2parent method, of class CategoryManager.
//     */
//    @Test
//    public void testMoveSubCategories2parent() {
//        System.out.println("moveSubCategories2parent");
//        Category cat = null;
//        CategoryManager instance = new CategoryManager();
//        instance.moveSubCategories2parent(cat);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//
//    /**
//     * Test of getCategoryTreeForUser method, of class CategoryManager.
//     */
//    @Test
//    public void testGetCategoryTreeForUser() {
//        System.out.println("getCategoryTreeForUser");
//        int userid = 0;
//        CategoryManager instance = new CategoryManager();
//        Category expResult = null;
//        Category result = instance.getCategoryTreeForUser(userid);
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
//    /**
//     * Test of unLazyCategoryChildren method, of class CategoryManager.
//     */
//    @Test
//    public void testUnLazyCategoryChildren() {
//        System.out.println("unLazyCategoryChildren");
//        Category category = null;
//        CategoryManager instance = new CategoryManager();
//        instance.unLazyCategoryChildren(category);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }
//
}
