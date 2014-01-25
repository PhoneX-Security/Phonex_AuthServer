/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

import com.phoenix.utils.JSONHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * POJO for pushing events from server to the user in presence texts.
 * @author ph4r05
 */
public class PresenceEvents {
    private int version=1;
    private List<String> files = new ArrayList<String>();

    public PresenceEvents() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }
    
    /**
     * Converts this POJO to JSON representation.
     * @return 
     * @throws java.io.IOException 
     */
    public String toJSON() throws IOException{
        return JSONHelper.obj2JSON(this);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + this.version;
        hash = 31 * hash + (this.files != null ? this.files.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PresenceEvents other = (PresenceEvents) obj;
        if (this.version != other.version) {
            return false;
        }
        if (this.files != other.files && (this.files == null || !this.files.equals(other.files))) {
            return false;
        }
        return true;
    }
}
