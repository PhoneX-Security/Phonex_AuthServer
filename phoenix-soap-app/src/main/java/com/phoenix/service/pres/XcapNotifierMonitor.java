/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.PhoenixDataService;
import static com.phoenix.service.pres.PresenceManager.NOTIFIER_SUFFIX;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
* Helper subclass for monitoring all active subscribers to detect 
* missing / extra XCAP rules for SIPNotification presence account.
* 
* @author ph4r05
*/
@Service
public class XcapNotifierMonitor extends BackgroundThreadService {
   private static final Logger log = LoggerFactory.getLogger(XcapNotifierMonitor.class);
   private long lastRefresh = 0;
   private boolean running=true;
   
   @Autowired
   private PresenceManager pm;
   
   @Autowired
   private PhoenixDataService dataService;

   public XcapNotifierMonitor() {

   }

   /**
    * Initializes internal running thread.
    */
   @PostConstruct
   public synchronized void init() {
       initThread(this, "XcapNotifierMonitor");
       this.start();
   }
   
   @PreDestroy
   public synchronized void deinit(){
       setRunning(false);
   }

   @Transactional
   public void doTheJob(){
       try {
            // Step 1 - find subscribers not having XCAP presence rules for
            // virtual presence notification account.
            // Optimal SQL query would be:
            // 
            // SELECT * FROM `subscriber` s
            // LEFT JOIN xcap x 
            // ON s.domain=x.domain AND s.username=x.username
            // WHERE x.id IS NULL and s.deleted=0
            // 
            // keyword "ON" isn not valid in JPQL in JPA 2.0 queries thus
            // cartesian product woudl have to be used what would be 
            // expensive for database. Native query may not be very
            // portable but for now it is acceptable price to pay 
            // for optimality.
            //
            // BUUUT, JPA 2.1 Draft supports ON keyword.
            //
            // Warning! Calling own methods with @Transactional won't work
            // here. Spring's AoP wraps Autowired beans with proxy class that 
            // handles transaction management. In case of "this" there is no
            // proxy to handle transaction and it will simply not work.
            // Update/delete/truncate queries has to be done inside transaction.
            //
            // 
            final String queryStringNative = 
                    " SELECT s.* FROM subscriber s" +
                    " LEFT JOIN xcap x " +
                    " ON s.domain=x.domain AND x.username=CONCAT(s.username, :suffix)" +
                    " WHERE x.id IS NULL and s.deleted=0";
            final String queryStringJPA21 =  
                    " SELECT s FROM Subscriber s" +
                    " LEFT JOIN com.phoenix.db.opensips.Xcap x " +
                    " ON s.domain=x.domain AND x.username=CONCAT(s.username, :suffix)" +
                    " WHERE x.id IS NULL and s.deleted=0";
            Query nativeQuery = pm.getEm().createNativeQuery(queryStringNative, Subscriber.class);
            nativeQuery.setParameter("suffix", NOTIFIER_SUFFIX);
            List resultList = nativeQuery.getResultList();
            final int resSize = resultList!=null?resultList.size():0;
            if (resSize > 0){
                log.info("Native query finished, size=" + resSize);
            }
            
            //TypedQuery<Subscriber> jpaQuery = em.createQuery(queryStringJPA21, Subscriber.class);
            //List resultList = jpaQuery.getResultList();
            for(Object obj : resultList){
                Subscriber tmpS = (Subscriber) obj;
                String tmpSip = PhoenixDataService.getSIP(tmpS);

                ArrayList<String> arrayList = new ArrayList<String>();
                arrayList.add(tmpSip);

                // Add corresponding XCAP record
                final String usernameNotif = pm.getSipNotifierUsername(tmpS.getUsername());
                pm.updateXCAPPolicyFile(usernameNotif, tmpS.getDomain(), arrayList);

                // Refresh XCAP server via MI command.
                pm.sendCommand(new ServerMIRefreshWatchers(usernameNotif + "@" + tmpS.getDomain(), 0));

                log.info("Added XCAP for notification for: " + tmpSip);
            }
        } catch(Exception ex){
            log.info("Problem occurred during updating Xcap", ex);
        }
   }
   
    @Override
    public void run() {
        while (this.running) {
            long cmilli = System.currentTimeMillis();
            if ((cmilli - lastRefresh) > PresenceManager.REFRESH_XCAP_NOTIFY) {
                lastRefresh = cmilli;
                doTheJob();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }
    }

    public PresenceManager getPm() {
        return pm;
    }

    public void setPm(PresenceManager pm) {
        this.pm = pm;
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
}
