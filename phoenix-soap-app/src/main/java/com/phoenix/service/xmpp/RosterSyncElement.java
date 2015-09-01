package com.phoenix.service.xmpp;

import com.phoenix.db.Contactlist;
import com.phoenix.db.opensips.Subscriber;

import java.util.ArrayList;
import java.util.List;

/**
 * Holder class used for bulk roster synchronization.
 * Created by dusanklinec on 31.08.15.
 */
public class RosterSyncElement {
    /**
     * Owner of the roster.
     */
    private Subscriber owner;

    /**
     * Contact list array. Collection of contact list entries.
     */
    private ArrayList<Contactlist> clist;

    /**
     * Collection of corresponding internal contacts, as subscribers.
     */
    private ArrayList<Subscriber> clistSubs;

    public RosterSyncElement() {
    }

    public RosterSyncElement(Subscriber owner) {
        this.owner = owner;
    }

    public RosterSyncElement(Subscriber owner, ArrayList<Subscriber> clistSubs, ArrayList<Contactlist> clist) {
        this.owner = owner;
        this.clistSubs = clistSubs;
        this.clist = clist;
    }

    public Subscriber getOwner() {
        return owner;
    }

    public ArrayList<Subscriber> getClistSubs() {
        if (clistSubs == null){
            clistSubs = new ArrayList<Subscriber>();
        }
        return clistSubs;
    }

    public ArrayList<Contactlist> getClist() {
        if (clist == null){
            clist = new ArrayList<Contactlist>();
        }
        return clist;
    }
}
