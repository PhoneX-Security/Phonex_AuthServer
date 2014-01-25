/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import com.phoenix.db.opensips.Xcap;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.PhoenixDataService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Random;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing bean for presence.
 * @author ph4r05
 */
@Service
@Repository
public class PresenceManager {
    private static final Logger log = LoggerFactory.getLogger(PresenceManager.class);
    public static final String PRESENCE_RULES_TEMPLATE = "pres-rules-template.xml";
    public static final String PRESENCE_PUBLISH_TEMPLATE = "pres-publish-template.xml";
    
    private static final String STATE_BASIC_OPEN = "open";
    private static final String STATE_BASIC_CLOSED = "closed";
    
    private static final String RULES_PLACEHOLDER = "[[[RULES]]]";
    private static final String PUBLISH_PLACEHOLDER_BASIC = "[[[BASIC]]]";
    private static final String PUBLISH_PLACEHOLDER_ENTITY = "[[[ENTITY]]]";
    private static final String PUBLISH_PLACEHOLDER_TUPLE_ID = "[[[TUPLEID]]]";
    private static final String PUBLISH_PLACEHOLDER_PERSON_ID = "[[[PERSONID]]]";
    private static final String PUBLISH_PLACEHOLDER_STATUS_TEXT = "[[[STATUSTEXT]]]";
    
    public enum PresenceStatus {
        OPEN, 
        CLOSED
    };
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private PhoenixDataService dataService;
    
    /**
     * Presence rules template for XCAP server (permissions to observe presence changes).
     */
    private String presenceRulesTemplate;
    
    /**
     * Presence publish template file for PUA_MI module to publish
     * presence information on behalf virtual users.
     */
    private String presencePublishTemplate;

    /**
     * No-arg constructor for Spring container.
     */
    public PresenceManager() {
    }
    
    /**
     * Callback called after Spring Container initializes this object.
     * All dependencies should be autowired prior this call.
     */
    @PostConstruct
    public void init(){
        log.info("PostContruct called on presence manager; this="+this);
        
        try {
            this.presenceRulesTemplate = loadTemplate(PRESENCE_RULES_TEMPLATE);
            this.presencePublishTemplate = loadTemplate(PRESENCE_PUBLISH_TEMPLATE);
        } catch(Exception e){
            log.error("Cannot load presence policy template file from resources.", e);
            throw new RuntimeException("Cannot load presence policy template file from resources.", e);
        }
    }
    
    /**
     * Loads template file from resources.
     * @param resource
     * @return 
     * @throws java.io.IOException 
     */
    public String loadTemplate(String resource) throws IOException{
        InputStream resourceAsStream = PresenceManager.class.getClassLoader().getResourceAsStream(resource);
        return PhoenixDataService.convertStreamToStr(resourceAsStream);
    }
    
    /**
     * Parses input template and builds new template adding all sips to the whitelist.
     * @param template
     * @param sips
     * @return 
     */
    public String getXCAPFile(String template, List<String> sips){
        if (sips==null){
            throw new NullPointerException("Sips list is null");
        }
        
        StringBuilder sb = new StringBuilder();
        for(String s : sips){
            // check string s for valid characters
            if (s.matches("[a-zA-Z0-9_\\-]+@[a-zA-Z0-9\\._\\-]+")==false){
                log.warn("Illegal sip passed: " + s);
                continue;
            }
            sb.append("                   <cp:one id=\"sip:").append(s).append("\" />\n");
        }
        
        return template.replace(RULES_PLACEHOLDER, sb.toString());
    }
    
    /**
     * Parses input template and builds new template adding all sips to the whitelist.
     * @param sips
     * @return 
     */
    public String getXCAPFilee(List<String> sips){
        return getXCAPFile(presenceRulesTemplate, sips);
    }
    
    /**
     * Builds presence PIDF message for publishing.
     * @param entity
     * @param ps
     * @param statusText
     * @return 
     */
    public String getPresencePublishPidf(String entity, PresenceStatus ps, String statusText){
        // Generate random IDs for the request.
        Random rand  = new Random();
        int tupleId  = rand.nextInt();
        int personId = rand.nextInt();
        if (tupleId<0) tupleId *= -1;
        if (personId<0) personId *= -1;
        final String presStatus = presenceStatus2String(ps);
        
        // Substitute real values to the template
        String xml = presencePublishTemplate;
        xml = xml.replace(PUBLISH_PLACEHOLDER_ENTITY, entity);
        xml = xml.replace(PUBLISH_PLACEHOLDER_PERSON_ID, String.format("pdd%d", personId));
        xml = xml.replace(PUBLISH_PLACEHOLDER_TUPLE_ID, String.format("0x%x", tupleId));
        xml = xml.replace(PUBLISH_PLACEHOLDER_BASIC, presStatus);
        xml = xml.replace(PUBLISH_PLACEHOLDER_STATUS_TEXT, statusText);
        
        return xml;
    }
    
    /**
     * Converts PresenceStatus enum to string
     * @param ps
     * @return 
     */
    public String presenceStatus2String(PresenceStatus ps){
        return ps==PresenceStatus.OPEN ? STATE_BASIC_OPEN : STATE_BASIC_CLOSED;
    }
    
    /**
     * Updates XCAP database for particular user. Sets read permissions to 
     * a sip addresses given in as a parameter.
     * 
     * @param username
     * @param domain
     * @param sips
     * @return new persisted XCAP entity
     * @throws java.io.UnsupportedEncodingException
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public Xcap updateXCAPPolicyFile(String username, String domain, List<String> sips) throws UnsupportedEncodingException{
        String xmlfile = getXCAPFile(presenceRulesTemplate, sips);
        log.info("Going to update presence rules for user["+username+"]: " + xmlfile);

        //
        // XCAP table update;
        //
        Query delQuery = this.em.createQuery("DELETE FROM Xcap x "
                + " WHERE x.username=:uname AND x.domain=:domain AND doc_type=2");
        delQuery.setParameter("uname", username);
        delQuery.setParameter("domain", domain);
        delQuery.executeUpdate();

        Xcap xcapEntity = new Xcap(
                username, 
                domain, 
                xmlfile.getBytes("UTF-8"), 
                2, 
                "", 0, "index.xml", 0);
        
        this.em.persist(xcapEntity);
        return xcapEntity;
    }
    
    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EndpointAuth getAuth() {
        return auth;
    }

    public void setAuth(EndpointAuth auth) {
        this.auth = auth;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }
}
