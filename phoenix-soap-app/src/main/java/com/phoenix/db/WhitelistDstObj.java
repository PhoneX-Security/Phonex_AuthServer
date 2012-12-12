/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.db;
import com.phoenix.db.opensips.Subscriber;
import java.io.Serializable;
import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

/**
 *
 * @author ph4r05
 */
@Embeddable 
public class WhitelistDstObj implements Serializable{
    @ManyToOne
    @JoinColumn(name="dst_int_usr_id")
    private Subscriber intern_user=null;
    
    @ManyToOne
    @JoinColumn(name="dst_ext_usr_id")
    private RemoteUser extern_user=null;
    
    @ManyToOne
    @JoinColumn(name="dst_int_grp_id")
    private PhoenixGroup intern_group=null;

    public WhitelistDstObj() {
    }

    public WhitelistDstObj(Subscriber intern_user) {
        this.intern_user = intern_user;
    }

    public WhitelistDstObj(RemoteUser extern_user) {
        this.extern_user = extern_user;
    }

    public WhitelistDstObj(PhoenixGroup intern_group) {
        this.intern_group = intern_group;
    }

    @Override
    public String toString() {
        return "WhitelistDstObj{" + "intern_user=" + intern_user + ", extern_user=" + extern_user + ", intern_group=" + intern_group + '}';
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.intern_user != null ? this.intern_user.hashCode() : 0);
        hash = 53 * hash + (this.extern_user != null ? this.extern_user.hashCode() : 0);
        hash = 53 * hash + (this.intern_group != null ? this.intern_group.hashCode() : 0);
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
        final WhitelistDstObj other = (WhitelistDstObj) obj;
        if (this.intern_user != other.intern_user && (this.intern_user == null || !this.intern_user.equals(other.intern_user))) {
            return false;
        }
        if (this.extern_user != other.extern_user && (this.extern_user == null || !this.extern_user.equals(other.extern_user))) {
            return false;
        }
        if (this.intern_group != other.intern_group && (this.intern_group == null || !this.intern_group.equals(other.intern_group))) {
            return false;
        }
        return true;
    }
    
    public Subscriber getIntern_user() {
        return intern_user;
    }

    public void setIntern_user(Subscriber intern_user) {
        this.intern_user = intern_user;
    }

    public RemoteUser getExtern_user() {
        return extern_user;
    }

    public void setExtern_user(RemoteUser extern_user) {
        this.extern_user = extern_user;
    }

    public PhoenixGroup getIntern_group() {
        return intern_group;
    }

    public void setIntern_group(PhoenixGroup intern_group) {
        this.intern_group = intern_group;
    }
}
