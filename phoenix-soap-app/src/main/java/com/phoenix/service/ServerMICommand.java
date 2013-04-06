/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import java.util.LinkedList;
import java.util.List;

/**
 * OpenSips MI command class. 
 * 
 * Command can be in queue to be sent over FIFO to OpenSips.
 * @author ph4r05
 */
public class ServerMICommand {
    private String commandName;
    private List<String> parameters;
    private boolean onRequest=true;
    
    public ServerMICommand(String commandName) {
        this.commandName = commandName;
    }

    public ServerMICommand(String commandName, List<String> parameters) {
        this.commandName = commandName;
        this.parameters = parameters;
    }
    
    /**
     * Adds string parameter to the command, provides fluent interface.
     * @return 
     */
    public ServerMICommand addParameter(String param){
        this.getParameters().add(param);
        return this;
    }
    
    
    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public List<String> getParameters() {
        if (this.parameters == null){
            this.parameters = new LinkedList<String>();
        }
        
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public boolean isOnRequest() {
        return onRequest;
    }

    public void setOnRequest(boolean onRequest) {
        this.onRequest = onRequest;
    }

    @Override
    public String toString() {
        StringBuilder par = new StringBuilder();
        for(String s : getParameters()){
            par.append("'").append(s).append("', ");
        }
        
        return "ServerMICommand{" + "commandName=" + commandName + ", parameters=" + par.toString() + "; onRequest="+(onRequest ? "true":"false")+'}';
    }
    
}
