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
import javax.persistence.Table;
import javax.persistence.Temporal;
import org.hibernate.annotations.Index;

/**
 * User's Diffie-Hellman offline keys for file transfer server cache.
 * @author ph4r05
 */
@Entity
@Table(name = "dhkeys")
public class DHKeys {
    /*public static final String FIELD_ID = "id";
    public static final String FIELD_OWNER = "subscriber_id";
    public static final String FIELD_FOR_USER = "for_user";*/
    
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
    private byte[] aEncBlock;
    
    @Lob
    @Column(nullable = false)
    private byte[] sEncBlock;
    
    @Index(name="nonce1Index")
    @Column(nullable = false, columnDefinition = "VARCHAR(44)", unique = true)
    private String nonce1;
    
    @Index(name="nonce2Index")
    @Column(nullable = false, columnDefinition = "VARCHAR(24)", unique = true)
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
    @Column(nullable = false, columnDefinition = "TINYINT(1) default 0")
    private Boolean used;
    
    @Index(name="uploadedIndex")
    @Column(nullable = false, columnDefinition = "TINYINT(1) default 0")
    private Boolean uploaded;
    
    @Column(nullable = false, columnDefinition = "TINYINT(1) default 0")
    private Boolean expired;
    
    @Column(nullable = false)
    private int protocolVersion;

    public DHKeys() {
    }

    public DHKeys(Long id, Subscriber owner, String forUser, byte[] aEncBlock, byte[] sEncBlock, String nonce1, String nonce2, byte[] sig1, byte[] sig2, Date created, Date whenUsed, Date expires, Boolean used, Boolean expired) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.aEncBlock = aEncBlock;
        this.sEncBlock = sEncBlock;
        this.nonce1 = nonce1;
        this.nonce2 = nonce2;
        this.sig1 = sig1;
        this.sig2 = sig2;
        this.created = created;
        this.whenUsed = whenUsed;
        this.expires = expires;
        this.used = used;
        this.expired = expired;
    }

    public DHKeys(Long id) {
        this.id = id;
    }

    public DHKeys(Long id, Subscriber owner, String forUser, String nonce1, String nonce2, Date created, Date whenUsed, Date expires) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.nonce1 = nonce1;
        this.nonce2 = nonce2;
        this.created = created;
        this.whenUsed = whenUsed;
        this.expires = expires;
    }

    public DHKeys(Long id, Subscriber owner, String forUser, String nonce2, Date expires, Boolean used, Boolean uploaded) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.nonce2 = nonce2;
        this.expires = expires;
        this.used = used;
        this.uploaded = uploaded;
    }

    public DHKeys(Long id, Subscriber owner, String forUser, String nonce2, Date expires, Boolean used) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.nonce2 = nonce2;
        this.expires = expires;
        this.used = used;
    }

    public DHKeys(Long id, Subscriber owner, String forUser, Date expires, Boolean used, Boolean uploaded) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.expires = expires;
        this.used = used;
        this.uploaded = uploaded;
    }

    public DHKeys(Long id, Subscriber owner, String forUser, Date expires, Boolean used, Boolean uploaded, Boolean expired) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.expires = expires;
        this.used = used;
        this.uploaded = uploaded;
        this.expired = expired;
    }
    
    public DHKeys(Long id, Subscriber owner, String forUser, Date expires, Boolean used) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.expires = expires;
        this.used = used;
    }

    public DHKeys(Long id, Subscriber owner, String forUser, byte[] aEncBlock, byte[] sEncBlock, String nonce1, String nonce2, byte[] sig1, byte[] sig2, Date created, Date whenUsed, Date expires, Boolean used, Boolean uploaded, Boolean expired, int protocolVersion) {
        this.id = id;
        this.owner = owner;
        this.forUser = forUser;
        this.aEncBlock = aEncBlock;
        this.sEncBlock = sEncBlock;
        this.nonce1 = nonce1;
        this.nonce2 = nonce2;
        this.sig1 = sig1;
        this.sig2 = sig2;
        this.created = created;
        this.whenUsed = whenUsed;
        this.expires = expires;
        this.used = used;
        this.uploaded = uploaded;
        this.expired = expired;
        this.protocolVersion = protocolVersion;
    }
    
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
        return aEncBlock;
    }

    public void setaAncBlock(byte[] aAncBlock) {
        this.aEncBlock = aAncBlock;
    }

    public byte[] getsAncBlock() {
        return sEncBlock;
    }

    public void setsAncBlock(byte[] sAncBlock) {
        this.sEncBlock = sAncBlock;
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

    public Boolean isUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }

    public Boolean isExpired() {
        return expired;
    }

    public void setExpired(Boolean expired) {
        this.expired = expired;
    }

    public byte[] getaEncBlock() {
        return aEncBlock;
    }

    public void setaEncBlock(byte[] aEncBlock) {
        this.aEncBlock = aEncBlock;
    }

    public byte[] getsEncBlock() {
        return sEncBlock;
    }

    public void setsEncBlock(byte[] sEncBlock) {
        this.sEncBlock = sEncBlock;
    }

    public Date getWhenUsed() {
        return whenUsed;
    }

    public void setWhenUsed(Date whenUsed) {
        this.whenUsed = whenUsed;
    }

    public Boolean isUploaded() {
        return uploaded;
    }

    public void setUploaded(Boolean uploaded) {
        this.uploaded = uploaded;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
    
    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + (this.id != null ? this.id.hashCode() : 0);
        hash = 29 * hash + (this.owner != null ? this.owner.hashCode() : 0);
        hash = 29 * hash + (this.forUser != null ? this.forUser.hashCode() : 0);
        hash = 29 * hash + Arrays.hashCode(this.aEncBlock);
        hash = 29 * hash + Arrays.hashCode(this.sEncBlock);
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
        if (!Arrays.equals(this.aEncBlock, other.aEncBlock)) {
            return false;
        }
        if (!Arrays.equals(this.sEncBlock, other.sEncBlock)) {
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
