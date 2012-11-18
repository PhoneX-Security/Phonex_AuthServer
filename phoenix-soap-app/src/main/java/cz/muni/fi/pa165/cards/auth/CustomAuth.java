package cz.muni.fi.pa165.cards.auth;

import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.managers.UserManager;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.encoding.MessageDigestPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Repository;

/**
 * Custom authentication provider bean. Alternative to built-in auth bean for more flexibility.
 * @author ph4r05
 */
@Repository
public class CustomAuth implements AuthenticationProvider{
    @PersistenceContext
    protected EntityManager em;
    
    private static final Logger log = LoggerFactory.getLogger(CustomAuth.class);

    @Autowired
    protected UserManager userManager;
    
    @Autowired
    protected EntityManagerFactory entityManagerFactory;
    
    @Autowired
    protected MessageDigestPasswordEncoder passwordEncoder;

    @Autowired
    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }

    @Autowired
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void setPasswordEncoder(MessageDigestPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    public void setEm(EntityManager em) {
        this.em = em;
    }
 
    protected String getProviderName() {
        return "DatabaseUsers";
    }
 
    protected Authentication doInternalAuthenticate(Authentication authentication) {
        // All your user authentication needs
        String principal = (String) authentication.getPrincipal();
        String password = (String) authentication.getCredentials();
        
        User user = findUser(principal);
        if (user==null){
            throw new BadCredentialsException("Username/Password does not match for " + principal);
        }
        
        // user exists here, check password (just plaintext for this moment
        if (password.equals(user.getPassword())){
            System.out.println("Authenticating user: " + principal);
            return new UsernamePasswordAuthenticationToken(
                    principal,
                    password,
                    authentication.getAuthorities());
        }
        throw new BadCredentialsException("Username/Password does not match for " + principal);
    }

    
    @Override
    public boolean supports(Class authentication) {
        return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String userName = authentication.getName();
        log.debug("Trying to authenticate user '{}' via {}", userName, getProviderName());
        try {
            // test entity manager
            if (this.em == null){
                log.debug("Entity manager is not properly injected. "
                        + "Generating from entity manager factory");
                System.out.println("Err - em=null");
                if (this.entityManagerFactory!=null){
                    this.em = this.entityManagerFactory.createEntityManager();
                    this.userManager.setEm(this.em);
                    System.out.println("EM was set!");
                } else {
                    System.out.println("Err - emf=null!!!");
                    log.error("EntityManagerFactory is not injected.");
                    throw new NullPointerException("EntityManagerFactory injection fails!");
                }

                if (this.em == null){
                    throw new NullPointerException("EntityManager is not injected!");
                }
            }
        
            authentication = doInternalAuthenticate(authentication);
        } catch (AuthenticationException e) {
            log.debug("Failed to authenticate user {} via {}: {}",
                    new Object[]{userName, getProviderName(), e.getMessage()});
            throw e;
        } catch (Exception e) {
            String message = "Unexpected exception in " + getProviderName() + " authentication:";
            log.error(message, e);
            throw new AuthenticationServiceException(message, e);
        }
        if (!authentication.isAuthenticated()) {
            return authentication;
        }

        // user authenticated via ldap
        log.debug("'{}' authenticated successfully by {}.", userName, getProviderName());        
        User user = findUser(userName);
        if (user==null){
            throw new NullPointerException("User with login name '" + userName + "' was not found in database");
        }
        
        UserAuthority authorityObj = new UserAuthority(user.getAuthority());
        LinkedList<UserAuthority> authorities = new LinkedList<UserAuthority>();
        authorities.add(authorityObj);
        
        // create new authentication response containing the user and it's authorities
        UsernamePasswordAuthenticationToken simpleUserAuthentication =
                new UsernamePasswordAuthenticationToken(user, authentication.getCredentials(), authorities);
        return simpleUserAuthentication;
    }

    /**
     * Find or create a default user and save to the database. This method is called when a user
     * successfully authenticated via LDAP. If the user doesn't exist in the internal user database
     * it will create it.
     *
     * @param userName The user name to find or create
     * @return A new or found SimpleUser (never null)
     */
    protected User findUser(String userName) {
        User user = null;
        try {
            // check user manager injection
            if (this.userManager == null){
                throw new NullPointerException("UserManager injection was not "
                        + "successfull, canot load user data for auth.");
            }
            
            user = this.userManager.getUserByLogin(userName);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            log.debug(String.format("Creating new user '%s' for XXXX...", userName));
            
            throw new IllegalStateException("Cannot load user '" + userName +"'"
                    + ". Exception was thrown", e);
        }
        return user;
    }
}
