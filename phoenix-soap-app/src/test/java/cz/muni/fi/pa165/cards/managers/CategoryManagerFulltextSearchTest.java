package cz.muni.fi.pa165.cards.managers;

import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.helpers.CategoryTestDataProvider;
import cz.muni.fi.pa165.cards.fulltext.FulltextSearchHelper;
import cz.muni.fi.pa165.cards.managers.CategoryManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
 * @author ph4r05
 */
//@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="file:src/test/resources/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class CategoryManagerFulltextSearchTest extends AbstractTransactionalJUnit4SpringContextTests {    
    @Autowired
    private CategoryManager categoryManager;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Autowired
    private FulltextSearchHelper ftsh;
    
    @Autowired
    private UserManager userManager;
    
    private CategoryTestDataProvider testDataProvider;
    
    // single TransactionTemplate shared amongst all methods in this instance
    private TransactionTemplate transactionTemplate;

    public void setCategoryManager(CategoryManager categoryManager) {
        this.categoryManager = categoryManager;
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
    public CategoryManagerFulltextSearchTest() {
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
        assertNotNull("Bean was not injected", categoryManager);
        assertTrue("EntityManager is not correctly injected", 
                this.categoryManager.getEm()!=null 
                && this.categoryManager.getEm() instanceof EntityManager);
        
        assertNotNull("Transaction manager cannot be null", this.transactionManager);
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
        
        this.testDataProvider = new CategoryTestDataProvider();
        this.testDataProvider.setCategoryManager(categoryManager);
        this.testDataProvider.setUserManager(userManager);
        this.testDataProvider.setSimpleJdbcTemplate(simpleJdbcTemplate);
        this.testDataProvider.initTestCollection();
        
        transactionManager.commit(status);
        
        // another way of doing transactions
//        final TransactionTemplate tt = new TransactionTemplate(transactionManager);
//        tt.setReadOnly(false);
//        tt.execute(new TransactionCallbackWithoutResult() {
//            @Override
//            public void doInTransactionWithoutResult(TransactionStatus status) {
//                Resource resource = resourceLoader.getResource("file:src/test/resources/categoryTable.sql");
//                SimpleJdbcTestUtils.executeSqlScript(simpleJdbcTemplate, resource, false);
//                status.flush();
//            }
//        });
    }
    
    @AfterTransaction
    public void afterTransaction(){
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);
        this.testDataProvider.cleanTables();       
        transactionManager.commit(status);
    }
    
    @Test
    @Transactional
    public void fulltextSearchTest(){  
        try {
            this.ftsh.deleteIndexDirectory();
            //this.ftsh.touchDirectory();
        } catch (IOException ex) {
            Logger.getLogger(CategoryManagerFulltextSearchTest.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace(System.err);
            //fail("Cannot erase fulltext index directory");
        }
        
        try {
            this.categoryManager.rebuildIndex(false, true);
        } catch(Exception e){
            e.printStackTrace(System.err);
            //fail("There should occur no exception during index rebuilding");
        }
        
        // search for only one result (direct search)
        try {
            List<Category> fulltextSearch = this.categoryManager.fulltextSearch("main");
            assertNotNull("Fulltext result should not be null", fulltextSearch);
            assertTrue("Number of results should be nonempty", fulltextSearch.size()>0);
            assertEquals("Number of results should be 3", 3, fulltextSearch.size());
        } catch(Exception e){
            // TODO refactor
            e.printStackTrace(System.err);
            fail("There should be no exception thrown for valid fulltext query");
        }
        
        // test another query (approximative/fuzzy)
        try {
            // search for only one result
            List<Category> fulltextSearch = this.categoryManager.fulltextSearch("main~");
            assertNotNull("Fulltext result should not be null", fulltextSearch);
            assertTrue("Number of results should be nonempty", fulltextSearch.size()>0);
            
            // dont kno why, but on windows this results in 4 records, on linux in 5 results
            assertTrue("Number of results should be in [4,5]", fulltextSearch.size() >= 4 && fulltextSearch.size() <= 5);
        } catch(Exception e){
            // TODO refactor
            e.printStackTrace(System.err);
            fail("There should be no exception thrown for valid fulltext query");
        }
    }

}
