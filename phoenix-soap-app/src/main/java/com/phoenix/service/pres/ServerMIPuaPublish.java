/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import com.phoenix.service.ServerMICommand;
import java.util.List;

/**
 * Server MI command to manually publish new information about some account.
 * {@see http://www.opensips.org/html/docs/modules/devel/pua_mi.html}.
 * @author ph4r05
 */
public class ServerMIPuaPublish extends ServerMICommand {
    private static final String COMMAND="pua_publish";
    private static final String PRESENCE="presence";
    private static final String CONTENT_TYPE="application/pidf+xml";
    private static final String SIP="sip:";
    
    public ServerMIPuaPublish() {
        super(COMMAND);
    }
    
    public ServerMIPuaPublish(String sip, int expires, String pidf) {
        super(COMMAND);
        
        if (sip==null || sip.isEmpty()){
            throw new IllegalArgumentException("SIP cannot be null");
        }
        
        // Make sure SIP address does not start with "sip:"
        if (sip.startsWith(SIP)){
            sip = sip.replace(SIP, "");
        }
        
        // Make sure PIDF is only on 1 line - remove new lines and tabs
        if (pidf==null) pidf = "";
        String newPidf = pidf.replace("\n", "").replace("\r", "").replace("\t", "");
        
        this.addParameter(SIP + sip);
        this.addParameter(Integer.toString(expires));
        this.addParameter(PRESENCE);
        this.addParameter(CONTENT_TYPE);
        this.addParameter(".");
        this.addParameter(".");
        this.addParameter(newPidf);
    }

    private ServerMIPuaPublish(String commandName) {
        super(COMMAND);
    }

    private ServerMIPuaPublish(String commandName, List<String> parameters) {
        super(COMMAND, parameters);
    }
}
