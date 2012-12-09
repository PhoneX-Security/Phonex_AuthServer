/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.db;

import com.phoenix.db.extra.WhitelistObjType;
import com.phoenix.soap.beans.WhitelistAction;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Temporal;

/**
 * Whitelist entity
 * @author ph4r05
 */
@Entity(name = "whitelist")
public class Whitelist implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;   
    
    // source type - group / user?
    @Enumerated(EnumType.STRING)
    private WhitelistObjType srcType;
    // source in whitelist
    @Column(nullable = false)
    private Long src;
    
    // subject type - group / internal user / external user
    @Enumerated(EnumType.STRING)
    private WhitelistObjType dstType;
    // subject of whitelist
    @Column(nullable = false)
    private Long dst;
    
    // whitelist action
    @Enumerated(EnumType.STRING)
    private WhitelistAction action;
    
    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.DATE)
    private Date dateCreated;
    @Temporal(javax.persistence.TemporalType.DATE)
    private Date dateLastEdit;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WhitelistObjType getSrcType() {
        return srcType;
    }

    public void setSrcType(WhitelistObjType srcType) {
        this.srcType = srcType;
    }

    public Long getSrc() {
        return src;
    }

    public void setSrc(Long src) {
        this.src = src;
    }

    public WhitelistObjType getDstType() {
        return dstType;
    }

    public void setDstType(WhitelistObjType dstType) {
        this.dstType = dstType;
    }

    public Long getDst() {
        return dst;
    }

    public void setDst(Long dst) {
        this.dst = dst;
    }

    public WhitelistAction getAction() {
        return action;
    }

    public void setAction(WhitelistAction action) {
        this.action = action;
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
        int hash = 5;
        hash = 47 * hash + (this.id != null ? this.id.hashCode() : 0);
        hash = 47 * hash + (this.srcType != null ? this.srcType.hashCode() : 0);
        hash = 47 * hash + (this.src != null ? this.src.hashCode() : 0);
        hash = 47 * hash + (this.dstType != null ? this.dstType.hashCode() : 0);
        hash = 47 * hash + (this.dst != null ? this.dst.hashCode() : 0);
        hash = 47 * hash + (this.action != null ? this.action.hashCode() : 0);
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
        final Whitelist other = (Whitelist) obj;
        if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
            return false;
        }
        if (this.srcType != other.srcType) {
            return false;
        }
        if (this.src != other.src && (this.src == null || !this.src.equals(other.src))) {
            return false;
        }
        if (this.dstType != other.dstType) {
            return false;
        }
        if (this.dst != other.dst && (this.dst == null || !this.dst.equals(other.dst))) {
            return false;
        }
        if (this.action != other.action) {
            return false;
        }
        return true;
    }
    
}
