package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;

import javax.persistence.*;
import java.util.Date;

/**
 * Event log for trial account.
 * @author ph4r05
 */
@Entity
@Table(name = "trialEventLog")
public class TrialEventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    // owner of this contact list
    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    // type of object
    @Column(nullable = false)
    private int etype;

    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "date_created", nullable = true, columnDefinition = "DATETIME")
    private Date dateCreated;

    public TrialEventLog() {
    }

    public TrialEventLog(Long id, Subscriber owner, int etype, Date dateCreated) {
        this.id = id;
        this.owner = owner;
        this.etype = etype;
        this.dateCreated = dateCreated;
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

    public int getEtype() {
        return etype;
    }

    public void setEtype(int etype) {
        this.etype = etype;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TrialEventLog that = (TrialEventLog) o;

        if (etype != that.etype) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;
        return !(dateCreated != null ? !dateCreated.equals(that.dateCreated) : that.dateCreated != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + etype;
        result = 31 * result + (dateCreated != null ? dateCreated.hashCode() : 0);
        return result;
    }


}
