/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.auth;

import org.springframework.security.core.GrantedAuthority;

/**
 * Basic user authority implementig interface
 * @author ph4r05
 */
public class UserAuthority implements GrantedAuthority{
    private String authority;
    
    @Override
    public String getAuthority() {
        return this.authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public UserAuthority(String authority) {
        this.authority = authority;
    }
}
