/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.xmpp;

import com.phoenix.db.Contactlist;
import com.phoenix.db.RemoteUser;
import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.utils.JiveGlobals;
import com.phoenix.utils.MiscUtils;
import com.phoenix.utils.PropertyEventDispatcher;
import com.phoenix.utils.PropertyEventListener;

import java.util.*;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author ph4r05
 */
@Service
@Scope(value = "singleton")
public class RosterSync extends BackgroundThreadService implements PropertyEventListener {
   private static final Logger log = LoggerFactory.getLogger(RosterSync.class);
   
   /**
    * Default timeout of the roster synchronization in seconds.
    */
   private static final long DEFAULT_TIMEOUT = 1000L*60L*60L*24L*7L;
   
   /**
    * Minimal value of the timeout allowed.
    */
   private static final long MIN_TIMEOUT = 1000L*60L;

    /**
     * Number of users in the local buffer to stat resync.
     */
    private static final int RESYNC_THRESHOLD = 100;
   
   /**
    * Settings value for update timeout.
    * If negative, then synchronization is disabled.
    * Has to be greater than minimal timeout.
    */
   private static final String PROP_TIMEOUT = "phonex.svc.rostersync.timeout";
   
   /**
    * Domain the XMPP server is enabled for.
    * XMPP server is working only for this particular domain if set.
    */
   public static final String PROP_DOMAIN = "phonex.svc.rostersync.domain";
   
   /**
    * Current timeout value loaded from settings.
    */        
   private long currentTimeout = DEFAULT_TIMEOUT;
   
   /**
    * Domain users are synchronized.
    */
   private String currentDomain = null;
   
   private long lastSync = 0;
   private volatile boolean running=true;

    /**
     * List of subscribers waiting for roster resync.
     */
    private final List<Subscriber> subList = new LinkedList<Subscriber>();
   
   @Autowired
   private PhoenixDataService dataService;
   
   @Autowired
   private JiveGlobals jiveGlobals;

   public RosterSync() {

   }

   /**
    * Initializes internal working thread.
    */
   @PostConstruct
   public synchronized void init() {
       PropertyEventDispatcher.addListener(this);
       
       initThread(this, "RosterSync");
       subList.clear();
       this.start();
   }
   
   @PreDestroy
   public synchronized void deinit(){
       PropertyEventDispatcher.removeListener(this);
       
       setRunning(false);
   }

    @Transactional
    public void doTheJob() {
        try {
            // Perform roster sync here.
            // Get all active users.
            TypedQuery<Subscriber> uq = null;

            final long android = dataService.getNewestAppVersionWithRetry("android", 5);
            log.info("Newest android version: " + android);

            // If XMPP is single-domain only, use only specified one.
            if (currentDomain!=null){
                uq = em.createQuery("SELECT u FROM Subscriber u WHERE u.deleted=false AND u.domain=?", Subscriber.class);
                uq.setParameter(1, currentDomain);
            } else {
                uq = em.createQuery("SELECT u FROM Subscriber u WHERE u.deleted=false", Subscriber.class);
            }

            // New resync run, clear old resync state.
            subList.clear();

            List<Subscriber> dbSubList = uq.getResultList();
            log.info("Going to sync roster for #of entries: " + (dbSubList == null ? 0 : dbSubList.size()));
            if (MiscUtils.collectionIsEmpty(dbSubList)){
                return;
            }

            for (Subscriber owner : dbSubList) {
                if (!isRunning() || !running){
                    log.info("Aborting execution, ending...");
                    return;
                }

                addSubscriberToSync(owner);
            }

            finishResync();
        } catch (Exception ex) {
            log.info("Problem occurred during roster sync", ex);
        }
    }

    public void addSubscriberToSync(Subscriber sub){
        subList.add(sub);

        // Reached threshold? Resync then
        if (subList.size() >= RESYNC_THRESHOLD){
            resyncFromList();
        }
    }

    public void finishResync(){
        resyncFromList();
    }

    protected void resyncFromList(){
        final Collection<RosterSyncElement> rosterDb = dataService.loadRosterSyncData(subList);

        // Process roster database, build roster resync data.
        try {
            log.info(String.format("Going to synchronize roster for #entries: %d, original size %d",
                    rosterDb.size(),
                    subList.size()
            ));

            if (!rosterDb.isEmpty()) {
                dataService.bulkSyncRosterWithRetry(rosterDb, 5);
            }
        } catch(Exception e){
            log.warn("Exception in bulk roster sync", e);
        }

        subList.clear();
    }

    @Override
    public void run() {
        // Property init if does not exist.
        String rawTimeout = jiveGlobals.getProperty(PROP_TIMEOUT);
        if (rawTimeout == null) {
            jiveGlobals.setProperty(PROP_TIMEOUT, String.valueOf(DEFAULT_TIMEOUT));
        } else {
            currentTimeout = jiveGlobals.getLongProperty(PROP_TIMEOUT, DEFAULT_TIMEOUT);
        }
        
        currentDomain = jiveGlobals.getProperty(PROP_DOMAIN);

        log.info("Starting roster sync with timeout=" + currentTimeout + " ms; raw=" + rawTimeout + "; domain="+currentDomain);

        while (this.running) {
            long cmilli = System.currentTimeMillis();

            // Check if roster sync is enabled and timeout value.
            if (currentTimeout >= MIN_TIMEOUT && (cmilli - lastSync) > currentTimeout) {
                lastSync = cmilli;
                doTheJob();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }
        log.info("RosterSync thread ended.");
    }

    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void propertySet(String property, Map<String, Object> params) {
        if (PROP_TIMEOUT.equals(property)){
            long newTimeout = jiveGlobals.getLongProperty(PROP_TIMEOUT, DEFAULT_TIMEOUT);
            if (newTimeout < MIN_TIMEOUT){
                log.warn("Roster sync timeout is below minimal value!");
            } else {
                this.currentTimeout = newTimeout;
            }
        }
        
        if (PROP_DOMAIN.equals(property)){
            this.currentDomain = jiveGlobals.getProperty(PROP_DOMAIN);
        }
    }

    @Override
    public void propertyDeleted(String property, Map<String, Object> params) {
        if (PROP_TIMEOUT.equals(property)){
            this.currentTimeout = DEFAULT_TIMEOUT;
        }
        
        if (PROP_DOMAIN.equals(property)){
            this.currentDomain = null;
        }
    }

    @Override
    public void xmlPropertySet(String property, Map<String, Object> params) {
        
    }

    @Override
    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        
    }
}

