package cz.muni.fi.pa165.cards.helpers;

import java.util.HashMap;
import java.util.Map;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.web.context.request.FacesRequestAttributes;

/**
 * ViewScope JSF implementation for spring. Supports destructive callbacks. Uses JSF
 * methods to proper manage lifecycle.
 * 
 * @author harezmi
 */
public class ViewScope implements Scope {
    private static final Logger log = LoggerFactory.getLogger(ViewScope.class);
    public static final String VIEW_SCOPE_CALLBACKS = "viewScope.callbacks";

    
    public synchronized Object get(String name, ObjectFactory<?> objectFactory) {
    //public synchronized Object get(String name, ObjectFactory objectFactory) {
        Map<String, Object> viewMap = getViewMap();
        if (viewMap==null){
            log.info("Returning null. [get] for: " + name);
            return null;
        }
        
        boolean newInstance=false;
        Object instance = viewMap.get(name);
        if (instance == null) {
            instance = objectFactory.getObject();
            viewMap.put(name, instance);
            newInstance=true;
        }
        
        log.info("Returning OK instance for name: " + name + "; newinstance: " + (newInstance? "TRUE":"FALSE"));
        return instance;
    }

    @Override
    public Object remove(String name) {
        Map<String, Object> viewMap = getViewMap();
        if (viewMap==null){
            log.info("Returning null. [remove] for: " + name);
            return null;
        }
        
        log.info("ViewURoot is ok for: " + name + "; [remove]");
        
        Object instance = viewMap.remove(name);
        if (instance != null) {
            Map<String, Runnable> callbacks = (Map<String, Runnable>) getViewMap().get(VIEW_SCOPE_CALLBACKS);
            if (callbacks != null) {
                callbacks.remove(name);
            }
        }
        return instance;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable runnable) {
        Map<String, Runnable> callbacks = (Map<String, Runnable>) getViewMap().get(VIEW_SCOPE_CALLBACKS);
        if (callbacks != null) {
            callbacks.put(name, runnable);
        }
    }

    
    public Object resolveContextualObject(String name) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        FacesRequestAttributes facesRequestAttributes = new FacesRequestAttributes(facesContext);
        return facesRequestAttributes.resolveReference(name);
    }

    @Override
    public String getConversationId() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        FacesRequestAttributes facesRequestAttributes = new FacesRequestAttributes(facesContext);
        return facesRequestAttributes.getSessionId() + "-" + facesContext.getViewRoot().getViewId();
    }

    private Map<String, Object> getViewMap() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        UIViewRoot viewRoot = currentInstance.getViewRoot();
        if (viewRoot==null){
            // problem here :)
            log.error("!! ViewRoot component is null, simulating request scoped, please check");
            log.info("Current phase id: " + currentInstance.getCurrentPhaseId());
            return null;
        }
        
        return viewRoot.getViewMap(true);
    }
}