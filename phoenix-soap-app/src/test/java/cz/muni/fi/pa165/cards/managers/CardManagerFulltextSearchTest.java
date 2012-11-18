package cz.muni.fi.pa165.cards.managers;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.helpers.CategoryTestDataProvider;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchHelper;
import cz.muni.fi.pa165.cards.managers.CategoryManager;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.test.jdbc.SimpleJdbcTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests fulltext search.
 * Warning! This dirties database. This should be performed only on empty test database
 * with no production data!
 * 
 * To perform fulltext search index need to be built at first. To build index, 
 * data is needed to be persisted in database, not in transaction running reindexing.
 * 
 * @author Miro, original idea by pharos
 */
//@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="file:src/test/resources/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class CardManagerFulltextSearchTest extends AbstractTransactionalJUnit4SpringContextTests {    
    final private ResourceLoader resourceLoader = new DefaultResourceLoader();
    @Autowired
    private CategoryManager categoryManager;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Autowired
    private FulltextSearchHelper ftsh;
    
    @Autowired
    private UserManager userManager;
    
    @Autowired
    private CardManager cardManager;
    
    private CategoryTestDataProvider testDataProvider;
    
    // single TransactionTemplate shared amongst all methods in this instance
    private TransactionTemplate transactionTemplate;

    public void setCategoryManager(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
    }

    public void setCardManager(CardManager cardManager) {
        this.cardManager = cardManager;
    }

    
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setFtsh(FulltextSearchHelper ftsh) {
        this.ftsh = ftsh;
    }

    public void setTestDataProvider(CategoryTestDataProvider testDataProvider) {
        this.testDataProvider = testDataProvider;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    /**
     * Test constructor
     */
    public CardManagerFulltextSearchTest() {
    }
    
    @Before
    public void setUp() {
        System.out.println("setup method");
        // init database with sample data
    }
    
    @After
    public void tearDown() {
        System.out.println("teardown method");
        // reset database, cleanup
        
    }

    @Test
    public void injectionTest(){
        assertNotNull("Bean was not injected", cardManager);
        assertTrue("EntityManager is not correctly injected", 
                this.cardManager.getEm()!=null 
                && this.cardManager.getEm() instanceof EntityManager);
        
        assertNotNull("Transaction manager cannot be null", this.cardManager);
    }
    
    /**
     * Inserts data to database before transaction.
     * To index it properly is needed to store data to database before 
     * reindex/fulltext search transaction is started.
     * 
     * Need to insert data in single transaction (disable foreign key check in transaction)
     */
    @BeforeTransaction
    public void beforeTransaction() {        
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);
        
        //this.testDataProvider = new CategoryTestDataProvider();
        
        
//        this.testDataProvider.setCategoryManager(categoryManager);
//        this.testDataProvider.setUserManager(userManager);
//        this.testDataProvider.setSimpleJdbcTemplate(simpleJdbcTemplate);
//        this.testDataProvider.initTestCollection();
        initTestCollection();
        transactionManager.commit(status);
    }
    
    private Card createDefaultCard(String address,String description, String firstName, String secondName, String email, User owner){
        Card c = new Card();
        c.setAddress(address);
        c.setDescription(description);
        c.setOwner(owner);
        c.setFirstName(firstName);
        c.setSecondName(secondName);
        c.setEmail(email);
        this.cardManager.addCard(c);
        
        return c;
    }
    
    private void initTestCollection(){   
        cleanTestData();
//        this.testDataProvider = new CategoryTestDataProvider();
//        User user1 = testDataProvider.createDefaultUser(1l, "Miro");
//        User user2 = testDataProvider.createDefaultUser(2l, "Jan");
//        User user3 = testDataProvider.createDefaultUser(3l, "Peter");
        
        User user1= new User();
        user1.setLoginC("Miro");
        userManager.addUser(user1);
                
                
        String address = "Lucna 173";
        String description = "Obycajna karta";
        String email = "email@gmail.com";
        
        Card card1 = createDefaultCard(address , description, "Juraj", "Húska", email, null);
        Card card2 = createDefaultCard("Rôzna adresa" , "Iný popis", "Miroslav", "Svítok", "Čudná pošta", null);
        Card card3 = createDefaultCard(address , "pec nam spadla", "Dušan", "Klinec", email, null);
        Card card4 = createDefaultCard(address , description, "Oliver", "Kišš", email, null);
        
        user1.addCard(card1);
        user1.addCard(card2);
        user1.addCard(card3);
        user1.addCard(card4);        
        userManager.updateUser(user1);
        
        //assertNotNull(card1.getOwner());
    }
    
    
    private void cleanTestData(){
        Resource resource = resourceLoader.getResource("file:src/test/resources/cleanTables.sql");
        SimpleJdbcTestUtils.executeSqlScript(simpleJdbcTemplate, resource, false);
    }
    
    @AfterTransaction
    public void afterTransaction(){
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);
        //this.testDataProvider.cleanTables();       
        this.cleanTestData();
        transactionManager.commit(status);
    }
    
    @Test
    @Transactional
    public void fulltextSearchTest(){  
        try {
            this.ftsh.deleteIndexDirectory();
        } catch (IOException ex) {
            Logger.getLogger(CardManagerFulltextSearchTest.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace(System.err);
            fail("Cannot erase fulltext index directory");
        }
        
        try {
            //this.cardManager.rebuildIndex(false);
            this.cardManager.rebuildIndex();
        } catch(Exception e){
            e.printStackTrace(System.err);
            fail("There should occur no exception during index rebuilding");
        }
        
         //search for only one result (direct search)
        try {
            List<Card> fulltextSearch = this.cardManager.fulltextSearch("Miroslav"); //this.categoryManager.fulltextSearch("main");
            assertNotNull("Fulltext result should not be null", fulltextSearch);
            assertTrue("Number of results should be nonempty", fulltextSearch.size()>0);
            assertEquals("Number of results should be 1", 1, fulltextSearch.size());
        } catch(Exception e){            
            e.printStackTrace(System.err);
            fail("There should be no exception thrown for valid fulltext query");
        }
        //search on attributes
        try {
            Set<String> attributes = new HashSet<String>();
            attributes.add("email");            
            
            List<Card> fulltextSearch = this.cardManager.fulltextSearch("karta"); //this.categoryManager.fulltextSearch("main");
            assertNotNull("Fulltext result should not be null", fulltextSearch);
            assertTrue("Number of results should be nonempty", fulltextSearch.size()>0);              
            assertTrue("Number of results should be 2 or 3 according to system", fulltextSearch.size() >= 2 && fulltextSearch.size() <= 3);            
            
            
        } catch(Exception e){            
            e.printStackTrace(System.err);
            fail("There should be no exception thrown for valid fulltext query");
        }
        
        // test another query (approximative/fuzzy)
//        try {
//            // search for only one result
//            List<Category> fulltextSearch = this.categoryManager.fulltextSearch("main~");
//            assertNotNull("Fulltext result should not be null", fulltextSearch);
//           assertTrue("Number of results should be nonempty", fulltextSearch.size()>0);
//            
//            // dont kno why, but on windows this results in 4 records, on linux in 5 results
//            assertTrue("Number of results should be in [4,5]", fulltextSearch.size() >= 4 && fulltextSearch.size() <= 5);
//        } catch(Exception e){
//            // TODO refactor
//            e.printStackTrace(System.err);
//            fail("There should be no exception thrown for valid fulltext query");
//        }
    }

}
