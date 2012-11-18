/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.muni.fi.pa165.cards.auth;

import cz.muni.fi.pa165.cards.db.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * My user details extending interface. Helps to obtain direct reference to logged user.
 * @author ph4r05
 */
public interface MyUserDetails extends UserDetails {
    public User getUser();
}
