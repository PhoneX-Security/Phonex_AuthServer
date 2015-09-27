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
@Entity(name = "accountingLog")
public class AccountingLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @JoinTable(name = "Subscriber", joinColumns = { @JoinColumn(name = "id", nullable = false) })
    @Column(name = "aowner", nullable = false)
    private Subscriber owner;

    @Column(name = "aresource", nullable = true, length = 32)
    private String resource;

    @Index(name="atype")
    @Column(name = "atype", nullable = false, length = 24)
    private String type;

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
}
