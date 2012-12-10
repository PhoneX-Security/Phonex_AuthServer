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
public class ContactlistDstObj implements Serializable{
    @ManyToOne
    @JoinColumn(name="int_usr_id")
    private Subscriber intern_user;
    
    @ManyToOne
    @JoinColumn(name="ext_usr_id")
    private RemoteUser extern_user;

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
}
