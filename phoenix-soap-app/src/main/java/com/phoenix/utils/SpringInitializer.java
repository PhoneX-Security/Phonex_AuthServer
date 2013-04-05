/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.utils;

import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Spring ApplicationContext profile initializer - depending on host name.
 * 
 * taken from http://blog.chariotsolutions.com/2012/01/spring-31-cool-new-features.html
 * @author ph4r05
 */
public class SpringInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(SpringInitializer.class);

    public String getCurrentProfile(){
        String hostname = "";
        String profile = "dev";
    
        try {
            hostname = InetAddress.getLocalHost().getHostName();
            hostname = hostname.toLowerCase();
        } catch(Exception e){
            logger.warn("Cannot determine hostname", e);
            return profile;
        }
        
        if (hostname.contains("net-wings") || hostname.contains("phoenix")) {
            logger.info("Running in production environment, hostname: " + hostname);
            profile="prod";
        } else {
            logger.info("Application running local - development environment, hostname: " + hostname);
            profile="dev";
        }
        
        return profile;
    }
    
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.getEnvironment().setActiveProfiles(this.getCurrentProfile());
        applicationContext.refresh();
    }
}
