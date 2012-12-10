/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.db;
import com.phoenix.db.opensips.Subscriber;
import java.io.Serializable;
import javax.persistence.Column;
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
    private Subscriber intern_user;
    
    @ManyToOne
    @JoinColumn(name="dst_ext_usr_id")
    private RemoteUser extern_user;
    
    @ManyToOne
    @JoinColumn(name="dst_int_grp_id")
    private PhoenixGroup intern_group;

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
