/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.db.opensips.Xcap;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import javax.persistence.TypedQuery;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/**
 * Executes server commands in separate thread
 * @author ph4r05
 */
@Service
@Repository
public class ServerCommandExecutor extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(ServerCommandExecutor.class);
    private static final String THREAD_NAME="ServerCommandExecutor";
    private static final String opensispsctl="/usr/sbin/opensipsctl";
    private static final String shell="/bin/sh";
    private static final String sudo="/usr/bin/sudo";
    
    // command queue here
    //private ConcurrentLinkedQueue<ServerMICommand> commandQueue = new ConcurrentLinkedQueue<ServerMICommand>();
    private final PriorityBlockingQueue<ServerMICommand> commandQueue = new PriorityBlockingQueue<ServerMICommand>();
    private final PriorityBlockingQueue<ServerMICommand> hiPriorityCommandQueue = new PriorityBlockingQueue<ServerMICommand>();
    private boolean running = true;
    
    // last refresh of presence auth
    private long lastRefresh=0;
    
    public ServerCommandExecutor() {
        
    }
    
    /**
     * Initializes internal running thread.
     */
    public synchronized void init(){
        initThread(this, THREAD_NAME);
    }
    
    @Override
    public void onContextInitialized(ServletContextEvent sce) {
        super.onContextInitialized(sce); 
        
        init();
    }
    
    /**
     * Obtains serverCommandExecutor instance from servlet context.
     * @param request
     * @return 
     */
    public static ServerCommandExecutor getExecutor(HttpServletRequest request){
        ServerCommandExecutor executor = null;
        DaemonStarter dstarter = DaemonStarter.getFromContext(request);
        if (dstarter==null){
            log.warn("Daemon starter is null, wtf?");
            return null;
        }
            
        executor = dstarter.getCexecutor();
        return executor;
    }
    
    /**
     * Reload presence meta data
     */
    public void reloadPresence(){
        long cmilli = System.currentTimeMillis();
        if ((cmilli - lastRefresh) > 1000*60*60){
            lastRefresh = cmilli;
            
            // fetch all presence policies to refresh from database
            try {
                log.info("Batch presence auth data sync");
                String querySql = "SELECT x FROM Xcap x";
                TypedQuery<Xcap> query = em.createQuery(querySql, Xcap.class);
                List<Xcap> resultList = query.getResultList();
                if (resultList!=null && resultList.size() > 0){
                   for(Xcap e : resultList){
                        ServerMICommand cmd;
                        cmd = new ServerMIRefreshWatchers(e.getUsername() + "@" + e.getDomain(), 0);
                        cmd.setOnRequest(false);
                        cmd.setPreDelay(1500);
                        
                        this.addToQueue(cmd);
                   }
                }
            } catch(Exception ex){
                log.info("Problem occurred during loading Xcap from database", ex);
            }
        }
    }
    
    @Override
    public void run() {
        log.info("ServerCommandExecutor thread started");
        
        // daemon loop
        while(running==true){
            // at first sleep for a while and if interrupted, end this thread
            
            // High priority commands at first, all of them, fast
            this.handleHighPriority();
            
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                log.error("Thread sleep interrupted, has to exit, in queue left elements: " + commandQueue.size(), ex);
                running=false;
                break;
            }
            
            // High priority commands at first, all of them, fast
            this.handleHighPriority();
            
            // reload presence rules automatically
            this.reloadPresence();
            
            if (commandQueue.isEmpty()) continue;
            ServerMICommand cmd = commandQueue.poll();
            if (cmd==null) continue;
            
            // pre-execution delay
            long preDelay = cmd.getPreDelay();
            if (preDelay>0){
                long waitStart = System.currentTimeMillis();
                long counter = preDelay/10;
                try {
                    while((counter--) > 0){
                        this.handleHighPriority();
                        
                        Thread.sleep(10);
                        if ((System.currentTimeMillis() - waitStart) >= preDelay) break;
                    }
                } catch (InterruptedException ex) {
                    log.error("Thread sleep interrupted, has to exit, in queue left elements: " + commandQueue.size(), ex);
                    running=false;
                    break;
                }
            }
            
            this.executeCommand(cmd);
        }
    }
    
    /**
     * Handles content of high priority queue
     */
    protected void handleHighPriority(){
        while(this.hiPriorityCommandQueue.isEmpty()==false){
            ServerMICommand cmd = hiPriorityCommandQueue.poll();
            if (cmd==null) continue;
            
            this.executeCommand(cmd);
        }
    }
    
    public void executeCommand(ServerMICommand cmd){
        if (cmd==null) throw new NullPointerException("Command to execute cannot be null");
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
            log.error("Exception during executing process", b, ex);
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
    
    public void addToHiPriorityQueue(ServerMICommand command){
        if (command==null){
            throw new NullPointerException("Command to be inserted to command queue cannot be null");
        }
        
        log.debug("Adding high priority command to execute: " + command.toString());
        this.hiPriorityCommandQueue.add(command);
    }
    
    /**
     * Stops thread from running
     */
    public void stopRunning(){
        this.running = false;
    }
}
