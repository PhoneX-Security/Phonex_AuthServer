package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * Accounting log to track user actions on the client, e.g., number of seconds for outgoing calls, MB for
 * outgoing data files.
 *
 * @author ph4r05
 */
@Entity
@Table(name = "accountingLog")
public class AccountingLog {
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
     * Precomputed record key for easy duplicate detection.
     * base64(md5("owner;resource;type[;aref];actionId;actionCounter"));
     */
    @Index(name="rkey")
    @Column(name = "record_key", nullable = false, length = 24)
    private String rkey;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "adate_created", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateCreated = new Date();

    @Index(name="auid")
    @Column(name = "aid", nullable = false)
    private long actionId;

    @Index(name="auid")
    @Column(name = "actr", nullable = false)
    private int actionCounter;

    @Column(name = "aamount", nullable = false)
    private long amount;

    @Column(name = "aagregated", nullable = false)
    private int aggregated = 0;

    @Column(name = "aref", nullable = true, length = 64)
    private String aaref;

    @Column(name = "perm_id", nullable = true)
    private Long permId;

    @Column(name = "license_id", nullable = true)
    private Long licenseId;

    /**
     * Source action ID for this record. One call may be split among multiple records.
     */
    @Column(name = "source_id", nullable = true)
    private Long srcdId;

    @Column(name = "aextra", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String extra;

    @Column(name = "aaux", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String aux;

    public AccountingLog() {

    }

    public AccountingLog(Long id, Subscriber owner, String resource, String type, Date dateCreated, long actionId, int actionCounter, long amount, String aaref, String extra, String aux) {
        this.id = id;
        this.owner = owner;
        this.resource = resource;
        this.type = type;
        this.dateCreated = dateCreated;
        this.actionId = actionId;
        this.actionCounter = actionCounter;
        this.amount = amount;
        this.aaref = aaref;
        this.extra = extra;
        this.aux = aux;
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

    public long getActionId() {
        return actionId;
    }

    public void setActionId(long actionId) {
        this.actionId = actionId;
    }

    public int getActionCounter() {
        return actionCounter;
    }

    public void setActionCounter(int actionCounter) {
        this.actionCounter = actionCounter;
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

    public int getAggregated() {
        return aggregated;
    }

    public void setAggregated(int aggregated) {
        this.aggregated = aggregated;
    }

    public String getRkey() {
        return rkey;
    }

    public void setRkey(String rkey) {
        this.rkey = rkey;
    }

    public Long getPermId() {
        return permId;
    }

    public void setPermId(Long permId) {
        this.permId = permId;
    }

    public Long getLicenseId() {
        return licenseId;
    }

    public void setLicenseId(Long licenseId) {
        this.licenseId = licenseId;
    }

    public Long getSrcdId() {
        return srcdId;
    }

    public void setSrcdId(Long srcdId) {
        this.srcdId = srcdId;
    }

    @Override
    public String toString() {
        return "AccountingLog{" +
                "id=" + id +
                ", owner=" + owner +
                ", resource='" + resource + '\'' +
                ", type='" + type + '\'' +
                ", rkey='" + rkey + '\'' +
                ", dateCreated=" + dateCreated +
                ", actionId=" + actionId +
                ", actionCounter=" + actionCounter +
                ", amount=" + amount +
                ", aggregated=" + aggregated +
                ", aaref='" + aaref + '\'' +
                ", extra='" + extra + '\'' +
                ", aux='" + aux + '\'' +
                '}';
    }
}
