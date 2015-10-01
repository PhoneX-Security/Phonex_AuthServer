package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * Accounting log to track user actions on the client, e.g., number of seconds for outgoing calls, MB for
 * outgoing data files. Stores aggregated records.
 *
 * @author ph4r05
 */
@Entity(name = "accountingAggregated")
public class AccountingAggregated {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Index(name = "idxSubscriber")
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    @Column(name = "aresource", nullable = true, length = 32)
    private String resource;

    @Column(name = "atype", nullable = false, length = 24)
    private String type;

    /**
     * Aggregation key, hashed value for the aggregation record.
     * Base64 encoded hash, smaller index size.
     */
    @Index(name="akey")
    @Column(name = "aggregation_key", nullable = false, length = 24)
    private String aggregationKey;

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
     * Amount of units together in aggregated record.
     */
    @Column(name = "aamount", nullable = false)
    private long amount = 0;

    /**
     * Aggregation interval size in milliseconds.
     */
    @Column(name = "aagregation_period", nullable = false)
    private long aggregationPeriod = 3600000;

    /**
     * Number of records in the aggregated record.
     */
    @Column(name = "aagregation_count", nullable = false)
    private int aggregationCount = 1;

    /**
     * Boundary in milliseconds from which aggregation is computed in this record.
     */
    @Column(name = "aggregation_start", nullable = false)
    private long aggregationStart;

    @Column(name = "aref", nullable = true, length = 64)
    private String aaref;

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

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getAggregationPeriod() {
        return aggregationPeriod;
    }

    public void setAggregationPeriod(long aggregationPeriod) {
        this.aggregationPeriod = aggregationPeriod;
    }

    public int getAggregationCount() {
        return aggregationCount;
    }

    public void setAggregationCount(int aggregationCount) {
        this.aggregationCount = aggregationCount;
    }

    public String getAaref() {
        return aaref;
    }

    public void setAaref(String aaref) {
        this.aaref = aaref;
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

    public String getAggregationKey() {
        return aggregationKey;
    }

    public void setAggregationKey(String aggregationKey) {
        this.aggregationKey = aggregationKey;
    }

    public long getAggregationStart() {
        return aggregationStart;
    }

    public void setAggregationStart(long aggregationStart) {
        this.aggregationStart = aggregationStart;
    }

    @Override
    public String toString() {
        return "AccountingAggregated{" +
                "id=" + id +
                ", owner=" + owner +
                ", resource='" + resource + '\'' +
                ", type='" + type + '\'' +
                ", aggregationKey='" + aggregationKey + '\'' +
                ", dateCreated=" + dateCreated +
                ", dateModified=" + dateModified +
                ", actionIdFirst=" + actionIdFirst +
                ", actionCounterFirst=" + actionCounterFirst +
                ", actionIdLast=" + actionIdLast +
                ", actionCounterLast=" + actionCounterLast +
                ", amount=" + amount +
                ", aggregationPeriod=" + aggregationPeriod +
                ", aggregationCount=" + aggregationCount +
                ", aggregationStart=" + aggregationStart +
                ", aaref='" + aaref + '\'' +
                ", extra='" + extra + '\'' +
                ", aux='" + aux + '\'' +
                '}';
    }
}
