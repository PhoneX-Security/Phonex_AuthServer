/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import com.phoenix.service.ServerMICommand;
import java.util.List;

/**
 * OpenSips MI command for refreshing watcher list - notification that XCAP 
 * policy has changed to the server.
 *
 * @deprecated
 * @author ph4r05
 */
public final class ServerMIRefreshWatchers extends ServerMICommand {
    private static final String COMMAND="refreshWatchers";
    private static final String PRESENCE="presence";
    private static final String SIP="sip:";
    
    public ServerMIRefreshWatchers() {
        super(COMMAND);
    }
    
    public ServerMIRefreshWatchers(String sip, int flag) {
        super(COMMAND);
        
        if (sip==null || sip.isEmpty()){
            throw new IllegalArgumentException("SIP cannot be null");
        }
        
        // Make sure SIP address does not start with "sip:"
        if (sip.startsWith(SIP)){
            sip = sip.replace(SIP, "");
        }
        
        this.addParameter(SIP + sip);
        this.addParameter(PRESENCE);
        this.addParameter(Integer.toString(flag));
    }

    private ServerMIRefreshWatchers(String commandName) {
        super(COMMAND);
    }

    private ServerMIRefreshWatchers(String commandName, List<String> parameters) {
        super(COMMAND, parameters);
    }
}
