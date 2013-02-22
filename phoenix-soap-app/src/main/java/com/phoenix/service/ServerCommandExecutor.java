/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    // command queue here
    private ConcurrentLinkedQueue<ServerMICommand> commandQueue = new ConcurrentLinkedQueue<ServerMICommand>();
    private boolean running = true;
    
    public ServerCommandExecutor() {
        this.setName("ServerCommandExecutor");
    }
    
    @Override
    public void run() {
        log.info("ServerCommandExecutor thread started");
        while(running==true){
            
            // at first sleep for a while and if interrupted, end this thread
            try {
                Thread.sleep(1000);
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
            params.add(shell);
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
}
