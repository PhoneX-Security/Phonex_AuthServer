package cz.muni.fi.pa165.cards.fulltext;

/**
 * Corresponds to filter to apply on lucene search query
 * @author ph4r05
 */
public class FulltextFilterEntity {
    private String filterName;
    
    private boolean enable=true;
    
    private String parameter;
    
    private Object value;

    public FulltextFilterEntity(String filterName, boolean enable) {
        this.filterName = filterName;
        this.enable = enable;
    }
    
    public FulltextFilterEntity(String filterName, boolean enable, String parameter, Object value) {
        this.filterName = filterName;
        this.enable = enable;
        this.parameter = parameter;
        this.value = value;
    }
    
    public String getFilterName() {
        return filterName;
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }  
}
