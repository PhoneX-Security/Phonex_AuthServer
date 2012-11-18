/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.db;

import cz.muni.fi.pa165.cards.fulltext.CategoryFilterFactory;
import cz.muni.fi.pa165.cards.fulltext.OwnerFilterFactory;
import cz.muni.fi.pa165.cards.managers.CategoryManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.apache.solr.analysis.ASCIIFoldingFilterFactory;
import org.apache.solr.analysis.LowerCaseFilterFactory;
import org.apache.solr.analysis.StandardTokenizerFactory;
import org.hibernate.search.annotations.Analyzer;
import org.hibernate.search.annotations.AnalyzerDef;
import org.hibernate.search.annotations.Boost;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FullTextFilterDef;
import org.hibernate.search.annotations.FullTextFilterDefs;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.TokenFilterDef;
import org.hibernate.search.annotations.TokenizerDef;
import org.hibernate.search.filter.ShardSensitiveOnlyFilter;

/**
 * Entity represents single category node.
 * @author ph4r05
 */
@Entity
@Indexed //tridu budeme chtit indexovat ve fulltextu
@AnalyzerDef(name = "categoryAnalyzer",
        tokenizer = @TokenizerDef(factory = StandardTokenizerFactory.class),
filters = {
    @TokenFilterDef(factory = ASCIIFoldingFilterFactory.class),
    @TokenFilterDef(factory = LowerCaseFilterFactory.class)
})
@FullTextFilterDefs( {
    @FullTextFilterDef(name = "category", impl = CategoryFilterFactory.class)
})
@Embeddable
public class Category implements Serializable {
    private static final long serialVersionUID = 1216348069826762177L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    @Field
    @Analyzer(definition = "categoryAnalyzer")
    @Boost(1.5f)
    private String name;
    
    @Field
    @Analyzer(definition = "categoryAnalyzer")
    @Lob
    private String description;
    
    @Field
    private boolean privateCategory=false;
    
    @ManyToOne
    @JoinColumn(name = "owner_id")
    @IndexedEmbedded(depth = 1, prefix = "ownedBy_")
    private User owner;
    
    /**
     * Change on children is NOT cascaded!
     * (to keep model consistent)
     * 
     * Change can be made by iterating over children and setting parent.
     * But specialized operations should be used instead (which CategoryManager provides)
     * 
     * @OrderColumn is disabled for now - categories will be sorted alphabetically
     */
    @OneToMany(fetch=FetchType.LAZY, orphanRemoval=true)
    @JoinColumn(name = "parent_id")
    @OrderBy("name ASC")
    private List<Category> children = new ArrayList<Category>();

    @ManyToOne(fetch=FetchType.LAZY, cascade = {CascadeType.ALL})
    @JoinColumn(name = "parent_id")//,insertable=false,updatable=false)
    private Category parent;

    public Category() {
    }
    
    public Category(Long id, String name, String description, User owner) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.parent = null;
    }    
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Internal method to modify children content
     * @return 
     */
    protected List<Category> getWriteableChildren() {
        return children;
    }
    
    /**
     * Returns unmodifiable set since children should not be directly modified
     * since operations on it are not cascaded
     */
    public List<Category> getChildren() {
        return Collections.unmodifiableList(this.children);
    }
    
    public boolean emptyChildren() {
        return this.children == null || this.children.isEmpty();
    }

    public void setChildren(List<Category> children) {
        this.children = children;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getParent() {
        return parent;
    }

    /**
     * Sets parent,
     * keeps bidirectional associations consistent.
     * If oldParent is not null, then this is removed from its children list.
     * If new parent is not null, then this is added to its children list.
     * 
     * @param parent 
     */
    public void setParent(Category parent) {
        Category oldParent = this.parent;
        this.parent = parent;
        
        // nothing to do
        if (parent==null && oldParent==null){
            return;
        }
        
        // deassoc from old parent if any
        if (oldParent!=null 
                && oldParent instanceof Category 
                && !oldParent.equals(parent)
                && oldParent.getChildren()!=null){
            
            // remove this element from old parents list
            CategoryManager.removeCategoryFromCollection(this, oldParent.getWriteableChildren());
        }
        
        // assoc to new parent
        if (this.parent!=null 
                && !this.parent.equals(oldParent)
                && this.parent.getChildren()!=null){
            this.parent.getWriteableChildren().add(this);
        }
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public boolean getPrivateCategory() {
        return privateCategory;
    }

    public void setPrivateCategory(boolean privateCategory) {
        this.privateCategory = privateCategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
          if (object==null) return false;
           if (Category.class.isAssignableFrom(object.getClass())) {
            Category other = (Category) object;
            
            if (this.id==null && other.getId()==null){
                return true;
            }
            
            if (this.id==null || other.getId()==null){
                return false;
            }
            
            return this.id.equals(other.getId());
        }
        
        return false;
        
//        // TODO: Warning - this method won't work in the case the id fields are not set
//        if (!(object instanceof Category)) {
//            return false;
//        }
//        Category other = (Category) object;
//        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
//            return false;
//        }
//        return true;
    }

    @Override
    public String toString() {
        return "cz.muni.fi.pa165.vizitky.db.Category[ id=" + id + " name=" + name + "]";
    }
    
}

