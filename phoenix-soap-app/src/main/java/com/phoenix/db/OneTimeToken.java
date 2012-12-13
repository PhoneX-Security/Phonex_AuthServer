/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.db;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.hibernate.annotations.Index;

/**
 * Storage for one time tokens for user REALM auth
 * @author ph4r05
 */
@Entity(name = "oneTimeToken") 
public class OneTimeToken implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    @Index(name="dateIdx")
    private Date notValidAfter;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    @Index(name="dateInsIdx")
    private Date inserted;
    
    @Column(nullable = false)
    @Index(name="usrIdx")
    private String userSIP;
    
    @Column(nullable = true)
    private String fprint;
    
    @Column(nullable = false)
    @Index(name="usrTokenIdx")
    private String userToken;
    
    @Column(nullable = false, unique=true)
    private String token;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getNotValidAfter() {
        return notValidAfter;
    }

    public void setNotValidAfter(Date notValidAfter) {
        this.notValidAfter = notValidAfter;
    }

    public String getUserSIP() {
        return userSIP;
    }

    public void setUserSIP(String userSIP) {
        this.userSIP = userSIP;
    }

    public String getFprint() {
        return fprint;
    }

    public void setFprint(String fprint) {
        this.fprint = fprint;
    }

    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Date getInserted() {
        return inserted;
    }

    public void setInserted(Date inserted) {
        this.inserted = inserted;
    }

    @Override
    public String toString() {
        return "OneTimeToken{" + "id=" + id + ", notValidAfter=" + notValidAfter + ", inserted=" + inserted + ", userSIP=" + userSIP + ", fprint=" + fprint + ", userToken=" + userToken + ", token=" + token + '}';
    }
}
