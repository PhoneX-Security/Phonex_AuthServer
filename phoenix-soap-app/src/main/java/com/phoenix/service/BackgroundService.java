/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.ServletContextEvent;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Base background service running as a separate thread.
 * @author ph4r05
 */
@Service
@Repository
public class BackgroundService {
    private static final Logger log = LoggerFactory.getLogger(BackgroundService.class);
     
    // Spring application context - dependency injector
    protected ApplicationContext appContext = null;
    
    @Autowired
    protected SessionFactory sessionFactory;
    
    @PersistenceContext
    protected EntityManager em;
    
    /**
     * Called on servlet context initialized event.
     * SpringContext is loaded and autowiring is performed. 
     * 
     * @param sce 
     */
    public void onContextInitialized(ServletContextEvent sce){
        // Obtain Spring web application context defined for this servlet context.
        final WebApplicationContext wAppContext = WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext());
        final ApplicationContext pAppContext = wAppContext.getParent();
        this.appContext = pAppContext!=null ? pAppContext : wAppContext;
        
        // Perform autowiring on this bean instructed by annotations.
        this.appContext.getAutowireCapableBeanFactory().autowireBean(this);
        log.info("Autowiring call finished. "
                + " EM=" + (em==null ? "null" : "OK") + "; open=" + (em==null ? "null" : em.isOpen())
                + " SF=" + (sessionFactory==null ? "null" : "OK"));
        log.info("All dependencies initialized");
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

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }
}
