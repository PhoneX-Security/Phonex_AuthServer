/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service;

import com.phoenix.utils.JiveGlobals;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for property synchronization from database.
 * @author ph4r05
 */
@Service
@Scope(value = "singleton")
public class PropertySyncService extends BackgroundThreadService {
   private static final Logger log = LoggerFactory.getLogger(PropertySyncService.class);
   
   /**
    * Timeous sync in milliseconds.
    */
   private static final long TIMEOUT_SYNC = 1000*60*30;
   
   private long lastSync = 0;
   private volatile boolean running=true;
   
   @Autowired
   private PhoenixDataService dataService;
   
   @Autowired
   private JiveGlobals jiveGlobals;

   public PropertySyncService() {

   }

   /**
    * Initializes internal running thread.
    */
   @PostConstruct
   public synchronized void init() {
       initThread(this, "PropertySyncService");
       this.start();
   }
   
   @PreDestroy
   public synchronized void deinit(){
       setRunning(false);
   }

   @Transactional
   public void doTheJob(){
       try {
            jiveGlobals.syncProperties();
        } catch(Exception ex){
            log.info("Problem occurred during property sync", ex);
        }
   }
   
    @Override
    public void run() {
        while (this.running) {
            long cmilli = System.currentTimeMillis();
            if ((cmilli-lastSync) > TIMEOUT_SYNC){
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
        log.info("PropertySync thread ended.");
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


