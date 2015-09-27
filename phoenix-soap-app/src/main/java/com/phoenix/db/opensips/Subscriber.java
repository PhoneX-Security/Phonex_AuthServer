package com.phoenix.db.opensips;

// Generated Dec 2, 2012 7:18:24 PM by Hibernate Tools 3.4.0.CR1

import javax.persistence.*;
import java.util.Calendar;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * Subscriber generated by hbm2java
 */
@Entity
@Table(name = "subscriber", uniqueConstraints = @UniqueConstraint(columnNames = {"username", "domain"}))
public class Subscriber implements java.io.Serializable {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "domain", nullable = false, length = 64)
    private String domain;

    @Column(name = "password", nullable = false, length = 32)
    private String password;

    @Column(name = "email_address", nullable = false, length = 64)
    private String emailAddress;

    @Column(name = "ha1", nullable = false, length = 64)
    private String ha1;

    @Column(name = "ha1b", nullable = false, length = 64)
    private String ha1b;

    @Column(name = "rpid", length = 64)
    private String rpid;

    @Column(name = "isAdmin", nullable = false)
    private boolean isAdmin;

    @Column(name = "primaryGroup")
    private Integer primaryGroup;

    @Column(name = "turnPasswd", nullable = true, length = 64)
    private String turnPasswd;

    @Column(name = "turn_passwd_ha1b", nullable = true, length = 32)
    private String turnPasswdHa1b;

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
    @Column(name = "app_version", nullable = true, columnDefinition = "TEXT")
    private String appVersion;

    @Column(name = "last_action_ip", nullable = true, length = 64)
    private String lastActionIp;

    @Column(name = "last_authcheck_ip", nullable = true, length = 64)
    private String lastAuthCheckIp;

    @Lob
    @Column(name = "testing_settings", nullable = true, columnDefinition = "TEXT")
    private String testingSettings;

    @Lob
    @Column(name = "usage_policy_expired", nullable = true, columnDefinition = "TEXT")
    private String usagePolicyExpired;

    @Lob
    @Column(name = "usage_policy_current", nullable = true, columnDefinition = "TEXT")
    private String usagePolicyCurrent;

    @Lob
    @Column(name = "aux_data", nullable = true, columnDefinition = "TEXT")
    private String auxData;

    public Subscriber() {
    }

    public Subscriber(String username, String domain, String password,
                      String emailAddress, String ha1, String ha1b, boolean isAdmin) {
        this.username = username;
        this.domain = domain;
        this.password = password;
        this.emailAddress = emailAddress;
        this.ha1 = ha1;
        this.ha1b = ha1b;
        this.isAdmin = isAdmin;
    }

    public Subscriber(String username, String domain, String password,
                      String emailAddress, String ha1, String ha1b, String rpid,
                      boolean isAdmin, Integer primaryGroup) {
        this.username = username;
        this.domain = domain;
        this.password = password;
        this.emailAddress = emailAddress;
        this.ha1 = ha1;
        this.ha1b = ha1b;
        this.rpid = rpid;
        this.isAdmin = isAdmin;
        this.primaryGroup = primaryGroup;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDomain() {
        return this.domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmailAddress() {
        return this.emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getHa1() {
        return this.ha1;
    }

    public void setHa1(String ha1) {
        this.ha1 = ha1;
    }

    public String getHa1b() {
        return this.ha1b;
    }

    public void setHa1b(String ha1b) {
        this.ha1b = ha1b;
    }

    public String getRpid() {
        return this.rpid;
    }

    public void setRpid(String rpid) {
        this.rpid = rpid;
    }

    public boolean isIsAdmin() {
        return this.isAdmin;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Integer getPrimaryGroup() {
        return this.primaryGroup;
    }

    public void setPrimaryGroup(Integer primaryGroup) {
        this.primaryGroup = primaryGroup;
    }

    /**
     * @return
     */
    public Boolean isCanSignNewCert() {
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

    public Boolean isDeleted() {
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

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public Calendar getDateLastActivity() {
        return dateLastActivity;
    }

    public void setDateLastActivity(Calendar dateLastActivity) {
        this.dateLastActivity = dateLastActivity;
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

    public String getTurnPasswd() {
        return turnPasswd;
    }

    public void setTurnPasswd(String turnPasswd) {
        this.turnPasswd = turnPasswd;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public String getTurnPasswdHa1b() {
        return turnPasswdHa1b;
    }

    public void setTurnPasswdHa1b(String turnPasswdHa1b) {
        this.turnPasswdHa1b = turnPasswdHa1b;
    }

    public Boolean getCanSignNewCert() {
        return canSignNewCert;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public String getTestingSettings() {
        return testingSettings;
    }

    public void setTestingSettings(String testingSettings) {
        this.testingSettings = testingSettings;
    }

    public String getAuxData() {
        return auxData;
    }

    public void setAuxData(String auxData) {
        this.auxData = auxData;
    }

    public String getUsagePolicyExpired() {
        return usagePolicyExpired;
    }

    public void setUsagePolicyExpired(String usagePolicyExpired) {
        this.usagePolicyExpired = usagePolicyExpired;
    }

    public String getUsagePolicyCurrent() {
        return usagePolicyCurrent;
    }

    public void setUsagePolicyCurrent(String usagePolicyCurrent) {
        this.usagePolicyCurrent = usagePolicyCurrent;
    }
}
