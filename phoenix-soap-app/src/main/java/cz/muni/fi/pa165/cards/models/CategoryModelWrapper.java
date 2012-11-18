package cz.muni.fi.pa165.cards.models;

import java.io.Serializable;

/**
 * Wraps category after flattening
 * @author ph4r05
 */
public class CategoryModelWrapper implements Serializable{
    private CategoryViewModel cat;
    private int level;
    private int order;
    private int childrenCount;
    private boolean first;
    private boolean last;
    private String path2root;

    public CategoryModelWrapper(CategoryViewModel cat) {
        this.cat = cat;
    }

    public CategoryModelWrapper(CategoryViewModel cat, int level, boolean first, boolean last) {
        this.cat = cat;
        this.level = level;
        this.first = first;
        this.last = last;
    }

    public CategoryModelWrapper(CategoryViewModel cat, int level, int order, int childrenCount, boolean first, boolean last) {
        this.cat = cat;
        this.level = level;
        this.order = order;
        this.childrenCount = childrenCount;
        this.first = first;
        this.last = last;
    }
    
    /**
     * Copies all attributes from flatten wrapper except category
     * @param flWrap 
     */
    public void copyFromCategoryWrapper(CategoryFlattenWrapper flWrap){
        if (flWrap==null){
            throw new NullPointerException("Cannot copy from null wrapper");
        }
        
        this.childrenCount = flWrap.getChildrenCount();
        this.first = flWrap.isFirst();
        this.last = flWrap.isLast();
        this.level = flWrap.getLevel();
        this.order = flWrap.getOrder();
        this.path2root = flWrap.getPath2root();
    }
    
    /**
     * Rewrapping constructor 
     * @param cat 
     */
    public CategoryModelWrapper(CategoryFlattenWrapper cat) {
        this.cat = new CategoryViewModel();
        if (cat.getCat()!=null){
            this.cat.initFrom(cat.getCat());
        }
        
        this.childrenCount = cat.getChildrenCount();
        this.first = cat.isFirst();
        this.last = cat.isLast();
        this.level = cat.getLevel();
        this.order = cat.getOrder();
        this.path2root = cat.getPath2root();
    }

    public CategoryViewModel getCat() {
        return cat;
    }

    public void setCat(CategoryViewModel cat) {
        this.cat = cat;
    }
    

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getChildrenCount() {
        return childrenCount;
    }

    public void setChildrenCount(int childrenCount) {
        this.childrenCount = childrenCount;
    }

    public String getPath2root() {
        return path2root;
    }

    public void setPath2root(String path2root) {
        this.path2root = path2root;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CategoryModelWrapper other = (CategoryModelWrapper) obj;
        if (this.cat != other.cat && (this.cat == null || !this.cat.equals(other.cat))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + (this.cat != null ? this.cat.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "CategoryFlattenWrapper{" + "cat=" + cat + ", level=" + level + ", order=" + order + ", childrenCount=" + childrenCount + ", first=" + first + ", last=" + last + ", path2root=" + path2root + '}';
    }

}
