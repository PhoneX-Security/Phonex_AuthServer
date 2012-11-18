package cz.muni.fi.pa165.cards.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

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
import cz.muni.fi.pa165.cards.db.User;

@ContextConfiguration(locations = "file:src/test/resources/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class UserManagerTest extends
		AbstractTransactionalJUnit4SpringContextTests {

	@Autowired
	private UserManager userManager;

	@Autowired
	private CardManager cardManager;

	private static Card TEST_CARD = new Card();

	public void setCategoryManager(UserManager userManager) {
		this.userManager = userManager;
	}

	/**
	 * Test constructor
	 */
	public UserManagerTest() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
		// fill database with testing data
		TEST_CARD.setAddress("Kosovska 14");
		TEST_CARD.setCompany("Kamil and his dogs");
		TEST_CARD.setEmail("kamildogs@gmail.com");
		TEST_CARD.setFirstName("Kamil");
		TEST_CARD.setSecondName("Peteraj");
		TEST_CARD.setDescription("song writer and also dog breeder");

	}

	@AfterClass
	public static void tearDownClass() throws Exception {
		// database cleanup
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
		System.out.println("deleting all rows from tables");
		// reset database, cleanup
		deleteFromTables();
	}

	public void deleteFromTables() {

		TEST_CARD.setId(null);

		deleteFromTables("User", "Card", "Category");
	}

	@Test
	public void testInjectionOfUserManager() {

		assertNotNull("User Manager was not injected successfully", userManager);
		assertNotNull(
				"Entity Manager was not injected successfully to UserManager!",
				userManager.getEm());
	}

	@Test
	public void testAddUser() {

		User userToAdd = new User();

		addUserAndCheck(userToAdd);

		User userToAdd2 = new User();
		userToAdd2.setEmailC("foo").setFirstNameC("jurko");

		addUserAndCheck(userToAdd2);

	}

	private void addUserAndCheck(User user) {

		userManager.addUser(user);

		// the id is automatically set when the entity is persisted
		User userGot = userManager.getUserByID(user.getId());
		assertNotNull("User should be added", userGot);

		assertEquals("User should be added correctly!", user, userGot);
	}

	@Test
	public void testDeleteUser() {

		User userToAdd = new User();
		userManager.addUser(userToAdd);

		deleteUserAndCheck(userToAdd);

		User userToAdd2 = new User();
		userToAdd2.setEmailC("mrkvicka@dd.xa").setFirstNameC("Jozko")
				.setSurNameC("Mrkvicka");
		userManager.addUser(userToAdd2);

		deleteUserAndCheck(userToAdd2);
	}

	private void deleteUserAndCheck(User user) {

		boolean result = userManager.deleteUserById(user.getId());
		assertTrue("The user: " + user
				+ " should be deleted. False return value of deleted method",
				result);

		assertNull("The user should be deleted!",
				userManager.getUserByID(user.getId()));
	}

	@Test
	public void testUpdateUser() {

		User addedUser = userManager.addUser("gooseka", "foo", "Juraj",
				"Huska", "foo@muni.cz", new Date(), "222.44.33.322", new Date(
						System.currentTimeMillis()), 2);

		String newFirstName = "Changed";
		addedUser.setFirstNameC(newFirstName);

		userManager.updateUser(addedUser);

		User retrievedUser = userManager.getUserByID(addedUser.getId());

		assertEquals(newFirstName, retrievedUser.getFirstName());
	}

	//@Test
	public void testAddCard() {

		User addedUser = userManager.addUser("gooseka", "foo", "Juraj",
				"Huska", "foo@muni.cz", new Date(), "222.44.33.322", new Date(
						System.currentTimeMillis()), 2);

		userManager.addCard(TEST_CARD, addedUser.getId());

		User retrievedUser = userManager.getUserByID(addedUser.getId());

		List<Card> cards = retrievedUser.getCards();

		assertTrue("Retrieved cards should contain added card!",
				cards.contains(TEST_CARD));
		assertEquals("Card should have correct owner!",
				cards.get(0).getOwner(), retrievedUser);
	}

	//@Test
	public void testDeleteCard() {

		User addedUser = userManager.addUser("gooseka2", "foo2", "Juraj2",
				"Huska2", "foo@muni.cz", new Date(), "222.44.33.322", new Date(
						System.currentTimeMillis()), 2);

		userManager.addCard(TEST_CARD, addedUser.getId());

		userManager.deleteCard(TEST_CARD, addedUser.getId());

		User retrievedUser = userManager.getUserByID(addedUser.getId());

		assertTrue("User should have no cards",
				retrievedUser.getCards().size() == 0);

		assertNull("The cards should be deleted from database!",
				cardManager.getCard(TEST_CARD));
	}

}
