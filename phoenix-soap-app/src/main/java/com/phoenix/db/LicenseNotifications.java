package com.phoenix.db;

import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.extra.ContactlistStatus;
import com.phoenix.db.opensips.Subscriber;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;

/**
 * Stores last notifications about license update.
 * When notification about license update is sent to the user (e.g., as a push message)
 * new LicenseNotifications is stored to indicate sent message not to spam user with license notifications.
 *
 * Created by dusanklinec on 27.03.15.
 */
@Entity
@Table(name = "licenseNotifications")
public class LicenseNotifications {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @OneToOne
    @JoinColumn(name="subscriber", nullable=false, unique = true)
    private Subscriber subscriber;

    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "licenseDateExpire", nullable = true, columnDefinition = "TIMESTAMP")
    private java.util.Calendar licenseDateExpire;

    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "lastNotificationDate", nullable = true, columnDefinition = "TIMESTAMP")
    private java.util.Calendar lastNotificationDate;

    public LicenseNotifications(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    public LicenseNotifications() {
    }

    public LicenseNotifications(Long id, Subscriber subscriber, Calendar licenseDateExpire, Calendar lastNotificationDate) {
        this.id = id;
        this.subscriber = subscriber;
        this.licenseDateExpire = licenseDateExpire;
        this.lastNotificationDate = lastNotificationDate;
    }

    public LicenseNotifications(Subscriber subscriber, Calendar licenseDateExpire, Calendar lastNotificationDate) {
        this.subscriber = subscriber;
        this.licenseDateExpire = licenseDateExpire;
        this.lastNotificationDate = lastNotificationDate;
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    public Calendar getLicenseDateExpire() {
        return licenseDateExpire;
    }

    public void setLicenseDateExpire(Calendar licenseDateExpire) {
        this.licenseDateExpire = licenseDateExpire;
    }

    public Calendar getLastNotificationDate() {
        return lastNotificationDate;
    }

    public void setLastNotificationDate(Calendar lastNotificationDate) {
        this.lastNotificationDate = lastNotificationDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
