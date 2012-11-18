package cz.muni.fi.pa165.cards.managers;

/*
 * author miro
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Category;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.helpers.CategoryTestDataProvider;
import cz.muni.fi.pa165.cards.managers.CardManager;
import cz.muni.fi.pa165.cards.managers.CategoryManager;

/**
 *
 * @author Miro
 */

@ContextConfiguration(locations = "file:src/test/resources/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class CardManagerTest extends AbstractTransactionalJUnit4SpringContextTests {
    //private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    
    
    
    @Autowired
    private CardManager cardManager;    
    @Autowired 
    private CategoryManager categoryManager;
    @Autowired
    private UserManager userManager;
//    @Autowired
//    private CardManager cardManager;    
//    @Autowired 
//    private CategoryManager categoryManager;
//    @Autowired
//    private UserManager userManager;
////    @Autowired
////    private SimpleJdbcTemplate simpleJdbcTemplate;
//
//    public void setUserManager(UserManager userManager) {
//        this.userManager = userManager;
//    }
    
    
    
    private CategoryTestDataProvider testDataProvider;
    
    public CardManagerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
        this.testDataProvider = new CategoryTestDataProvider();
        this.testDataProvider.setCategoryManager(categoryManager);
        this.testDataProvider.setUserManager(userManager);
        
        
//        this.testDataProvider.setSimpleJdbcTemplate(simpleJdbcTemplate);
    }
    
    
    @After
    public void tearDown() {
    }
    
    
    private List<Card> getTestCollection(){        
        //first parameter shouldnt matter
        User u1 = this.testDataProvider.createDefaultUser(1l, "Miro");
        User u2 = this.testDataProvider.createDefaultUser(2l, "Patrik");
        
                //this.testDataProvider.createDefaultUser(21l, "Miro");                        
        //User user2 = this.testDataProvider.createDefaultUser(31l, "Patrik");                
        
                
        Card c1 = new Card();                
        c1.setTelephone("0902 335 490");
        c1.setEmail("mail1@gmail.com");
        c1.setOwner(u1);
        
        Card c2 = new Card();        
        //c2.addToCategory(cat2);
        c2.setTelephone("0911 666 10");
        c2.setEmail("mail2@gmail.com");
        c2.setOwner(u2);
        
        Card c3 = new Card();        
        //c3.addToCategory(cat2);
        c3.setTelephone("2331 12 977");
        c3.setEmail("mail3@gmail.com");
        c3.setOwner(u2);
        
        Card c4 = new Card();                
        c3.setTelephone("+421 221 122");
        c3.setEmail("mail4@gmail.com");
        c4.setOwner(u2);
        
        List<Card> list= new ArrayList<Card>();
        list.add(c1);
        list.add(c2);
        list.add(c3);
        list.add(c4);
        return list;
    }
    
    @Test
    public void testCardManagerInjection() {
        assertNotNull("Bean was not injected", cardManager);
        assertTrue("Entity Manager was not injected successfully to CardManager!",cardManager.getEm() != null &&  
                cardManager.getEm() instanceof EntityManager);
        
        assertNotNull("Bean was not injected", categoryManager);
        assertTrue("Entity Manager was not injected successfully to CategoryManager!",categoryManager.getEm() != null &&  
                categoryManager.getEm() instanceof EntityManager);
    }
    
    @Test
    public void testAddCard(){
        Card c = new Card();
        c.setAddress("Lucna");
        c.setCompany("Apple");
        c.setSecondName("Svitok");
        c.setFirstName("Miro");        
        
      


        String tn = "+490 122 233";
        
        c.addProperty("telephone", tn);
        
        cardManager.addCard(c);
        
        
        Card retCard = cardManager.getCardById(c.getId());
        assertNotNull("Card is null",retCard);
      
        
        assertEquals("Returned card and added card are not the same.",c, retCard);
        assertTrue("Card address is not the same", "Lucna".equals(retCard.getAddress()));
        assertTrue("Property was not added",tn.equals(retCard.getProperty("telephone"))); 
        
        
        List<Card> testCards = getTestCollection();        
                
        for (Card card : testCards){
            cardManager.addCard(card);            
        } 
        for (Card card : testCards){
            Card returnedCard = cardManager.getCardById(card.getId());
            assertNotNull(returnedCard.getOwner());  
            assertEquals("Returned card and added card are not the same.",card, returnedCard);            
        }
        
        
        
    }
    
    @Test 
    public void testDeleteCard(){        
        List<Card> testCards = getTestCollection();                        
        for (Card card : testCards){
            cardManager.addCard(card);            
        }         
        for (Card card : testCards){            
                cardManager.deleteCard(card);
		assertNull("The card should be deleted!",
                                cardManager.getCardById(card.getId()));
        } 
    }   
   
    
    @Test
    public void testUpdateCard(){    
         List<Card> testCards = getTestCollection();        
                
        for (Card card : testCards){
            cardManager.addCard(card);            
        } 
        for (Card card : testCards){
            Card returnedCard = cardManager.getCardById(card.getId());
            assertEquals("Returned card and added card are not the same.",card, returnedCard);            
        }
        String newMail = "changed@gmail.com";
        User user1 = new User();
        user1.setLoginC("John");
        userManager.addUser(user1);
        
        Card c1=  testCards.get(0);
        assertNotNull(c1);
        c1.setEmail(newMail);
        c1.setOwner(user1);
        Card c2=  testCards.get(1);
        assertNotNull(c2);
        c2.setEmail(newMail);
        c2.setOwner(user1);
        Card c3=  testCards.get(2);
        assertNotNull(c3);
        c3.setEmail(newMail);
        c3.setOwner(user1);
        
        Short pbl = 0;        
        Short prv = 0;        
        
        Category cat1=  new Category();
        cat1.setName("cat1");
        cat1.setDescription("desc1");
        cat1.setOwner(user1);
        
        //assertTrue("Category cannot be saved",categoryManager.canSaveCategory(cat1)==0); 
        categoryManager.saveCategory(cat1);
        
        Category cat2=  new Category();
        cat2.setName("cat2");
        cat2.setDescription("desc2");
        cat2.setOwner(user1);
        categoryManager.saveCategory(cat2);
        
               
        Set<Category> categs=new HashSet<Category>();
        categs.add(cat1);
        categs.add(cat2);               
        
        //includes calling of updateCard
        cardManager.addCategory(cat1, c1); 
        cardManager.addCategory(cat2, c2);
        cardManager.addCategories(categs, c3);
        
                
        Card retrievedCard1 = cardManager.getCardById(c1.getId());
        Card retrievedCard2 = cardManager.getCardById(c2.getId());
        Card retrievedCard3 = cardManager.getCardById(c3.getId());        

        
        assertNotNull("Card should have categories",retrievedCard1.getCategories());
        assertTrue("Owner does not have proper login",user1.getLogin().equals(retrievedCard1.getOwner().getLogin()));
        
    }
    
    @Test
    public void testEditCategories(){    
         List<Card> testCards = getTestCollection();        
                
        for (Card card : testCards){
            cardManager.addCard(card);            
        } 
        Card c1=  testCards.get(0);        
        Card c2=  testCards.get(1);        
        Card c3=  testCards.get(2);
        
        assertTrue(c1.getOwner().getLogin().equals("Miro")); 
        assertTrue(c2.getOwner().getLogin().equals("Patrik")); 
        //c1.getOwner()
        
        Category cat1=new Category(null, "Root1", "Desc1", c1.getOwner());
        Category cat2=new Category(null, "Root2", "Desc2", c1.getOwner());        
        
        cardManager.addCategory(cat1, c1.getId());
        cardManager.addCategory(cat2, c2.getId());
        cardManager.addCategory(cat1, c3.getId());
        cardManager.addCategory(cat2, c3.getId());       
        
        Card retrievedCard1 = cardManager.getCardById(c1.getId());
        Card retrievedCard2 = cardManager.getCardById(c2.getId());
        Card retrievedCard3 = cardManager.getCardById(c3.getId());        
        
        assertTrue("Number of categories is wrong " + retrievedCard1.getCategories().size(),retrievedCard1.getCategories().size()==1);
        assertTrue("Number of categories is wrong " + retrievedCard2.getCategories().size(),retrievedCard2.getCategories().size()==1);
        assertTrue("Number of categories is wrong " + retrievedCard3.getCategories().size(),retrievedCard3.getCategories().size()==1);
        
        cardManager.deleteAllCategories(retrievedCard1.getId());
        
        assertNull(retrievedCard1.getCategories());
        assertTrue("Number of categories is wrong",retrievedCard3.getCategories().size()==1);
    }
    
    @Test
    public void testAddExistingCategories(){   
         List<Card> testCards = getTestCollection();        
                
        for (Card card : testCards){
            cardManager.addCard(card);            
        } 
        
        Card c1=  testCards.get(0);        
        Card c2=  testCards.get(1);        
        Card c3=  testCards.get(2);
    
        Category cat1=new Category(null, "Root1", "Desc1", c2.getOwner());
        Category cat2=new Category(null, "Root2", "Desc2", c2.getOwner()); 
        categoryManager.saveCategory(cat1);
        categoryManager.saveCategory(cat2);
        
        
        
        cardManager.addCategory(cat1, c1.getId());
        assertNotNull("no categories",c1.getCategories());
        
        cardManager.addCategory(cat2, c2.getId());        
        assertNotNull("no categories",c2.getCategories());
        
        cardManager.addCategory(cat1, c3);        
        cardManager.addCategory(cat2, c3.getId());        
        assertTrue("Card does not contain all categories",c3.getCategories().size()==2);
                
    }
}
