/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.db;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;

/**
 * Collects statistics about basic SOAP calls. 
 * 
 * @author ph4r05
 */
@Entity
@Table(name = "usage_logs")
public class UsageLogs {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;  
    
    @Column(nullable = false, columnDefinition = "VARCHAR(24)")
    private String laction;
    
    @Column(nullable = true)
    private String lactionAux;
    
    @Column(nullable = false, columnDefinition = "VARCHAR(64)")
    private String luser;
    
    @Column(nullable = true, columnDefinition = "VARCHAR(24)")
    private String lip;
    
    @Column(nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date lwhen;

    public UsageLogs() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLaction() {
        return laction;
    }

    public void setLaction(String laction) {
        this.laction = laction;
    }

    public String getLuser() {
        return luser;
    }

    public void setLuser(String luser) {
        this.luser = luser;
    }

    public String getLip() {
        return lip;
    }

    public void setLip(String lip) {
        this.lip = lip;
    }

    public Date getLwhen() {
        return lwhen;
    }

    public void setLwhen(Date lwhen) {
        this.lwhen = lwhen;
    }

    public String getLactionAux() {
        return lactionAux;
    }

    public void setLactionAux(String lactionAux) {
        this.lactionAux = lactionAux;
    }
}
