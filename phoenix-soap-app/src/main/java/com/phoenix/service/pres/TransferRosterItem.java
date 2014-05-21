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
}
