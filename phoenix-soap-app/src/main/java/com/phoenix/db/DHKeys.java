/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import java.util.Arrays;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import org.hibernate.annotations.Index;

/**
 * User's Diffie-Hellman offline keys for file transfer server cache.
 * @author ph4r05
 */
@Entity(name = "dhkeys")
public class DHKeys {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;  
    
    // owner of this dh key
    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;
    
    @Index(name="userIndex")
    @Column(nullable = false, columnDefinition = "VARCHAR(128)")
    private String forUser;
    
    @Lob
    @Column(nullable = false)
    private byte[] aAncBlock;
    
    @Lob
    @Column(nullable = false)
    private byte[] sAncBlock;
    
    @Index(name="nonce1Index")
    @Column(nullable = false, columnDefinition = "VARCHAR(24)")
    private String nonce1;
    
    @Index(name="nonce2Index")
    @Column(nullable = false, columnDefinition = "VARCHAR(24)")
    private String nonce2;
    
    @Lob
    @Column(nullable = false)
    private byte[] sig1;
    
    @Lob
    @Column(nullable = false)
    private byte[] sig2;
    
    @Column(nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date created;
    
    @Column(nullable = true)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date whenUsed;
    
    @Column(nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date expires;
    
    @Index(name="usedIndex")
    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean used=false;
    
    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean expired=false;

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

    public String getForUser() {
        return forUser;
    }

    public void setForUser(String forUser) {
        this.forUser = forUser;
    }

    public byte[] getaAncBlock() {
        return aAncBlock;
    }

    public void setaAncBlock(byte[] aAncBlock) {
        this.aAncBlock = aAncBlock;
    }

    public byte[] getsAncBlock() {
        return sAncBlock;
    }

    public void setsAncBlock(byte[] sAncBlock) {
        this.sAncBlock = sAncBlock;
    }

    public String getNonce1() {
        return nonce1;
    }

    public void setNonce1(String nonce1) {
        this.nonce1 = nonce1;
    }

    public String getNonce2() {
        return nonce2;
    }

    public void setNonce2(String nonce2) {
        this.nonce2 = nonce2;
    }

    public byte[] getSig1() {
        return sig1;
    }

    public void setSig1(byte[] sig1) {
        this.sig1 = sig1;
    }

    public byte[] getSig2() {
        return sig2;
    }

    public void setSig2(byte[] sig2) {
        this.sig2 = sig2;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getExpires() {
        return expires;
    }

    public void setExpires(Date expires) {
        this.expires = expires;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + (this.id != null ? this.id.hashCode() : 0);
        hash = 29 * hash + (this.owner != null ? this.owner.hashCode() : 0);
        hash = 29 * hash + (this.forUser != null ? this.forUser.hashCode() : 0);
        hash = 29 * hash + Arrays.hashCode(this.aAncBlock);
        hash = 29 * hash + Arrays.hashCode(this.sAncBlock);
        hash = 29 * hash + (this.nonce1 != null ? this.nonce1.hashCode() : 0);
        hash = 29 * hash + (this.nonce2 != null ? this.nonce2.hashCode() : 0);
        hash = 29 * hash + Arrays.hashCode(this.sig1);
        hash = 29 * hash + Arrays.hashCode(this.sig2);
        hash = 29 * hash + (this.created != null ? this.created.hashCode() : 0);
        hash = 29 * hash + (this.expires != null ? this.expires.hashCode() : 0);
        hash = 29 * hash + (this.used ? 1 : 0);
        hash = 29 * hash + (this.expired ? 1 : 0);
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
        final DHKeys other = (DHKeys) obj;
        if (this.id != other.id && (this.id == null || !this.id.equals(other.id))) {
            return false;
        }
        if (this.owner != other.owner && (this.owner == null || !this.owner.equals(other.owner))) {
            return false;
        }
        if ((this.forUser == null) ? (other.forUser != null) : !this.forUser.equals(other.forUser)) {
            return false;
        }
        if (!Arrays.equals(this.aAncBlock, other.aAncBlock)) {
            return false;
        }
        if (!Arrays.equals(this.sAncBlock, other.sAncBlock)) {
            return false;
        }
        if ((this.nonce1 == null) ? (other.nonce1 != null) : !this.nonce1.equals(other.nonce1)) {
            return false;
        }
        if ((this.nonce2 == null) ? (other.nonce2 != null) : !this.nonce2.equals(other.nonce2)) {
            return false;
        }
        if (!Arrays.equals(this.sig1, other.sig1)) {
            return false;
        }
        if (!Arrays.equals(this.sig2, other.sig2)) {
            return false;
        }
        if (this.created != other.created && (this.created == null || !this.created.equals(other.created))) {
            return false;
        }
        if (this.expires != other.expires && (this.expires == null || !this.expires.equals(other.expires))) {
            return false;
        }
        if (this.used != other.used) {
            return false;
        }
        if (this.expired != other.expired) {
            return false;
        }
        return true;
    }
    
    
}
