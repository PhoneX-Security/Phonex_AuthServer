package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Authentication state database.
 * @author ph4r05
 */
@Entity(name = "phxAuthState")
public class PhxAuthState implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Index(name = "idxSubscriber")
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)", unique = true)
    private String nonce;

    @Index(name = "idxIdentifier")
    @Column(nullable = true, columnDefinition = "VARCHAR(255)")
    private String identifier;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String secret;

    @Column(name = "app_version_code", nullable = false)
    private long appVersionCode;

    @Lob
    @Column(name = "app_version", nullable = true, columnDefinition = "TEXT")
    private String appVersion;

    @Column(name = "was_used", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean wasUsed = false;

    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "date_created", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateCreated;

    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "date_used", nullable = true, columnDefinition = "TIMESTAMP")
    private Date dateUsed;

    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "date_expiration", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateExpiration;

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

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateExpiration() {
        return dateExpiration;
    }

    public void setDateExpiration(Date dateExpiration) {
        this.dateExpiration = dateExpiration;
    }

    public long getAppVersionCode() {
        return appVersionCode;
    }

    public void setAppVersionCode(long appVersionCode) {
        this.appVersionCode = appVersionCode;
    }

    public boolean isWasUsed() {
        return wasUsed;
    }

    public void setWasUsed(boolean wasUsed) {
        this.wasUsed = wasUsed;
    }

    public Date getDateUsed() {
        return dateUsed;
    }

    public void setDateUsed(Date dateUsed) {
        this.dateUsed = dateUsed;
    }
}
