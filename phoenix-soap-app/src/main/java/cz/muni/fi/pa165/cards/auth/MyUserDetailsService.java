/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.auth;

import cz.muni.fi.pa165.cards.auth.MyUserDetailsImpl;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.managers.UserManager;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

/**
 * Retrieves user details data
 * @author ph4r05
 */
@Repository
public class MyUserDetailsService implements UserDetailsService{

    @PersistenceContext
    protected EntityManager em;
    
    private static final Logger log = LoggerFactory.getLogger(MyUserDetailsService.class);

    @Autowired
    protected UserManager userManager;
    
    @Autowired
    protected EntityManagerFactory entityManagerFactory;

    @Autowired
    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    @Autowired
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }
    
    public void setEm(EntityManager em) {
        this.em = em;
    }
    
    protected void checkConnection(){
        // test entity manager
        if (this.em == null){
            log.debug("Entity manager is not properly injected. "
                    + "Generating from entity manager factory");
            
            if (this.entityManagerFactory!=null){
                this.em = this.entityManagerFactory.createEntityManager();
                this.userManager.setEm(this.em);
            } else {
                log.error("EntityManagerFactory is not injected.");
                throw new NullPointerException("EntityManagerFactory injection fails!");
            }
            
            if (this.em == null){
                throw new NullPointerException("EntityManager is not injected!");
            }
        }
    }
    
    @Override
    public UserDetails loadUserByUsername(String string) throws UsernameNotFoundException {
        // check em
        this.checkConnection();
        
        // now we have connection
        User user = this.userManager.getUserByLogin(string);
        if (user==null){
            throw new UsernameNotFoundException("Username: " + string + " not found");
        }
        
        System.out.println(user.toString());
        
        MyUserDetailsImpl userDetails = new MyUserDetailsImpl(user);
        return userDetails;
    }
    
}
