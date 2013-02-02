/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.db;

import com.phoenix.db.opensips.Subscriber;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.Transient;

/**
 * @deprecated 1.2.2013
 * All certificates are stored in CAcertsSigned table together with their revocation
 * 
 * @author ph4r05
 */
@Entity(name = "subscriberCertificate")
public class SubscriberCertificate implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=false, unique=true)
    private Subscriber subscriber;
    
    //@Basic(fetch=LAZY)
    @Transient
    private X509Certificate cert;
    
    @Lob
    @Column(name = "cert", nullable = false)
    private byte[] rawCert;
    
    // just meta data, already in certificate
    @Transient
    private Date notValidBefore;
    @Transient
    private Date notValidAfter;
    
    // auditing information about creating and last change
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date dateCreated;
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date dateLastEdit;
    
    @Column(nullable=false)
    private String certHash;
    
    @Column(nullable=false, columnDefinition = "TINYINT(1)")
    private boolean valid;
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * Returns transient certificate representation. Is converted from RAW byte array.
     * @return
     * @throws CertificateEncodingException
     * @throws CertificateException 
     */
    public X509Certificate getCert() throws CertificateEncodingException, CertificateException {
        if (cert==null){
            this.updateCert();
        }
        
        return cert;
    }

    /**
     * Update certificate object from RAW representation
     */
    public void updateCert() throws CertificateEncodingException, CertificateException {
        if (this.rawCert == null || this.rawCert.length==0){
            this.cert = null;
            return; 
        }
        
        // certificate factory according to X.509 std
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
            
        InputStream in = new ByteArrayInputStream(this.rawCert);
        this.cert = (X509Certificate)cf.generateCertificate(in);
    }
    
    /**
     * Sets raw certificate
     * @param cert 
     */
    public void setCert(X509Certificate cert) throws CertificateEncodingException {
        this.cert = cert;
        if (cert==null){
            this.rawCert = null;
        } else {
            this.rawCert = cert.getEncoded();
        }
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

    public Date getNotValidBefore() {
        notValidBefore = this.cert.getNotBefore();
        return notValidBefore;
    }

    public Date getNotValidAfter() {
        notValidAfter = this.cert.getNotAfter();
        return notValidAfter;
    }

    public String getDN() {
        return this.cert.getSubjectDN().toString();
    }

    public byte[] getRawCert() {
        return rawCert;
    }

    public String getCertHash() {
        return certHash;
    }

    public boolean isValid() {
        return valid;
    }
    
    public void setRawCert(byte[] rawCert) throws CertificateEncodingException, CertificateException {
        this.rawCert = rawCert;
        this.updateCert();
    }

    @Override
    public String toString() {
        return "SubscriberCertificate{" + "id=" + id + ", subscriber=" + subscriber + ", cert=" + cert + ", rawCert=" + rawCert + ", notValidBefore=" + notValidBefore + ", notValidAfter=" + notValidAfter + ", dateCreated=" + dateCreated + ", dateLastEdit=" + dateLastEdit + ", certHash=" + certHash + ", valid=" + valid + '}';
    }
}
