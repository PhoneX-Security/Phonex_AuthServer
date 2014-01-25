/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.utils.SpringInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        
        // Set this object to the servlet context 
        // so as it would be reachable by Spring servlets.
        ServletContext context = sce.getServletContext();
        context.setAttribute(DaemonStarter.EXECUTOR_NAME, this);
        
        // Spring application context init
        log.info("Spring context manual initialization");
        SpringInitializer springInitializer = new SpringInitializer();
        
        // Set current active profile, otherwise no profile will be loaded -> no database classes.
        // Profile is determined programatically by environment inspection and Database
        // conenction is choosen based on the active profile.
        System.setProperty("spring.profiles.active", springInitializer.getCurrentProfile());
        
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
    
    /**
     * Obtains running DaemonStarter from the ServletRequest
     * @param request
     * @return 
     */
    public static DaemonStarter getFromContext(HttpServletRequest request){
        return getFromContext(request.getServletContext());
    }
    
    /**
     * Obtains running DaemonStarter from the ServletContext.
     * @param ctxt
     * @return 
     */
    public static DaemonStarter getFromContext(ServletContext ctxt){
        return (DaemonStarter) ctxt.getAttribute(DaemonStarter.EXECUTOR_NAME);
    }

    public ServerCommandExecutor getCexecutor() {
        return cexecutor;
    }
}

