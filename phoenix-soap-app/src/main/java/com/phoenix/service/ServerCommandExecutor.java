/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.utils.SpringInitializer;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Executes server commands in separate thread
 * @author ph4r05
 */
@Service
public class ServerCommandExecutor extends Thread {
    private static final Logger log = LoggerFactory.getLogger(ServerCommandExecutor.class);
    private static final String opensispsctl="/usr/sbin/opensipsctl";
    private static final String shell="/bin/sh";
    private static final String sudo="/usr/bin/sudo";
    
    // command queue here
    private ConcurrentLinkedQueue<ServerMICommand> commandQueue = new ConcurrentLinkedQueue<ServerMICommand>();
    private boolean running = true;
    
    // Spring application context - dependency injector
    private ApplicationContext appContext = null;
    
    @Autowired
    private SessionFactory sessionFactory;
    
    //@PersistenceContext
    protected EntityManager em;
    
    // last refresh of presence auth
    private long lastRefresh=0;
    
    public ServerCommandExecutor() {
        this.setName("ServerCommandExecutor");
    }
    
    /**
     * Reload presence meta data
     */
    public void reload(){
        long cmilli = System.currentTimeMillis();
        if ((lastRefresh - cmilli) > 1000*60*60){
            log.info("Presence auth data sync");
            
            
            lastRefresh = cmilli;
        }
    }
    
    public void initContext(){
        // spring application context init
        log.info("Spring context manual initialization");
        SpringInitializer springInitializer = new SpringInitializer();
        
        // Set current active profile, otherwise no profile will be loaded -> no database classes
        System.setProperty("spring.profiles.active", springInitializer.getCurrentProfile());
        
        // Problem here, in tomcat, WEB-INF is not in classpath, but WEB-INF/classes 
        // is, so copy of applicationContext is placed in casses directory also
        appContext = new ClassPathXmlApplicationContext("applicationContext.xml");
        sessionFactory = appContext.getBean("sessionFactory", SessionFactory.class);
        
        EntityManagerFactory factory = appContext.getBean("entityManagerFactory", EntityManagerFactory.class);
        em = factory.createEntityManager();
        
        log.info("EM=" + (em==null ? "NULL" : "ok") + "; open=" + em.isOpen());
        log.info("All dependencies initialized");
    }
    
    @Override
    public void run() {
        log.info("ServerCommandExecutor thread started");
        
        // init spring context
        this.initContext();
        
        // daemon loop
        while(running==true){
            
            // at first sleep for a while and if interrupted, end this thread
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ex) {
                log.error("Thread sleep interrupted, has to exit, in queue left elements: " + commandQueue.size(), ex);
                running=false;
                break;
            }
            
            if (commandQueue.isEmpty()) continue;
            ServerMICommand cmd = commandQueue.poll();
            if (cmd==null) continue;
            
            log.info("Going to execute server command: " + cmd.toString());
            
            // execute here server call - executing external command
            List<String> params  = new LinkedList<String>();
            //params.add(shell);
            params.add(sudo);
            params.add(opensispsctl);
            params.add("fifo");
            params.add(cmd.getCommandName());
            params.addAll(cmd.getParameters());
            
            ProcessBuilder b = new ProcessBuilder(params);
            try {
                Process p = b.start();
                p.waitFor();
                
                // To capture output from the shell
                InputStream shellIn = p.getInputStream();
                int shellExitStatus = p.waitFor();
                String response = PhoenixDataService.convertStreamToStr(shellIn);
                shellIn.close();
                
                log.info("Execution finished, exit code: [" + shellExitStatus + "]. Result=[" + response + "]");
            } catch(Exception ex){
                log.error("Exception during executing process", b);
            }
        }
    }
    
    /**
     * Inserts new command to be executed to the queue.
     * @param command 
     */
    public void addToQueue(ServerMICommand command){
        if (command==null){
            throw new NullPointerException("Command to be inserted to command queue cannot be null");
        }
        
        log.debug("Adding command to execute: " + command.toString());
        this.commandQueue.add(command);
    }    
    
    /**
     * Stops thread from running
     */
    public void stopRunning(){
        this.running = false;
    }

    public ApplicationContext getAppContext() {
        return appContext;
    }

    public void setAppContext(ApplicationContext appContext) {
        this.appContext = appContext;
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

}
