/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.db;

import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.extra.ContactlistStatus;
import com.phoenix.db.opensips.Subscriber;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;

/**
 * User's contact list
 * @author ph4r05
 */
@Entity(name = "contactlist")
public class Contactlist implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;  
    
    // owner of this contact list
    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;
    
    // type of object
    @Enumerated(EnumType.STRING)
    private ContactlistObjType objType;
    // object in contact list - can be another subscriber
    @Column(nullable = false)
    @Embedded
    private ContactlistDstObj obj;
    
    // status of this contact list entry
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactlistStatus entryState;

    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.TIME)
    private Date dateCreated;
    @Temporal(javax.persistence.TemporalType.TIME)
    private Date dateLastEdit;
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Subscriber getOwner() {
        return owner;
    }

    public void setOwner(Subscriber owner) {
        this.owner = owner;
    }

    public ContactlistObjType getObjType() {
        return objType;
    }

    public void setObjType(ContactlistObjType objType) {
        this.objType = objType;
    }

    public ContactlistDstObj getObj() {
        return obj;
    }

    public void setObj(ContactlistDstObj obj) {
        this.obj = obj;
    }

    public ContactlistStatus getEntryState() {
        return entryState;
    }

    public void setEntryState(ContactlistStatus entryState) {
        this.entryState = entryState;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateLastEdit() {
        return dateLastEdit;
    }

    public void setDateLastEdit(Date dateLastEdit) {
        this.dateLastEdit = dateLastEdit;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + (this.id != null ? this.id.hashCode() : 0);
        hash = 97 * hash + (this.owner != null ? this.owner.hashCode() : 0);
        hash = 97 * hash + (this.objType != null ? this.objType.hashCode() : 0);
        hash = 97 * hash + (this.obj != null ? this.obj.hashCode() : 0);
        hash = 97 * hash + (this.entryState != null ? this.entryState.hashCode() : 0);
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
        final Contactlist other = (Contactlist) obj;
        if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
            return false;
        }
        if (this.owner != other.owner && (this.owner == null || !this.owner.equals(other.owner))) {
            return false;
        }
        if (this.objType != other.objType) {
            return false;
        }
        if (this.obj != other.obj && (this.obj == null || !this.obj.equals(other.obj))) {
            return false;
        }
        if (this.entryState != other.entryState) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Contactlist{" + "id=" + id + ", owner=" + owner + ", objType=" + objType + ", obj=" + obj + ", entryState=" + entryState + ", dateCreated=" + dateCreated + ", dateLastEdit=" + dateLastEdit + '}';
    }
}
