/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Roster item for JSON transfer.
 * @author ph4r05
 */

@JsonAutoDetect
public class TransferRosterItem {
    @JsonProperty
    public String jid;
    
    @JsonProperty
    public String name;
    
    @JsonProperty
    public Integer subscription;
    
    @JsonProperty
    public Integer recvStatus;
    
    @JsonProperty
    public Integer askStatus;
    
    @JsonProperty
    public String groups;

    public TransferRosterItem() {
    }
    
    public String getJid() {
        return jid;
    }

    public void setJid(String jid) {
        this.jid = jid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSubscription() {
        return subscription;
    }

    public void setSubscription(Integer subscription) {
        this.subscription = subscription;
    }

    public Integer getRecvStatus() {
        return recvStatus;
    }

    public void setRecvStatus(Integer recvStatus) {
        this.recvStatus = recvStatus;
    }

    public Integer getAskStatus() {
        return askStatus;
    }

    public void setAskStatus(Integer askStatus) {
        this.askStatus = askStatus;
    }

    public String getGroups() {
        return groups;
    }

    public void setGroups(String groups) {
        this.groups = groups;
    }
}
