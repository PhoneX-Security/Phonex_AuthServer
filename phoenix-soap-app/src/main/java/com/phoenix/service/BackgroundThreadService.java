/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background service with running thread inside.
 * @author ph4r05
 */
abstract public class BackgroundThreadService extends BackgroundService implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(BackgroundThreadService.class);
    protected Thread runThread;
    
    @Override
    abstract public void run();
    
    /**
     * Tests if this thread is alive.
     * @return 
     */
    public  boolean isAlive(){
        return runThread!=null && runThread.isAlive();
    }

    public  void setDaemon(boolean bln) {
        runThread.setDaemon(bln);
    }

    public  boolean isDaemon() {
        return runThread.isDaemon();
    }

    public synchronized void start() {
        runThread.start();
    }
    
    public  boolean threadNull(){
        return runThread==null;
    }
    
    public synchronized void initThread(Runnable runnable, String threadName){
        runThread = new Thread(runnable, threadName);
    }

    public Thread getRunThread() {
        return runThread;
    }
    
}
