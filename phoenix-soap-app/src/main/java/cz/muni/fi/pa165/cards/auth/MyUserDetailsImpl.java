package cz.muni.fi.pa165.cards.auth;

import cz.muni.fi.pa165.cards.auth.MyUserDetails;
import cz.muni.fi.pa165.cards.db.User;
import java.util.Collection;
import java.util.LinkedList;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Structure to hold user details for auth purposes
 * @author ph4r05
 */
public class MyUserDetailsImpl implements MyUserDetails {
    private User user;

    @Override
    public User getUser() {
        this.checkUser();
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public MyUserDetailsImpl(User user) {
        this.user = user;
    }
    
    protected void checkUser(){
        if (this.user==null){
            throw new NullPointerException("No user passed here");
        }
    }
    
    @Override
    public Collection<UserAuthority> getAuthorities() {
        this.checkUser();
        UserAuthority authorityObj = new UserAuthority(user.getAuthority());
        LinkedList<UserAuthority> authorities = new LinkedList<UserAuthority>();
        authorities.add(authorityObj);        
        return authorities;
    }

    @Override
    public String getPassword() {
        this.checkUser();
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        this.checkUser();
        return user.getLogin();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
