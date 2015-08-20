package com.phoenix.db;

import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.extra.ContactlistStatus;
import com.phoenix.db.opensips.Subscriber;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Group entity for contact list entries.
 * @author ph4r05
 */
@Entity(name = "contactGroup")
public class ContactGroup implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    // Textual group key, may be used by application. Has to be unique for the user.
    @Column(nullable = false, columnDefinition = "VARCHAR(64)")
    private String groupKey;

    // Textual group type, may be used by application / system.
    @Column(nullable = false, columnDefinition = "VARCHAR(32) DEFAULT `NONE`")
    private String groupType;

    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=false)
    private Subscriber owner;

    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "dateCreated", nullable = false, columnDefinition = "DATETIME")
    private Date dateCreated;

    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(name = "dateLastEdit", nullable = false, columnDefinition = "DATETIME")
    private Date dateLastEdit;

    @Column(nullable = true, columnDefinition = "VARCHAR(255)")
    private String groupName;

    @Lob
    @Column(nullable = true, columnDefinition = "TEXT")
    private String auxData;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public Subscriber getOwner() {
        return owner;
    }

    public void setOwner(Subscriber owner) {
        this.owner = owner;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getDateLastEdit() {
        return dateLastEdit;
    }

    public void setDateLastEdit(Date dateLastEdit) {
        this.dateLastEdit = dateLastEdit;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getAuxData() {
        return auxData;
    }

    public void setAuxData(String auxData) {
        this.auxData = auxData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ContactGroup that = (ContactGroup) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (groupKey != null ? !groupKey.equals(that.groupKey) : that.groupKey != null) return false;
        if (groupType != null ? !groupType.equals(that.groupType) : that.groupType != null) return false;
        if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;
        if (dateCreated != null ? !dateCreated.equals(that.dateCreated) : that.dateCreated != null) return false;
        if (dateLastEdit != null ? !dateLastEdit.equals(that.dateLastEdit) : that.dateLastEdit != null) return false;
        if (groupName != null ? !groupName.equals(that.groupName) : that.groupName != null) return false;
        return !(auxData != null ? !auxData.equals(that.auxData) : that.auxData != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (groupKey != null ? groupKey.hashCode() : 0);
        result = 31 * result + (groupType != null ? groupType.hashCode() : 0);
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (dateCreated != null ? dateCreated.hashCode() : 0);
        result = 31 * result + (dateLastEdit != null ? dateLastEdit.hashCode() : 0);
        result = 31 * result + (groupName != null ? groupName.hashCode() : 0);
        result = 31 * result + (auxData != null ? auxData.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ContactGroup{" +
                "id=" + id +
                ", groupKey='" + groupKey + '\'' +
                ", groupType='" + groupType + '\'' +
                ", owner=" + owner +
                ", dateCreated=" + dateCreated +
                ", dateLastEdit=" + dateLastEdit +
                ", groupName='" + groupName + '\'' +
                ", auxData='" + auxData + '\'' +
                '}';
    }
}
