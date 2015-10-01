package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * Accounting log to track permission counters for the user.
 *
 * @author ph4r05
 */
@Entity(name = "accountingPermission")
public class AccountingPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Index(name = "idxSubscriber")
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    @Column(name = "perm_id", nullable = false)
    private long permId;

    @Column(name = "license_id", nullable = false)
    private long licenseId;

    /**
     * Permission name, for info only, not used as a key.
     */
    @Column(name = "pname", nullable = false, length = 32)
    private String name;

    /**
     * Amount of units together in aggregated record.
     */
    @Column(name = "aamount", nullable = false)
    private long amount = 0;

    @Column(name = "aref", nullable = true, length = 64)
    private String aaref;

    @Column(name = "max_value", nullable = true)
    private Long maxValue;

    /**
     * Aggregation key, hashed value for the aggregation record.
     * Base64 encoded hash(ownerSip:permissionId:licenseId).
     */
    @Index(name="ckey")
    @Column(name = "cache_key", nullable = false, length = 24)
    private String cacheKey;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "adate_created", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateCreated = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "adate_modified", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateModified = new Date();

    @Column(name = "aid_first", nullable = false)
    private long actionIdFirst;

    @Column(name = "actr_first", nullable = false)
    private int actionCounterFirst;

    @Column(name = "aid_last", nullable = false)
    private long actionIdLast;

    @Column(name = "actr_last", nullable = false)
    private int actionCounterLast;

    /**
     * Number of records in the aggregated record.
     */
    @Column(name = "aagregation_count", nullable = false)
    private int aggregationCount = 1;

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

    public long getPermId() {
        return permId;
    }

    public void setPermId(long permId) {
        this.permId = permId;
    }

    public long getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(long licenseId) {
        this.licenseId = licenseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getAaref() {
        return aaref;
    }

    public void setAaref(String aaref) {
        this.aaref = aaref;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateModified() {
        return dateModified;
    }

    public void setDateModified(Date dateModified) {
        this.dateModified = dateModified;
    }

    public long getActionIdFirst() {
        return actionIdFirst;
    }

    public void setActionIdFirst(long actionIdFirst) {
        this.actionIdFirst = actionIdFirst;
    }

    public int getActionCounterFirst() {
        return actionCounterFirst;
    }

    public void setActionCounterFirst(int actionCounterFirst) {
        this.actionCounterFirst = actionCounterFirst;
    }

    public long getActionIdLast() {
        return actionIdLast;
    }

    public void setActionIdLast(long actionIdLast) {
        this.actionIdLast = actionIdLast;
    }

    public int getActionCounterLast() {
        return actionCounterLast;
    }

    public void setActionCounterLast(int actionCounterLast) {
        this.actionCounterLast = actionCounterLast;
    }

    public int getAggregationCount() {
        return aggregationCount;
    }

    public void setAggregationCount(int aggregationCount) {
        this.aggregationCount = aggregationCount;
    }

    public Long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }
}
