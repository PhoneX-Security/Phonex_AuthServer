package cz.muni.fi.pa165.cards.models;

import java.util.Iterator;
import java.util.List;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts Category to String for comboBoxes and vice versa.
 * @author ph4r05
 */
public class CategoryModelWrapperConverter implements javax.faces.convert.Converter {
    private static final Logger log = LoggerFactory.getLogger(CategoryModelWrapperConverter.class);
    
    /**
     * Converts string representation to category model
     * string repr = ID
     * @param context
     * @param component
     * @param value
     * @return 
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object getAsObject(FacesContext context, UIComponent component, String value) {
        log.debug("Converter getAsObject: " + value);                
        List<SelectItem> selectItems = (List<SelectItem>) component.getAttributes().get("selectItems");
        if (selectItems==null){
            return null;
        }
        
        Iterator<SelectItem> iterator = selectItems.iterator();
        while (iterator.hasNext()) {
            SelectItem selItem = iterator.next();
            
            // test compatibility
            if (selItem.getValue() == null 
                    || selItem.getValue().getClass().isAssignableFrom(CategoryModelWrapper.class)==false){
                continue;
            }
            
            CategoryModelWrapper comboBoxItem = (CategoryModelWrapper) selItem.getValue();
            // test null
            if (comboBoxItem.getCat()==null || comboBoxItem.getCat().getId()==null){
                throw new NullPointerException("Category with null pointer occurred: " + comboBoxItem);
            }
            
            if (comboBoxItem.getCat().getId().toString().equals(value)) {
                return comboBoxItem;
            }
        }
        return null;
    }

    /**
     * Converts category view model to string representation
     * @param context
     * @param component
     * @param value
     * @return 
     */
    @Override
    public String getAsString(FacesContext context, UIComponent component, Object value) {        
        if (value == null) {
            return "";
        }
        
        // test correct application
        if (value==null){
            throw new NullPointerException("Passed null value to convert to string");
        }
        
        if (CategoryModelWrapper.class.isAssignableFrom(value.getClass())==false){
            // cannot use this conversion to string, we don't know this object
            throw new IllegalArgumentException("Passed unknown class to convert to string: " + value.getClass());
        }
        CategoryModelWrapper comboBoxItem = (CategoryModelWrapper) value;
        
        // has id?
        if (comboBoxItem.getCat()==null || comboBoxItem.getCat().getId()==null){
            return "";
        }
        
        return comboBoxItem.getCat().getId().toString();
    }
}
