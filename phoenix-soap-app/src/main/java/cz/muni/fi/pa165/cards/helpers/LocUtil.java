/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.helpers;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.faces.application.FacesMessage;
import javax.faces.application.FacesMessage.Severity;
import javax.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main util class
 *  - helps with I18n
 *  - faces messages
 * @author ph4r05
 */
public class LocUtil {
    private static final Logger log = LoggerFactory.getLogger(LocUtil.class);
    
    /**
     * Returns resource bundle for current localization, returns default message bundle
     * @return 
     */
    public static ResourceBundle getBundle() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        String messageBundleName = facesContext.getApplication().getMessageBundle();
        Locale locale = facesContext.getViewRoot().getLocale();
        ResourceBundle bundle = ResourceBundle.getBundle(messageBundleName, locale);
        return bundle;
    }
    
    /**
     * Returns message bundle by 
     * 
     * @param messageBundleName
     * @return 
     */
    public static ResourceBundle getBundle(String messageBundleName) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        Locale locale = facesContext.getViewRoot().getLocale();
        ResourceBundle bundle = ResourceBundle.getBundle(messageBundleName, locale);
        return bundle;
    }
    
    /**
     * Returns current locale selected
     * 
     * @return 
     */
    public static Locale getCurrentLocale(){
        FacesContext facesContext = FacesContext.getCurrentInstance();
        return facesContext.getViewRoot().getLocale();
    }
    
    /**
     * Gets message from resource bundle. Logs errors to log if any.
     * If bundle does not contain given key, N/A is returned and error logged.
     * @param bundle
     * @param key
     * @return 
     */
    public static String getMessageFromBundle(ResourceBundle bundle, String key) {
        try {
            if (bundle.containsKey(key)==false){
                log.error("Key not found in bundle");
                return "N/A";
            }
            
            return bundle.getString(key);
        } catch(MissingResourceException e){
            log.error("Error occured during message extraction (missing resource)", e);
            throw e;
        }catch(NullPointerException e){
            log.error("Error occured during message extraction (key is null) ", e);
            throw e;
        } catch(RuntimeException e){
            log.error("Unspecified error occured during message getting", e);
            throw e;
        }
    }
    
    /**
     * Gets string message from default resource bundle
     * @param key
     * @return 
     */
    public static String getMessageFromDefault(String key){
        // gets default bundle
        ResourceBundle bundle = LocUtil.getBundle();
        return LocUtil.getMessageFromBundle(bundle, key);
    }
    
    /**
     * Returns true if message with given key is in default resource bundle
     * @param key
     * @return 
     */
    public static boolean containsMessageDefault(String key){
        return LocUtil.getBundle().containsKey(key);
    }
    
    /**
     * Send message to user
     * 
     * @param sev
     * @param summary
     * @param detail 
     */
    public static void sendMessage(String destination, Severity sev, String summary, String detail){
        // add message
        FacesContext.getCurrentInstance().addMessage(destination,
                new FacesMessage(sev, summary, detail));
    }
}
