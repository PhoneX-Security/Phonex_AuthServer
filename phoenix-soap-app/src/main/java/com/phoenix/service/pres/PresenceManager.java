/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import com.phoenix.db.opensips.Xcap;
import com.phoenix.service.DaemonStarter;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.ServerCommandExecutor;
import com.phoenix.service.ServerMICommand;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.ServletContext;
import org.bouncycastle.util.encoders.Base64;
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
    
    public static String NOTIFIER_SUFFIX = "*i_notify";
    
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
    
    @Autowired
    ServletContext context;
    
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
     * Cached presence events for each SIP user (key to the map).
     */
    private final Map<String, PresenceEvents> cachedEvents = new ConcurrentHashMap<String, PresenceEvents>();
    
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
        log.info("PostContruct called on presence manager; ctxt="+context+"; cached="+cachedEvents+"; this="+this);
        
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
    
    /**
     * Returns notifier SIP address for given SIP user.
     * 
     * @param sip
     * @return 
     */
    public String getSipNotifier(String sip){
        // Parse SIP contact to user name and domain.
        if (sip==null)
            throw new NullPointerException("SIP cannot be null");
        if (sip.contains("@")==false || sip.indexOf("@") != sip.lastIndexOf("@"))
            throw new IllegalArgumentException("SIP is not well formed");
        
        String[] sipArr = sip.split("@");
        final String username = sipArr[0];
        final String domain = sipArr[1];
        
        return username + NOTIFIER_SUFFIX + "@" + domain;
    }
    
    /**
     * Notification of the user for new uploaded files.
     * This is not extensible design.
     * Proposal for better extensibility:
     *  - Assumption: another events will be signaled to the user besides new files
     *  - Thus already published information has to be cached in some long running thread.
     *      Thus if a new file is published all previously published cached information
     *      has to be sent along with this new information.
     *  - PresenceNotifier will be long running thread like ServerExecutor, caching
     *      all new information per user. On publishing new info, all cached data are packed
     *      and sent again.
     *  - In new implementation this request would be passed to PresenceNotifier.
     *      
     * @param sip
     * @param nonces
     * @throws java.io.IOException
     */
    public synchronized void notifyNewFiles(String sip, List<String> nonces) throws IOException{
        // Get existing PE
        PresenceEvents pe;
        if (cachedEvents.containsKey(sip)){
            pe = cachedEvents.get(sip);
        } else {
            pe = new PresenceEvents();
            pe.setVersion(1);
        }
        
        // Store new cached events.
        pe.setFiles(nonces);
        this.cachedEvents.put(sip, pe);
        
        // Generate JSON & notify.
        sendNotification(sip, pe);
    }
    
    /**
     * Sends prepared presence notification to the user via PIDF.
     * 
     * @param sip
     * @param ev 
     * @throws java.io.IOException 
     */
    public synchronized void sendNotification(String sip, PresenceEvents ev) throws IOException{
        final String notifSip = getSipNotifier(sip);
        
        // Generate notify body
        final String json = ev.toJSON();
        final String b64  = new String(Base64.encode(json.getBytes("UTF-8")), "UTF8");
        final String pidf = getPresencePublishPidf(notifSip, PresenceManager.PresenceStatus.OPEN, b64);
        
        // Send presence command
        ServerMIPuaPublish cmd = new ServerMIPuaPublish(notifSip, 3600, pidf);
        
        // send notification
        sendCommand(cmd);
    }
    
    /**
     * Sends server command to executor.
     * 
     * @param cmd
     * @return 
     */
    public int sendCommand(ServerMICommand cmd){
        ServerCommandExecutor executor = getExecutor();
        if (executor==null)
            return -1;
        
        executor.addToQueue(cmd);
        return 0;
    }
    
    /**
     * Obtains daemon executor running in the background.
     * @return 
     */
    public ServerCommandExecutor getExecutor(){
        ServerCommandExecutor executor = null;
        
        try {
            DaemonStarter dstarter = DaemonStarter.getFromContext(context);
            if (dstarter==null){
                log.warn("Daemon starter is null, wtf?");
                return null;
            }
            
            executor = dstarter.getCexecutor();
            log.info("Executor loaded: " + executor.toString());
        }catch(Exception ex){
            log.error("Exception during getExecutor()", ex);
        }
        
        return executor;
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

    public ServletContext getContext() {
        return context;
    }

    public void setContext(ServletContext context) {
        this.context = context;
    }
}
