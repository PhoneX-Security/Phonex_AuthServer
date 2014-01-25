/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.service.pres;

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
}
