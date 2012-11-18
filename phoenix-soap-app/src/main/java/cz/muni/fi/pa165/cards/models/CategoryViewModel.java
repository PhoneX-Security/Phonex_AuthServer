package cz.muni.fi.pa165.cards.models;

import cz.muni.fi.pa165.cards.db.Category;
import java.io.Serializable;

/**
 * Base category view - adaptor.
 * Use: in session scoped beans as attributes. Minimalizes memory requirements
 * due to lack of hierarchy inclusion (no children and parent referenced with ID).
 * 
 * Can be loaded from / converted to original Category class (db entity)
 * @author ph4r05
 */
public class CategoryViewModel implements Serializable {
    private static final long serialVersionUID = 1234L;
    
    private Long id;
    private String displayName;
    private String name;
    private String description;
    private boolean privateFlag;
    private Long parentId;

    /**
     * Init model from given category
     * @param cat 
     */
    public void initFrom(Category cat){
        if (cat==null){
            throw new NullPointerException("Cannot load data from empty category");
        }
        
        this.parentId = null;
        this.id = cat.getId();
        this.name = cat.getName();
        this.displayName = cat.getName();
        this.description = cat.getDescription();
        this.privateFlag = cat.getPrivateCategory();
        if (cat.getParent()!=null){
            this.parentId = cat.getParent().getId();
        }
    }
    
    /**
     * Copy current settings to given category.
     * {ID, ParentID, displayName} are not reflected!
     * @param cat 
     */
    public void copyTo(Category cat){
        if (cat==null){
            throw new NullPointerException("Cannot copy data from empty category");
        }
        cat.setName(name);
        cat.setDescription(description);
        cat.setPrivateCategory(privateFlag);
    }
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isPrivateFlag() {
        return privateFlag;
    }

    public void setPrivateFlag(boolean privateFlag) {
        this.privateFlag = privateFlag;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CategoryViewModel other = (CategoryViewModel) obj;
        if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "CategoryViewModel{" + "id=" + id + ", displayName=" + displayName + ", name=" + name + ", description=" + description + ", privateFlag=" + privateFlag + ", parentId=" + parentId + '}';
    }
}
