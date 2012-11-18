package cz.muni.fi.pa165.cards.db;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;

/**
 * Created by IntelliJ IDEA.
 * User: bleble
 * Date: 22.11.2011
 * Time: 12:24
 */
@Entity
public class Image implements Serializable {
    private static final long serialVersionUID = 8530498503498503948L;
    @Id
    @GeneratedValue
    private Long id;
    @ManyToOne(targetEntity=Card.class, cascade = CascadeType.ALL)
    private Card card;
    private ImageVariant imageVariant;
    @Basic(fetch = FetchType.LAZY)
    @Lob
    @Column
    private byte[] picture;
    
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public ImageVariant getImageVariant() {
        return imageVariant;
    }

    public void setImageVariant(ImageVariant imageVariant) {
        this.imageVariant = imageVariant;
    }

    public byte[] getPicture() {
        return picture;
    }

    public void setPicture(byte[] picture) {
        this.picture = picture;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Image image = (Image) o;

        if (id != null ? !id.equals(image.id) : image.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
