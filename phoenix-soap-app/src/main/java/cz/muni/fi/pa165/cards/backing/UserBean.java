/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.backing;

import cz.muni.fi.pa165.cards.auth.MyUserDetails;
import cz.muni.fi.pa165.cards.db.User;
import cz.muni.fi.pa165.cards.helpers.LocUtil;
import cz.muni.fi.pa165.cards.managers.UserManager;
import java.io.IOException;
import java.io.Serializable;
import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.validation.constraints.AssertTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.encoding.MessageDigestPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 
 * @author ph4r05
 */
public class UserBean implements Serializable {
	private static final long serialVersionUID = 112812352;
	private static final Logger log = LoggerFactory
			.getLogger(CategoryBean.class);

	@Autowired
	protected transient UserManager userManager;
        
        @Autowired
        protected MessageDigestPasswordEncoder passwordEncoder;

    

	@PersistenceContext
	protected EntityManager em;
        
        private User user;
        private String pass="";
        private String passconf="";

        
    public String getPass() {        
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getPassconf() {
        return passconf;
    }

    public void setPassconf(String passconf) {
        this.passconf = passconf;
    }
    public MessageDigestPasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    public void setPasswordEncoder(MessageDigestPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
        
    @AssertTrue //annotation doesnt work dont know why
    public boolean isPasswordsEquals() {        
        return this.passconf.equals(this.pass);
    }        
    @AssertTrue //annotation doesnt work dont know why
    public boolean isEmailUnique(){
        User u = this.userManager.getUserByEmail(this.user.getEmail());
        return (u == null || u.getLogin().equals(user.getLogin()));        
    }
        
        
        /**
        * updates info about user
        */
        public String saveUser(){
            //validation
            if (this.user == null) {
            System.out.println("Error creating new card");
            return "ERROR";
            }
            
            
            //manual validation, because asserts dont work            
            if(!isEmailUnique()){                
                LocUtil.sendMessage(null, FacesMessage.SEVERITY_ERROR, LocUtil.getMessageFromDefault("EmailInDatabase"), "");                                            
                return "ERROR";
            }   
            //update
            userManager.updateUser(user); 
            LocUtil.sendMessage(null, FacesMessage.SEVERITY_INFO, LocUtil.getMessageFromDefault("UserUpdated"), "");                                            
            return "USER_UPDATED";
        }
        
        
        /**
         * saves new password to DB
         */
        public String savePassword(){
            if(!isPasswordsEquals()){
                LocUtil.sendMessage("changePass:password", FacesMessage.SEVERITY_ERROR, LocUtil.getMessageFromDefault("PasswordsNotEquals"), "");                                
                return "ERROR";
            }
            
            String encodedPass = passwordEncoder.encodePassword(pass, this.user.getLogin());
            user.setPassword(encodedPass);
            userManager.updateUser(user);   
            LocUtil.sendMessage("changePass:password", FacesMessage.SEVERITY_INFO, LocUtil.getMessageFromDefault("PasswordChanged"), "");                                
            return "USER_PASSWORD_CHANGED";            
        }
        
        public User getUser() {
            user = getLoggedUser();
            return user;
        }
        

	public void setUserManager(UserManager userManager) {
		this.userManager = userManager;
	}

	public void setEm(EntityManager em) {
		this.em = em;
	}

	/**
	 * Returns TRUE if is newUser logged in
	 * 
	 * @return
	 */
	public boolean isLoggedIn() {		
		User user = this.getLoggedUser();		
		return (user != null);
	}

	/**
	 * Get actual logged newUser name
	 * 
	 * @return
	 */
	public String getLoggedUserName() {
		String name = "N/A";

		// try to load directly from newUser
		// TODO: need method to get complete newUser name
		// for ex. Ing. Joseph Carrot
		User loggedUser = this.getLoggedUser();
		if (loggedUser != null) {
			return loggedUser.getFirstName() + " " + loggedUser.getSurName();
		}

		// if here, previous method failed, return just login name
		Authentication auth = SecurityContextHolder.getContext()
				.getAuthentication();
		if (auth != null) {
			return auth.getName(); // get logged in username
		}

		// every method failed, probably no newUser logged in
		return "";
	}

	/**
	 * Returns logged newUser instance if it is possible, null otherwise
	 * 
	 * @return
	 */
	public User getLoggedUser() {
		/*if (this.isLoggedIn() == false) {
			return null;
		}*/

		Authentication auth = SecurityContextHolder.getContext()
				.getAuthentication();
		Object principal = auth.getPrincipal();
		if (MyUserDetails.class.isAssignableFrom(principal.getClass())) {
			MyUserDetails mudetails = (MyUserDetails) principal;
			return mudetails.getUser();
		}

		if (User.class.isAssignableFrom(principal.getClass())) {
			return (User) principal;
		}

		return null;
	}
        
     
        //managed properties for the login page, username/password/etc...

        // This is the action method called when the user clicks the "login" button
        public String doLogin() throws IOException, ServletException
        {
            ExternalContext context = FacesContext.getCurrentInstance().getExternalContext();

            RequestDispatcher dispatcher = ((ServletRequest) context.getRequest())
                     .getRequestDispatcher("/j_spring_security_check");

            dispatcher.forward((ServletRequest) context.getRequest(),
                    (ServletResponse) context.getResponse());

            FacesContext.getCurrentInstance().responseComplete();
            // It's OK to return null here because Faces is just going to exit.
            return null;
        }
        

}
