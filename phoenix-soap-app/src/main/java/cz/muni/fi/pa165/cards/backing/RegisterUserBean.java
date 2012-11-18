/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.backing;

import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.helpers.LocUtil;
import cz.muni.fi.pa165.cards.managers.UserManager;
import java.io.Serializable;
import java.util.Date;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.resource.spi.work.SecurityContext;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.richfaces.validator.FacesBeanValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.encoding.MessageDigestPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 *
 * @author miro
 */
public class RegisterUserBean implements Serializable {
    private static final long serialVersionUID = 11415352;
    private static final Logger log = LoggerFactory.getLogger(CategoryBean.class);    
    
   
    
    @AssertTrue //annotation doesnt work dont know why
    public boolean isPasswordsEquals() {                  
        return this.confpass.equals(this.pass);
    }    
    @AssertTrue //annotation doesnt work dont know why
    public boolean isLoginUnique(){
        return this.userManager.getUserByLogin(this.newUser.getLogin())==null;
    }
    @AssertTrue //annotation doesnt work dont know why
    public boolean isEmailUnique(){
        return this.userManager.getUserByEmail(this.newUser.getEmail())==null;
        
    }
    
    @Autowired
    protected MessageDigestPasswordEncoder passwordEncoder;
    
    @Autowired
    protected transient UserManager userManager;
    
    @Autowired
    protected AuthenticationManager authenticationManager;

    @PersistenceContext
    protected EntityManager em;
    
    protected User newUser = null;  
    protected String confpass;    
    protected String pass;
    
    @PostConstruct
    public void init(){    
        this.resetForNewRegistration();        
    }    

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }
    
    public String getConfpass() {
        return confpass;
    }

    public void setConfpass(String confpass) {
        this.confpass = confpass;
    }
    
     
    
    

    public AuthenticationManager getAuthenticationManager() {
        return authenticationManager;
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public MessageDigestPasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    public void setPasswordEncoder(MessageDigestPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    
    public User getNewUser() {
        return newUser;
    }

    public void setNewUser(User newUser) {
        this.newUser = newUser;
    }

    public void setUserManager(UserManager userManager) {
        this.userManager = userManager;
    }
    
    public void setEm(EntityManager em) {
        this.em = em;
    }    
    
    
    
    /**
     * initialize components before new registration, 
     * call after new newUser is created or during initialization of class
     */
    private void resetForNewRegistration(){
        this.newUser = new User();
        this.newUser.setAuthority("ROLE_USER");        
        this.confpass="";
        this.pass="";
        
    }
    
    
    public String registerUser(){
        if (this.newUser == null) {
            System.out.println("Error creating new card");
            return "ERROR";
        }
        //manual validation, because asserts dont work
        if(!isLoginUnique()){
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, LocUtil.getMessageFromDefault("LoginNotUnique"),""));
            return "ERROR";
        }
        if(!isPasswordsEquals()){
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, LocUtil.getMessageFromDefault("PasswordsNotEquals"), ""));
            return "ERROR";
        }
        if(!isEmailUnique()){
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, LocUtil.getMessageFromDefault("EmailInDatabase"), ""));
            return "ERROR";
        }        
              
        //add new card to db
        try{                                    
            //salt with username/login
            String encodedPass = passwordEncoder.encodePassword(pass, this.newUser.getLogin());                      
            this.newUser.setPassword(encodedPass);
            this.newUser.setRegistrationDate(new Date());            
            this.userManager.addUser(this.newUser);                
            //autologin after registration
            authenticateUserAndSetSession(newUser.getLogin(),this.pass,getRequest());
            
        } catch(Exception e){           
            e.printStackTrace(System.out);
            System.out.println("EXCEPTION");
            return "EXCEPTION";
        } finally{
            this.resetForNewRegistration();            
        }
        
        log.info("Register new user");           
        //add info message
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "User successfuly registered.", "User successfuly registered."));        
        return "USER_REGISTERED";
    }
    
    /**
     * used for login after registration
     */
    private void authenticateUserAndSetSession(String login, String password, HttpServletRequest request){
                 if (this.authenticationManager != null){
                  UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(login, password);

                  // generate session if one doesn't exist
                  request.getSession();

                  token.setDetails(new WebAuthenticationDetails(request));
                  Authentication authenticatedUser = authenticationManager.authenticate(token);

                  SecurityContextHolder.getContext().setAuthentication(authenticatedUser);
                  //SecurityContext context = (SecurityContext) SecurityContextHolder.getContext();
                 }
                 else {
                     log.info("No autentification manager found. ");
                 }
        }
    
        public static HttpServletRequest getRequest(){
                HttpServletRequest request = (HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest();
                if (request == null){
                        throw new RuntimeException("Sorry. Got a null request from faces context");
                }
                return request;
        }
    
    
}
