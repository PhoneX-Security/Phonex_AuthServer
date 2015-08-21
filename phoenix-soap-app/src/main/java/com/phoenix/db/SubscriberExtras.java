package com.phoenix.db;

import javax.persistence.*;
import java.util.Calendar;

/**
 * Created by dusanklinec on 12.03.15.
 */
@Entity
@Table(name = "subscriber_extras")
public class SubscriberExtras {
    @Id
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Column(name = "turnPasswd", nullable = true, length = 64)
    private String turnPasswd;

    // Can user sign new certificate?
    @Column(name = "canSignNewCert", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean canSignNewCert = false;

    // Should be user forced to change this password during first login?
    @Column(name = "forcePasswordChange", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean forcePasswordChange = false;

    // Account was issued on (datetime)
    // Has to add "&amp;zeroDateTimeBehavior=convertToNull" to connection string
    // Problem [http://stackoverflow.com/questions/11133759/0000-00-00-000000-can-not-be-represented-as-java-sql-timestamp-error]
    @Column(name = "issued_on", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar issued;

    // Account will expire on (datetime)
    @Column(name = "expires_on", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar expires;

    // Should account be considered as deleted / not valid?
    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean deleted = false;

    // Timestamp for the first login.
    @Column(name = "date_first_login", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar dateFirstLogin = null;

    // Timestamp for the first user added.
    @Column(name = "date_first_user_added", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar dateFirstUserAdded = null;

    // Timestamp for the first authCheck.
    @Column(name = "date_first_authCheck", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar dateFirstAuthCheck = null;

    // Timestamp for the last authCheck.
    @Column(name = "date_last_authCheck", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar dateLastAuthCheck = null;

    // Timestamp for the last password change.
    @Column(name = "date_last_pass_change", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar dateLastPasswordChange = null;

    // Timestamp for the last activity.
    @Column(name = "date_last_activity", nullable = true, columnDefinition = "DATETIME")
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private java.util.Calendar dateLastActivity = null;

    // License type for the user.
    @Column(name = "license_type", nullable = true, length = 128)
    private String licenseType;

    @Lob
    @Column(name = "app_version", nullable = true, columnDefinition="TEXT")
    private String appVersion;

    @Column(name = "last_action_ip", nullable = true, length = 64)
    private String lastActionIp;

    @Column(name = "last_authcheck_ip", nullable = true, length = 64)
    private String lastAuthCheckIp;

    @Column(name = "testing_type", nullable = true, length = 32)
    private String testingType;

    @Lob
    @Column(name = "aux_data", nullable = true, columnDefinition="TEXT")
    private String auxData;

    public SubscriberExtras() {
    }

    public SubscriberExtras(Long id, String turnPasswd, Boolean canSignNewCert, Boolean forcePasswordChange, Calendar issued, Calendar expires, Boolean deleted, Calendar dateFirstLogin, Calendar dateFirstUserAdded, Calendar dateFirstAuthCheck, Calendar dateLastAuthCheck, Calendar dateLastPasswordChange, Calendar dateLastActivity, String licenseType, String appVersion, String lastActionIp, String lastAuthCheckIp) {
        this.id = id;
        this.turnPasswd = turnPasswd;
        this.canSignNewCert = canSignNewCert;
        this.forcePasswordChange = forcePasswordChange;
        this.issued = issued;
        this.expires = expires;
        this.deleted = deleted;
        this.dateFirstLogin = dateFirstLogin;
        this.dateFirstUserAdded = dateFirstUserAdded;
        this.dateFirstAuthCheck = dateFirstAuthCheck;
        this.dateLastAuthCheck = dateLastAuthCheck;
        this.dateLastPasswordChange = dateLastPasswordChange;
        this.dateLastActivity = dateLastActivity;
        this.licenseType = licenseType;
        this.appVersion = appVersion;
        this.lastActionIp = lastActionIp;
        this.lastAuthCheckIp = lastAuthCheckIp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTurnPasswd() {
        return turnPasswd;
    }

    public void setTurnPasswd(String turnPasswd) {
        this.turnPasswd = turnPasswd;
    }

    public Boolean getCanSignNewCert() {
        return canSignNewCert;
    }

    public void setCanSignNewCert(Boolean canSignNewCert) {
        this.canSignNewCert = canSignNewCert;
    }

    public Boolean getForcePasswordChange() {
        return forcePasswordChange;
    }

    public void setForcePasswordChange(Boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
    }

    public Calendar getIssued() {
        return issued;
    }

    public void setIssued(Calendar issued) {
        this.issued = issued;
    }

    public Calendar getExpires() {
        return expires;
    }

    public void setExpires(Calendar expires) {
        this.expires = expires;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public Calendar getDateFirstLogin() {
        return dateFirstLogin;
    }

    public void setDateFirstLogin(Calendar dateFirstLogin) {
        this.dateFirstLogin = dateFirstLogin;
    }

    public Calendar getDateFirstUserAdded() {
        return dateFirstUserAdded;
    }

    public void setDateFirstUserAdded(Calendar dateFirstUserAdded) {
        this.dateFirstUserAdded = dateFirstUserAdded;
    }

    public Calendar getDateFirstAuthCheck() {
        return dateFirstAuthCheck;
    }

    public void setDateFirstAuthCheck(Calendar dateFirstAuthCheck) {
        this.dateFirstAuthCheck = dateFirstAuthCheck;
    }

    public Calendar getDateLastAuthCheck() {
        return dateLastAuthCheck;
    }

    public void setDateLastAuthCheck(Calendar dateLastAuthCheck) {
        this.dateLastAuthCheck = dateLastAuthCheck;
    }

    public Calendar getDateLastPasswordChange() {
        return dateLastPasswordChange;
    }

    public void setDateLastPasswordChange(Calendar dateLastPasswordChange) {
        this.dateLastPasswordChange = dateLastPasswordChange;
    }

    public Calendar getDateLastActivity() {
        return dateLastActivity;
    }

    public void setDateLastActivity(Calendar dateLastActivity) {
        this.dateLastActivity = dateLastActivity;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getLastActionIp() {
        return lastActionIp;
    }

    public void setLastActionIp(String lastActionIp) {
        this.lastActionIp = lastActionIp;
    }

    public String getLastAuthCheckIp() {
        return lastAuthCheckIp;
    }

    public void setLastAuthCheckIp(String lastAuthCheckIp) {
        this.lastAuthCheckIp = lastAuthCheckIp;
    }
}
