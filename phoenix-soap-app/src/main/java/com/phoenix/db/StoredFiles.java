/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
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
 * User's stored files, sent by different users.
 * @author ph4r05
 */
@Entity
@Table(name = "stored_files")
public class StoredFiles {
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
    private String sender;
    
    @Index(name="nonce2Index")
    @Column(nullable = false, columnDefinition = "VARCHAR(24)", unique = true)
    private String nonce2;
    
    @Lob
    @Column(nullable = false)
    private byte[] dhpublic;
    
    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date created;
    
    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date expires;
    
    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private String hashMeta;
    
    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private String hashPack;
    
    @Column(nullable = false)
    private long sizeMeta;
    
    @Column(nullable = false)
    private long sizePack;
    
    @Column(nullable = false)
    private int protocolVersion;

    public StoredFiles() {
    }
    
    public StoredFiles(Long id, Subscriber owner, String sender, String nonce2, byte[] dhpublic, Date created, Date expires, String hashMeta, String hashPack, long sizeMeta, long sizePack, int protocolVersion) {
        this.id = id;
        this.owner = owner;
        this.sender = sender;
        this.nonce2 = nonce2;
        this.dhpublic = dhpublic;
        this.created = created;
        this.expires = expires;
        this.hashMeta = hashMeta;
        this.hashPack = hashPack;
        this.sizeMeta = sizeMeta;
        this.sizePack = sizePack;
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

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getNonce2() {
        return nonce2;
    }

    public void setNonce2(String nonce2) {
        this.nonce2 = nonce2;
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

    public String getHashMeta() {
        return hashMeta;
    }

    public void setHashMeta(String hashMeta) {
        this.hashMeta = hashMeta;
    }

    public String getHashPack() {
        return hashPack;
    }

    public void setHashPack(String hashPack) {
        this.hashPack = hashPack;
    }

    public long getSizeMeta() {
        return sizeMeta;
    }

    public void setSizeMeta(long sizeMeta) {
        this.sizeMeta = sizeMeta;
    }

    public long getSizePack() {
        return sizePack;
    }

    public void setSizePack(long sizePack) {
        this.sizePack = sizePack;
    }

    public byte[] getDhpublic() {
        return dhpublic;
    }

    public void setDhpublic(byte[] dhpublic) {
        this.dhpublic = dhpublic;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
}
