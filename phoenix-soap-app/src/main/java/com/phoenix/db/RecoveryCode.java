package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * Storing recovery codes for password reset.
 *
 * Created by dusanklinec on 05.01.16.
 */
@Entity
@Table(name = "recoveryCode")
public class RecoveryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Index(name = "idxSubscriber")
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    @Column(name = "subscriber_sip", nullable = false, length = 255)
    private String subscriberSip;

    @Column(name = "subscriber_resource", nullable = true, length = 32)
    private String resource;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateCreated = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_valid", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateValid;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_confirmed", nullable = true, columnDefinition = "TIMESTAMP")
    private Date dateConfirmed;

    @Column(name = "code_valid", nullable = false)
    private Boolean codeIsValid = true;

    @Column(name = "recovery_code", nullable = false, length = 32)
    private String recoveryCode;

    @Column(name = "recovery_email", nullable = false, length = 250)
    private String recoveryEmail;

    @Column(name = "request_ip", nullable = false, length = 64)
    private String requestIp;

    @Column(name = "confirm_ip", nullable = false, length = 64)
    private String confirmIp;

    @Column(name = "request_appversion", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String requestAppVersion;

    @Column(name = "confirm_appversion", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String confirmAppVersion;

    @Column(name = "aextra", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String extra;

    @Column(name = "aaux", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String aux;

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

    public String getSubscriberSip() {
        return subscriberSip;
    }

    public void setSubscriberSip(String subscriberSip) {
        this.subscriberSip = subscriberSip;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateValid() {
        return dateValid;
    }

    public void setDateValid(Date dateValid) {
        this.dateValid = dateValid;
    }

    public Date getDateConfirmed() {
        return dateConfirmed;
    }

    public void setDateConfirmed(Date dateConfirmed) {
        this.dateConfirmed = dateConfirmed;
    }

    public Boolean getCodeIsValid() {
        return codeIsValid;
    }

    public void setCodeIsValid(Boolean codeIsValid) {
        this.codeIsValid = codeIsValid;
    }

    public String getRecoveryCode() {
        return recoveryCode;
    }

    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }

    public String getRecoveryEmail() {
        return recoveryEmail;
    }

    public void setRecoveryEmail(String recoveryEmail) {
        this.recoveryEmail = recoveryEmail;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getConfirmIp() {
        return confirmIp;
    }

    public void setConfirmIp(String confirmIp) {
        this.confirmIp = confirmIp;
    }

    public String getRequestAppVersion() {
        return requestAppVersion;
    }

    public void setRequestAppVersion(String requestAppVersion) {
        this.requestAppVersion = requestAppVersion;
    }

    public String getConfirmAppVersion() {
        return confirmAppVersion;
    }

    public void setConfirmAppVersion(String confirmAppVersion) {
        this.confirmAppVersion = confirmAppVersion;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getAux() {
        return aux;
    }

    public void setAux(String aux) {
        this.aux = aux;
    }

    @Override
    public String toString() {
        return "RecoveryCode{" +
                "id=" + id +
                ", owner=" + owner +
                ", subscriberSip='" + subscriberSip + '\'' +
                ", resource='" + resource + '\'' +
                ", dateCreated=" + dateCreated +
                ", dateValid=" + dateValid +
                ", dateConfirmed=" + dateConfirmed +
                ", codeIsValid=" + codeIsValid +
                ", recoveryCode='" + recoveryCode + '\'' +
                ", recoveryEmail='" + recoveryEmail + '\'' +
                ", requestIp='" + requestIp + '\'' +
                ", confirmIp='" + confirmIp + '\'' +
                ", requestAppVersion='" + requestAppVersion + '\'' +
                ", confirmAppVersion='" + confirmAppVersion + '\'' +
                ", extra='" + extra + '\'' +
                ", aux='" + aux + '\'' +
                '}';
    }
}
