package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.PhoenixServerCASigner;
import com.phoenix.utils.MiscUtils;
import org.bouncycastle.util.encoders.Base64;
import org.hibernate.annotations.Index;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.security.cert.*;
import java.util.Arrays;
import java.util.Date;

/**
 * Holding generated CRL.
 *
 * Created by dusanklinec on 23.11.15.
 */
@Entity
@Table(name = "crlHolder")
public class CrlHolder implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(CrlHolder.class);
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(nullable = false)
    @Index(name="domain")
    private String domain;

    @Lob
    @Column(name = "crl", nullable = false)
    private byte[] rawCrl;

    @Lob
    @Column(name = "pem_crl", nullable = true, columnDefinition = "LONGTEXT")
    private String pemCrl;

    @Transient
    private X509CRL crl;

    // pre-cache some meta data here - for database search purposes
    @Column(name = "time_generated", nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date timeGenerated;

    @Column(name = "serial", nullable = false)
    private Long serial;

    @Column(name = "ca_id", nullable = true, length = 64)
    private String caId;

    @Column(name = "num_records", nullable = false)
    private long numberOfRecords = 0;

    @Override
    public String toString() {
        return "CrlHolder{" +
                "id=" + id +
                ", domain='" + domain + '\'' +
                ", rawCrl=" + new String(Base64.encode(rawCrl)) +
                ", pemCrl='" + pemCrl + '\'' +
                ", crl=" + crl +
                ", timeGenerated=" + timeGenerated +
                ", serial=" + serial +
                ", caId='" + caId + '\'' +
                ", numberOfRecords=" + numberOfRecords +
                '}';
    }

    /**
     * Update CRL object from RAW representation
     */
    public void updateCrl() throws CRLException, CertificateException {
        if (this.rawCrl == null || this.rawCrl.length == 0){
            this.crl = null;
            return;
        }

        InputStream in = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            in = new ByteArrayInputStream(this.rawCrl);
            this.crl = (X509CRL) cf.generateCRL(in);

        } catch(CRLException e) {
            this.crl = null;
            throw e;

        } catch (CertificateException e) {
            this.crl = null;
            throw e;

        } finally {
            MiscUtils.closeSilently(in);
        }
    }

    /**
     * Updates CRL from raw representation, returns true if succeeded.
     *
     * @return
     */
    public boolean tryUpdateCrl(){
        try {
            updateCrl();
            return true;

        } catch(Exception e){
            log.error("Exception when updating CRL object from encoded form");
        }

        crl = null;
        return false;
    }

    /**
     * Tries to generate PEM form of the CRL from CRL object.
     * @return true if no exception ocurred
     */
    public boolean updatePem(){
        if (crl == null){
            return false;
        }

        try {
            pemCrl = new String(PhoenixServerCASigner.getCRLAsPEM(crl), "UTF-8");
            return true;

        } catch(Exception e) {
            log.error("Exception when generating PEM CRL");
            pemCrl = null;
        }

        return false;
    }

    public Long getSerial() {
        return serial;
    }

    public void setSerial(Long serial) {
        this.serial = serial;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public byte[] getRawCrl() {
        return rawCrl;
    }

    public void setRawCrl(byte[] rawCrl) {
        this.rawCrl = rawCrl;
    }

    public Date getTimeGenerated() {
        return timeGenerated;
    }

    public void setTimeGenerated(Date timeGenerated) {
        this.timeGenerated = timeGenerated;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Transient
    public X509CRL getCrl() {
        return crl;
    }

    public void setCrl(X509CRL crl) {
        this.crl = crl;
    }

    public String getPemCrl() {
        return pemCrl;
    }

    public void setPemCrl(String pemCrl) {
        this.pemCrl = pemCrl;
    }

    public String getCaId() {
        return caId;
    }

    public void setCaId(String caId) {
        this.caId = caId;
    }

    public long getNumberOfRecords() {
        return numberOfRecords;
    }

    public void setNumberOfRecords(long numberOfRecords) {
        this.numberOfRecords = numberOfRecords;
    }
}
