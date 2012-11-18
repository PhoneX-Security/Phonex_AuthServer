/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.db;

import cz.muni.fi.pa165.cards.fulltext.CardPrivateFilterFactory;
import cz.muni.fi.pa165.cards.fulltext.OwnerFilterFactory;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.OneToMany;

import org.apache.solr.analysis.ASCIIFoldingFilterFactory;
import org.apache.solr.analysis.LowerCaseFilterFactory;
import org.apache.solr.analysis.StandardTokenizerFactory;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.AnalyzerDef;
import org.hibernate.search.annotations.Boost;
import org.hibernate.search.annotations.DocumentId;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FullTextFilterDef;
import org.hibernate.search.annotations.FullTextFilterDefs;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.TokenFilterDef;
import org.hibernate.search.annotations.TokenizerDef;

/**
 *
 * @author Miro
 */
@Entity
@Indexed //tridu budeme chtit indexovat ve fulltextu
@AnalyzerDef(name = "cardAnalyzer",
        tokenizer = @TokenizerDef(factory = StandardTokenizerFactory.class),
filters = {
    @TokenFilterDef(factory = ASCIIFoldingFilterFactory.class),
    @TokenFilterDef(factory = LowerCaseFilterFactory.class)
})
@FullTextFilterDefs( {
    @FullTextFilterDef(name = "card", impl = CardPrivateFilterFactory.class),
    @FullTextFilterDef(name = "owner", impl = OwnerFilterFactory.class)
})
public class Card implements Serializable{
    private static final long serialVersionUID = 112312352;
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @DocumentId(name="id")
    private Long id;    
    
    @ManyToOne(cascade={CascadeType.REFRESH})
    @JoinColumn(name = "owner_id")
    @IndexedEmbedded(depth = 1, prefix = "ownedBy_")
    private User owner;     
       
    @ManyToMany(cascade={CascadeType.REFRESH},fetch= FetchType.LAZY)
    @IndexedEmbedded(depth = 1, prefix = "categories_")
    private Set<Category> categories = new HashSet<Category>();
    
    @Field
    @Analyzer(definition = "cardAnalyzer")
    private String firstName;
    
    @Field
    @Analyzer(definition = "cardAnalyzer")
    @Boost(1.1f)
    private String secondName;
    
    @Field
    @Analyzer(definition = "cardAnalyzer")
    @Boost(1.5f)
    private String company;
    
    @Field
    @Analyzer(definition = "cardAnalyzer")
    private String email;
    
    @Field
    @Analyzer(definition = "cardAnalyzer")
    private String address;
    
    @Field
    @Analyzer(definition = "cardAnalyzer")
    private String telephone;
    @Field @Basic
    private boolean publicVisibility;

    @Lob
    @Field
    @Analyzer(definition = "cardAnalyzer")
    private String description;

    @MapKeyEnumerated(EnumType.STRING)
    @OneToMany(cascade=CascadeType.ALL)
    @JoinColumn(name = "image_id")
    private Map<ImageVariant,Image> images;// = new EnumMap<ImageVariant, Image>(ImageVariant.class);

    @ElementCollection   
    @MapKeyColumn(name="name")
    @Column(name="value")
    //@Field
    //@IndexedEmbedded(depth = 1, prefix = "properties_")
    private Map<String,String> properties = new HashMap<String, String>();
    
    
    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
    
    public void addProperty(String name, String value){
        this.properties.put(name, value);        
    }
    public String getProperty(String name){
        return this.properties.get(name);
    }
    public void removeProperty(String name){
        this.properties.remove(name);                
    }
    

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public Card() {      
        
    }

    public User getOwner() {
        return this.owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    
    public boolean getPublicVisibility() {
		return publicVisibility;
	}

	public void setPublicVisibility(boolean publicVisibility) {
		this.publicVisibility = publicVisibility;
	}

   
    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSecondName() {
        return secondName;
    }

    public void setSecondName(String secondName) {
        this.secondName = secondName;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }
   
    
     public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }
    
    public void addToCategory(Category category){        
        this.categories.add(category);
    }
    
    public void removeFromCategory(Category category){        
        this.categories.remove(category);
    }

    public Map<ImageVariant, Image> getImages() {
        return images;
    }

    public void setImages(Map<ImageVariant, Image> images) {
        this.images = images;
    }

    @Override
    public String toString() {
        return "Card: " + this.firstName + "|" + this.secondName + "|" + email + "|" + address;        
    }
    
    
    
    
     @Override
    public boolean equals(Object object) {
        if (object==null) return false;
           if (Card.class.isAssignableFrom(object.getClass())) {
            Card other = (Card) object;
            
            if (this.id==null && other.getId()==null){
                return true;
            }
            
            if (this.id==null || other.getId()==null){
                return false;
            }
            
            return this.id.equals(other.getId());
        }
        
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 0;        
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }
    
    
    
    
    
    
            
    
}
