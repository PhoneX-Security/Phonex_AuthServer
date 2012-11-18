package cz.muni.fi.pa165.cards.managers;

import cz.muni.fi.pa165.cards.db.Card;
import cz.muni.fi.pa165.cards.db.Image;
import cz.muni.fi.pa165.cards.db.ImageVariant;
import org.apache.commons.io.IOUtils;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA. Image: bleble Date: 22.11.2011 Time: 12:25
 */
@Repository
public class ImageManager {
	@PersistenceContext
	private EntityManager em;

	@Autowired
	private EntityManagerFactory entityManagerFactory;
    
    @Autowired
    private CardManager cardManager;

	public EntityManager getEm() {
		return em;
	}

	public void setEm(EntityManager em) {
		this.em = em;
	}

	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}

    @Transactional
	public Map<ImageVariant, Image> addImages(byte[] front, byte[] back, Card card) {
		if (front == null && back == null) {
			throw new IllegalArgumentException("At least one image data array must be present.");
		}
		Map<ImageVariant, Image> imageMap = card.getImages();
        if (imageMap == null) {
            imageMap = new EnumMap<ImageVariant, Image>(ImageVariant.class);
            card.setImages(imageMap);
        }
		Image[] images = new Image[ImageVariant.values().length];
		for (int i = 0; i < images.length; i++) {
            ImageVariant currentVariant = ImageVariant.values()[i];
            Image currentImage = images[i];
            if (front == null && currentVariant.isFront()) {
                continue;
            }
            if (back == null && !currentVariant.isFront()) {
                continue;
            }
            currentImage = new Image();
			currentImage.setCard(card);
            currentImage.setImageVariant(currentVariant);
            currentImage.setPicture(currentVariant.isFront() ? front : back);
			if (currentVariant.getWidth() != 0) {
				try {
					resizeImage(currentImage, currentVariant.getWidth());
				} catch (IOException e) {
					throw new RuntimeException("Could not resize image.");
				}
			}
            if (imageMap.get(currentVariant) != null) {
                deleteImage(imageMap.get(currentVariant));
            }
            addImage(currentImage);
            imageMap.put(currentVariant, currentImage);
		}
        cardManager.updateCard(card);
        em.flush();
		return imageMap;
	}

    public Map<ImageVariant, Image> addImage(File front, File back, Card card) {
        byte[] frontArray = null;
        byte[] backArray = null;
        try {
            frontArray = IOUtils.toByteArray(new FileInputStream(front));
            backArray = IOUtils.toByteArray(new FileInputStream(front));
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return addImages(frontArray, backArray, card);
    }

    @Deprecated
    public Map<ImageVariant, Image> addImage(File front, File back, Image image) {
        return addImage(front, back, image.getCard());
    }

    public void deleteImages(Card card) {
        deleteImages(card, null);
    }

    @Transactional
    public void deleteImages(Card card, Boolean front) {
        for (Map.Entry<ImageVariant, Image> entry : card.getImages().entrySet()) {
            if (front == null || front == entry.getKey().isFront()) {
                Image image = entry.getValue();
                image.setCard(null);
                card.getImages().remove(entry.getKey());
                em.remove(image);
            }
        }
    }

    @Transactional
	private boolean addImage(Image image) {
		if (image == null) {
			throw new IllegalArgumentException("image is null");
		}
		em.persist(image);
		return true;
	}

    @Transactional(readOnly = true)
	public Image getImage(Long id) {
		return em.find(Image.class, id);
	}

    @Transactional
	private boolean deleteImage(Image image) {
        image = getImage(image.getId());
		if (image != null) {
            image.setCard(null);
			em.remove(image);
			return true;
		} else {
			return false;
		}
	}

	private void resizeImage(Image image, int width) throws IOException {
		InputStream input = new ByteArrayInputStream(image.getPicture());
		BufferedImage resized = Scalr.resize(ImageIO.read(input), width);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(resized, "jpeg", output);
		output.flush();
		image.setPicture(output.toByteArray());
		output.close();
	}
}
