package cz.muni.fi.pa165.cards.testData;

import java.io.File;

import javax.persistence.EntityManagerFactory;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Image;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.managers.CardManager;
import cz.muni.fi.pa165.cards.managers.ImageManager;
import cz.muni.fi.pa165.cards.managers.UserManager;

@ContextConfiguration(locations = "file:src/test/resources/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class FillDatabaseWithTestData extends
		AbstractTransactionalJUnit4SpringContextTests {

	@Autowired
	protected transient CardManager cardManager;

	@Autowired
	protected transient ImageManager imageManager;

	@Autowired
	protected transient UserManager userManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private void initCards() {

		Card card = new Card();
		card.setFirstName("Juraj");
		card.setSecondName("Huska");
		card.setTelephone("733213444");
		card.setAddress("Sebedrazie 22/345");
		card.setPublicVisibility(true);
		File front = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka.jpg").getFile());
		File back = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka2.jpg").getFile());
		imageManager.addImage(front, back, card);

		Card card2 = new Card();
		card2.setFirstName("Miroslav");
		card2.setSecondName("Svitok");
		card2.setTelephone("72223444");
		card2.setAddress("Sebedrazie 101011010011");
		card2.setPublicVisibility(true);
		File front2 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka2.jpg").getFile());
		File back2 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka3.jpg").getFile());
		imageManager.addImage(front2, back2, card2);

		Card card3 = new Card();
		card3.setFirstName("Dusan");
		card3.setSecondName("Klinec");
		card3.setTelephone("22333344");
		card3.setDescription("Programming and stuff");
		card3.setPublicVisibility(true);
		File front3 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka3.jpg").getFile());
		File back3 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka4.jpg").getFile());
		imageManager.addImage(front3, back3, card3);

		Card card4 = new Card();
		card4.setFirstName("Oliver");
		card4.setSecondName("Kiss");
		card4.setTelephone("22333344");
		card4.setEmail("domov@gmail.com");
		card4.setPublicVisibility(true);
		File front4 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka4.jpg").getFile());
		File back4 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka5.jpg").getFile());
		imageManager.addImage(front4, back4, card4);

		Card card5 = new Card();
		card5.setFirstName("Veronika");
		card5.setSecondName("Belickova");
		card5.setTelephone("22333344");
		card5.setDescription("Teacher of Biology");
		card5.setPublicVisibility(true);
		File front5 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka5.jpg").getFile());
		File back5 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka6.jpg").getFile());
		imageManager.addImage(front5, back5, card5);

		Card card6 = new Card();
		card6.setFirstName("Pavol");
		card6.setSecondName("Sojcik");
		card6.setTelephone("22333344");
		card6.setPublicVisibility(true);
		File front6 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka6.jpg").getFile());
		File back6 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka7.jpg").getFile());
		imageManager.addImage(front6, back6, card6);

		Card card7 = new Card();
		card7.setFirstName("Michal");
		card7.setSecondName("Kurbel");
		card7.setTelephone("22333344");
		card7.setPublicVisibility(true);
		File front7 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka7.jpg").getFile());
		File back7 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka8.jpg").getFile());
		imageManager.addImage(front7, back7, card7);

		Card card8 = new Card();
		card8.setFirstName("Mato");
		card8.setSecondName("Chudo");
		card8.setTelephone("22333344");
		card8.setPublicVisibility(true);
		File front8 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka8.jpg").getFile());
		File back8 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka9.jpg").getFile());
		imageManager.addImage(front8, back8, card8);

		Card card9 = new Card();
		card9.setFirstName("Jan");
		card9.setSecondName("Gabela");
		card9.setTelephone("22333344");
		card9.setPublicVisibility(true);
		File front9 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka9.jpg").getFile());
		File back9 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka10.jpg").getFile());
		imageManager.addImage(front9, back9, card9);

		Card card10 = new Card();
		card10.setFirstName("Pavol");
		card10.setSecondName("Šuster");
		card10.setTelephone("22333344");
		card10.setPublicVisibility(true);
		File front10 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka10.jpg").getFile());
		File back10 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka9.jpg").getFile());
		imageManager.addImage(front10, back10, card10);
		
		Card card11 = new Card();
		card11.setFirstName("Milan Rastislav");
		card11.setSecondName("Štefanik");
		card11.setTelephone("22333344");
		card11.setPublicVisibility(false);
		File front11 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka4.jpg").getFile());
		File back11 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka2.jpg").getFile());
		imageManager.addImage(front11, back11, card11);
		
		Card card12 = new Card();
		card12.setFirstName("Ludovid Stur");
		card12.setSecondName("Šuster");
		card12.setTelephone("22333344");
		card12.setPublicVisibility(false);
		File front12 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka5.jpg").getFile());
		File back12 = new File(this.getClass().getClassLoader()
				.getResource("testCards/vizitka2.jpg").getFile());
		imageManager.addImage(front12, back12, card12);
		
		User juro = new User();
		juro.setFirstNameC("Juro");
		juro.setSurNameC("Huska");
		juro.setLoginC("gooseka");
		juro.setPasswordC("foo");
		juro.setAuthority("ROLE_USER");
		userManager.addUser(juro);
		
		Long juroId = juro.getId();
		userManager.addCard(card, juroId);
		userManager.addCard(card2, juroId);
		userManager.addCard(card3, juroId);
		userManager.addCard(card4, juroId);
		userManager.addCard(card5, juroId);
		
		User rytmusovaManka = new User();
		rytmusovaManka.setFirstNameC("Manka");
		rytmusovaManka.setSurNameC("Rytmusova");
		rytmusovaManka.setLoginC("mulanostylo");
		rytmusovaManka.setPasswordC("ego");
		rytmusovaManka.setAuthority("ROLE_USER");
		userManager.addUser(rytmusovaManka);
		
		Long mankaId = rytmusovaManka.getId();
		userManager.addCard(card6, mankaId);
		userManager.addCard(card7, mankaId);
		userManager.addCard(card8, mankaId);
		userManager.addCard(card9, mankaId);
		userManager.addCard(card10, mankaId);
		userManager.addCard(card11, mankaId);
		userManager.addCard(card12, mankaId);
	}

	@Test
	public void fillInDatabase() {

		initCards();
	}

	public CardManager getCardManager() {
		return cardManager;
	}

	public void setCardManager(CardManager cardManager) {
		this.cardManager = cardManager;
	}

	public ImageManager getImageManager() {
		return imageManager;
	}

	public void setImageManager(ImageManager imageManager) {
		this.imageManager = imageManager;
	}

	public UserManager getUserManager() {
		return userManager;
	}

	public void setUserManager(UserManager userManager) {
		this.userManager = userManager;
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	public void setEntityManagerFactory(
			EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}
}
