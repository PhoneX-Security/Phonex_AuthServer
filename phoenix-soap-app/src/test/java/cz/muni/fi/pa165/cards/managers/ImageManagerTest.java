package cz.muni.fi.pa165.cards.managers;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Image;
import cz.muni.fi.pa165.cards.db.ImageVariant;
import cz.muni.fi.pa165.cards.db.User;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

@ContextConfiguration(locations = "/applicationContext.xml")
@TransactionConfiguration
@Transactional
public class ImageManagerTest extends
        AbstractTransactionalJUnit4SpringContextTests {

    @Autowired
    ImageManager imageManager;

    @Autowired
    CardManager cardManager;

    @PersistenceContext
    EntityManager em;
    
    private static Map<ImageVariant, Image> imageMap = new EnumMap<ImageVariant, Image>(ImageVariant.class);
    private static long cardId;

    /**
     * Test constructor
     */
    public ImageManagerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // fill database with testing data

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
    }

    @Test
    public void dependencyInjectionTest() {
        Assert.assertNotNull("imageManager is null", imageManager);
        Assert.assertNotNull("cardManager is null", cardManager);
        Assert.assertNotNull("em is null", em);
    }

    @Test
    public void imageCRUDTest() throws IOException, URISyntaxException {
        Card card = new Card();
        cardManager.addCard(card);
        cardId = card.getId();

        byte[] front = IOUtils.toByteArray(
                new FileInputStream(new File(this.getClass().getClassLoader().getResource("card_f.jpg").getFile())));
        byte[] back = IOUtils.toByteArray(
                new FileInputStream(new File(this.getClass().getClassLoader().getResource("card_b.jpg").getFile())));

        Map<ImageVariant, Image> returnedImages = imageManager.addImages(front, back, card);
        em.flush();

        for (ImageVariant variant : ImageVariant.values()) {
            Image returned = returnedImages.get(variant);
            Assert.assertNotNull("Image variant " + variant + " was not created", returned);
            Assert.assertNotNull("Image variant " + variant + " was not persisted", returned.getId());
            Assert.assertEquals(
                    "Card for image variant " + variant + " was not set properly",
                    returned.getCard(), card);

            Assert.assertNotNull(
                    "Picture data for image variant " + variant + " were not created",
                    returned.getPicture());
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(returned.getPicture()));
            int expectedWidth;
            if (variant.getWidth() != 0) {
                expectedWidth = variant.getWidth();
            } else {
                int frontWidth = ImageIO.read(new ByteArrayInputStream(front)).getWidth();
                int backWidth = ImageIO.read(new ByteArrayInputStream(back)).getWidth();
                expectedWidth = variant.isFront() ? frontWidth : backWidth;
            }
            Assert.assertEquals(
                    "Image variant " + variant + " was not resized properly", expectedWidth, bufferedImage.getWidth());
        }
        imageMap.putAll(returnedImages);

        imageManager.deleteImages(cardManager.getCardById(cardId));
        List<Image> allImages = em.createQuery("select i from Image i", Image.class).getResultList();
        Assert.assertTrue("Images were not deleted", allImages.isEmpty());
    }

}
