/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.files;

import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.PhoenixDataService;
import java.util.Calendar;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for cleaning file storage of the file transfer services.
 * Cleans temporary files and files
 * @author ph4r05
 */
@Service
public class FSCleaner  extends BackgroundThreadService {
   private static final Logger log = LoggerFactory.getLogger(FSCleaner.class);
   
   /**
    * Primarily the this task will be executed after this day-hour.
    * Allowed span for starting this task is 1 hour.
    * Since this task can be resource expensive it is performed in night hours.
    */
   private static final int START_HOUR = 2;
   
   /**
    * Maximum allowed delay of the task invocation. 
    * If task was not executed in last MAX_TIMEOUT milliseconds it will be executed
    * as soon as possible.
    */
   private static final long MAX_TIMEOUT = 1000*60*60*24;
   
   private long lastRefresh = 0;
   private boolean running=true;
   
   @Autowired
   private FileManager fm;
   
   @Autowired
   private PhoenixDataService dataService;

   public FSCleaner() {

   }

   /**
    * Initializes internal running thread.
    */
   @PostConstruct
   public synchronized void init() {
       initThread(this, "FSCleaner");
       this.start();
   }
   
   @PreDestroy
   public synchronized void deinit(){
       setRunning(false);
   }

   @Transactional
   public void doTheJob(){
       try {
            log.info("FSCleaner started");
            
            fm.expireRecords();
            fm.cleanupFS();
            
            log.info("FSCleaner ended");
        } catch(Exception ex){
            log.info("Problem occurred during updating Xcap", ex);
        }
   }
   
   @Override
   public void run(){
       while(this.running){
           Calendar cal = Calendar.getInstance();
           long cmilli = System.currentTimeMillis();
           int hour = cal.get(Calendar.HOUR_OF_DAY);
            
           // This task is executed if 
           //  a) it was not executed in previous MAX_TIMEOUT milliseconds OR
           //  b) current hour of day is START_HOUR or 1 hour later.
           if ((cmilli - lastRefresh) > MAX_TIMEOUT || (hour-START_HOUR) <= 1){
                lastRefresh = cmilli;

                doTheJob();
                
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    log.error("Interrupted", ex);
                    break;
                }
            }
       }
   }

    public FileManager getFm() {
        return fm;
    }

    public void setFm(FileManager fm) {
        this.fm = fm;
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



