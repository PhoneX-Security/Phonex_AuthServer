/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.helpers;

import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.managers.CategoryManager;
import cz.muni.fi.pa165.cards.managers.UserManager;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.jdbc.SimpleJdbcTestUtils;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;

/**
 * Generates and stores to DB sample data used in Category tests
 * @author ph4r05
 */
public class CategoryTestDataProvider {
   
    final private ResourceLoader resourceLoader = new DefaultResourceLoader();
    
    @Autowired
    private CategoryManager categoryManager;
    
    @Autowired
    private UserManager userManager;
    
    @Autowired
    private SimpleJdbcTemplate simpleJdbcTemplate;
    
    private ArrayList<User> userCollection; 
    private ArrayList<Category> categoryCollection;
    
    public void setCategoryManager(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    public void setSimpleJdbcTemplate(SimpleJdbcTemplate simpleJdbcTemplate) {
        this.simpleJdbcTemplate = simpleJdbcTemplate;
    }

    public ArrayList<Category> getCategoryCollection() {
        return categoryCollection;
    }

    public ArrayList<User> getUserCollection() {
        return userCollection;
    }
    
    /**
     * Helps in testing - creates default users with given id
     * 
     * @param userid  ID of user to be created
     * @return new persisted entity
     */
    public User createDefaultUser(long userid, String name){
        User user = new User();
        user.setLoginC(name);
        this.userManager.addUser(user);
        return user;
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
        
        Category tmpCat = new Category(null, name, description, owner);
        
        // need to be called especially
        tmpCat.setParent(parent);
                
        this.categoryManager.saveCategory(tmpCat);

        return tmpCat;
    }
    
    public void cleanTables(){
        Resource resource = resourceLoader.getResource("file:src/test/resources/cleanTables.sql");
        SimpleJdbcTestUtils.executeSqlScript(simpleJdbcTemplate, resource, false);
    }
    
    /**
     * Builds and saves initial test collection of categories
     */
    public ArrayList<User> initTestCollection(){
        this.cleanTables();
        
        this.categoryCollection = new ArrayList<Category>(20);
        this.userCollection = new ArrayList<User>(6);
        User user1 = this.createDefaultUser(1l, "John"); //1
        User user2 = this.createDefaultUser(3l, "Annie"); //2
        User user3 = this.createDefaultUser(31l, "Eamy"); //3
        User user4 = this.createDefaultUser(11l, "Porscha"); //4
        User user5 = this.createDefaultUser(123l, "Emily"); //5
        User user6 = this.createDefaultUser(9999l, "Emma"); //6
        this.userCollection.add(user1);
        this.userCollection.add(user2);
        this.userCollection.add(user3);
        this.userCollection.add(user4);
        this.userCollection.add(user5);
        this.userCollection.add(user6);
        
        Category cat1Root = this.createDefaultCategory(1l, "MaiYY", user1, (short) 0, null, "");
        Category cat2 = this.createDefaultCategory(2l, "MaUU ma in", user1, (short) 0, cat1Root, "");
        Category cat3 = this.createDefaultCategory(3l, "MaQQ Main", user1, (short) 0, cat1Root, "");
        Category cat4 = this.createDefaultCategory(4l, "MaTT mmiann", user1, (short) 0, cat1Root, "");
        Category cat5 = this.createDefaultCategory(5l, "MaHH Maim", user1, (short) 0, cat1Root, "");
        Category cat6 = this.createDefaultCategory(6l, "MaHH Máin", user1, (short) 0, cat2, "");
        Category cat7 = this.createDefaultCategory(7l, "MaHH M.a.i.n", user1, (short) 0, cat6, "");
        Category cat8 = this.createDefaultCategory(8l, "MaHH M\"ai\"n", user1, (short) 0, cat7, "");
        Category cat9 = this.createDefaultCategory(9l, "MaGG Mains", user1, (short) 0, cat7, "");
        Category cat10 = this.createDefaultCategory(10l, "Main category from bundle", user2, (short) 0, null, "");
        
        // 11
        Category cat11 = this.createDefaultCategory(100l, "ROOT_31", user3, (short) 0, null, "");
        Category cat12 = this.createDefaultCategory(102l, "ROOT", user4, (short) 0, null, "");
        Category cat13 = this.createDefaultCategory(104l, "ROOT|A", user4, (short) 0, cat12, "");
        Category cat14 = this.createDefaultCategory(105l, "ROOT|B", user4, (short) 0, cat12, "");
        Category cat15 = this.createDefaultCategory(106l, "ROOT|C", user4, (short) 0, cat12, "");
        Category cat16 = this.createDefaultCategory(107l, "ROOT|D", user4, (short) 0, cat12, "");
        Category cat17 = this.createDefaultCategory(108l, "ROOT|A|A", user4, (short) 0, cat13, "");
        Category cat18 = this.createDefaultCategory(109l, "ROOT|A|A|A", user4, (short) 0, cat17, "");
        Category cat19 = this.createDefaultCategory(110l, "ROOT|A|A|A|A", user4, (short) 0, cat18, "");
        Category cat20 = this.createDefaultCategory(111l, "ROOT|A|A|A|B", user4, (short) 0, cat18, "");
        
        this.categoryCollection.add(cat1Root);
        this.categoryCollection.add(cat2);
        this.categoryCollection.add(cat3);
        this.categoryCollection.add(cat4);
        this.categoryCollection.add(cat5);
        this.categoryCollection.add(cat6);
        this.categoryCollection.add(cat7);
        this.categoryCollection.add(cat8);
        this.categoryCollection.add(cat9);
        this.categoryCollection.add(cat10);
        this.categoryCollection.add(cat11);
        this.categoryCollection.add(cat12);
        this.categoryCollection.add(cat13);
        this.categoryCollection.add(cat14);
        this.categoryCollection.add(cat15);
        this.categoryCollection.add(cat16);
        this.categoryCollection.add(cat17);
        this.categoryCollection.add(cat18);
        this.categoryCollection.add(cat19);
        this.categoryCollection.add(cat20);
        return this.userCollection;
    }
}
