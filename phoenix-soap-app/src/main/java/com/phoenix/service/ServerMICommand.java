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
public class ServerMICommand implements Comparable<Object> {
    private String commandName;
    private List<String> parameters;
    private boolean onRequest=true;
    private long preDelay=0;
    private long postDelay=0;
    private long priority = 100;
    private long timeAdded = 0;
    
    public ServerMICommand(String commandName) {
        this.commandName = commandName;
    }

    public ServerMICommand(String commandName, List<String> parameters) {
        this.commandName = commandName;
        this.parameters = parameters;
    }
    
    /**
     * Adds string parameter to the command, provides fluent interface.
     * @param param
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

    public long getPreDelay() {
        return preDelay;
    }

    public void setPreDelay(long preDelay) {
        this.preDelay = preDelay;
    }

    public long getPostDelay() {
        return postDelay;
    }

    public void setPostDelay(long postDelay) {
        this.postDelay = postDelay;
    }

    public long getPriority() {
        return priority;
    }

    public void setPriority(long priority) {
        this.priority = priority;
    }

    public long getTimeAdded() {
        return timeAdded;
    }

    public void setTimeAdded(long timeAdded) {
        this.timeAdded = timeAdded;
    }

    @Override
    public String toString() {
        StringBuilder par = new StringBuilder();
        for(String s : getParameters()){
            par.append("'").append(s).append("', ");
        }
        
        return "ServerMICommand{" + "commandName=" + commandName + ", parameters=" + par.toString() + ", onRequest=" + onRequest + ", preDelay=" + preDelay + ", postDelay=" + postDelay + ", priority=" + priority + ", timeAdded=" + timeAdded + '}';
    }

    @Override
    public int compareTo(Object o) {
        if ((o instanceof ServerMICommand)==false) return -1;
        final ServerMICommand cmd = (ServerMICommand) o;
        
        if (priority == cmd.getPriority()){
            if (timeAdded == cmd.getTimeAdded()) return 0;
            return timeAdded < cmd.getTimeAdded() ? -1 : 1;
        } else {
            return priority < cmd.getPriority() ? -1 : 1;
        }
    }
}
