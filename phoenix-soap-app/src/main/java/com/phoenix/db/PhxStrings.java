package com.phoenix.db;

import com.phoenix.db.extra.PairingRequestResolution;
import com.phoenix.db.opensips.Subscriber;
import org.hibernate.annotations.Index;

import javax.persistence.*;
import java.util.Date;

/**
 * Translation strings.
 *
 * Created by dusanklinec on 05.01.16.
 */
@Entity
@Table(name = "phxStrings")
public class PhxStrings {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    @Index(name = "idxKey")
    @Column(name = "string_key", nullable = false, length = 255)
    private String key;

    @Index(name = "idxLocale")
    @Column(name = "string_locale", nullable = false, length = 16)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "plural_type", nullable = false, columnDefinition="char(6) default 'NONE'")
    private String pluralType;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created", nullable = false, columnDefinition = "TIMESTAMP")
    private Date dateCreated = new Date();

    @Column(name = "translatable", nullable = false, columnDefinition = "TINYINT")
    private Boolean translatable = true;

    @Column(name = "string_value", nullable = true, columnDefinition = "TEXT")
    @Lob
    private String value;

    public String getPluralType() {
        return pluralType;
    }

    public void setPluralType(String pluralType) {
        this.pluralType = pluralType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Boolean getTranslatable() {
        return translatable;
    }

    public void setTranslatable(Boolean translatable) {
        this.translatable = translatable;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "PhxStrings{" +
                "id=" + id +
                ", key='" + key + '\'' +
                ", locale='" + locale + '\'' +
                ", pluralType='" + pluralType + '\'' +
                ", dateCreated=" + dateCreated +
                ", translatable=" + translatable +
                ", value='" + value + '\'' +
                '}';
    }
}
