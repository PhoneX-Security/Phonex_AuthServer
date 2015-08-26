package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Error reporting from application.
 * @author ph4r05
 */
@Entity(name = "phxErrorReport")
public class PhxErrorReport implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    @Column(nullable = false, columnDefinition = "VARCHAR(128)")
    private String userName;

    @Column(nullable = true, columnDefinition = "VARCHAR(128)")
    private String userResource;

    // auditing information about creating and last change
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateCreated;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_expiration", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateExpiration;

    @Lob
    @Column(nullable = true, columnDefinition = "TEXT")
    private String appVersion;

    @Lob
    @Column(nullable = true, columnDefinition = "TEXT")
    private String userMessage;

    @Column(nullable = true, columnDefinition = "VARCHAR(255)")
    private String filename;

    @Column(nullable = false)
    private long fileSize = 0;

    @Lob
    @Column(nullable = true, columnDefinition = "TEXT")
    private String auxData;

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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserResource() {
        return userResource;
    }

    public void setUserResource(String userResource) {
        this.userResource = userResource;
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

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getAuxData() {
        return auxData;
    }

    public void setAuxData(String auxData) {
        this.auxData = auxData;
    }

    @Override
    public String toString() {
        return "ErrorReport{" +
                "id=" + id +
                ", owner=" + owner +
                ", userName='" + userName + '\'' +
                ", userResource='" + userResource + '\'' +
                ", dateCreated=" + dateCreated +
                ", dateExpiration=" + dateExpiration +
                ", appVersion='" + appVersion + '\'' +
                ", userMessage='" + userMessage + '\'' +
                ", filename='" + filename + '\'' +
                ", fileSize=" + fileSize +
                ", auxData='" + auxData + '\'' +
                '}';
    }
}
