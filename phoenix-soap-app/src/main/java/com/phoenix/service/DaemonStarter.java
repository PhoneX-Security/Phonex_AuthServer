/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Daemon starter class - takes care about background operations in Tomcat.
 * @author ph4r05
 */
@Service
public class DaemonStarter implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(DaemonStarter.class);
    public static final String EXECUTOR_NAME = "DAEMON_STARTER_EXECUTOR";
    
    private final ServerCommandExecutor cexecutor = new ServerCommandExecutor();
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Context initialized, starting server communication thread");
        
        ServletContext context = sce.getServletContext();
        context.setAttribute(DaemonStarter.EXECUTOR_NAME, this);
        
        if (cexecutor.isAlive()){
            log.warn("Cexecutor is already alive, nothing to start");
        } else {
            log.info("Starting command executor thread");
            cexecutor.onContextInitialized(sce);
            cexecutor.setDaemon(true);
            cexecutor.start();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // you could notify your thread you're shutting down if 
        // you need it to clean up after itself
        
        log.info("Context destroy now... Disable all background threads");
        if (cexecutor!=null){
            log.info("Command executor was asked to stop running.");
            cexecutor.stopRunning();
        }
    }

    public ServerCommandExecutor getCexecutor() {
        return cexecutor;
    }
}

