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
import org.hibernate.annotations.Index;

/**
 * CA database of signed certificates, revocations.
 * @author ph4r05
 */
@Entity(name = "CAcertsSigned") 
public class CAcertsSigned implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "serial", nullable = false)
    private Long serial;
    
    /**
     * Subscriber may be null in special cases if we are not signing certificate 
     * to somebody but to somewhat. 
     */
    @ManyToOne
    @JoinColumn(name="subscriber_id", nullable=true, unique=false)
    private Subscriber subscriber;
    
    @Transient
    private X509Certificate cert;
    
    @Column(nullable = false)
    @Index(name="hash")
    private String certHash;
    
    @Lob
    @Column(name = "cert", nullable = false)
    private byte[] rawCert;

    // pre-cache some meta data here - for database search purposes
    @Column(nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date notValidBefore;
    @Column(nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date notValidAfter;
    
    @Column(name = "dname", nullable = false)
    private String DN;
    @Column(name = "cname", nullable = false)
    private String CN;
    
    // auditing information about creating and last change
    @Column(nullable = false)
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date dateSigned;
    
    @Column(name = "isRevoked", nullable = false, columnDefinition = "TINYINT(1)")
    private Boolean isRevoked=false;
    
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    @Column(nullable = true)
    private Date dateRevoked;
    
    @Column(nullable = true)
    private String revokedReason;

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
    
    public Long getSerial() {
        return serial;
    }

    public void setSerial(Long serial) {
        this.serial = serial;
    }

    public Subscriber getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    public X509Certificate getCert() throws CertificateEncodingException, CertificateException {
        if (cert==null){
            this.updateCert();
        }
        
        return cert;
    }

    public String getCertHash() {
        return certHash;
    }

    public void setCertHash(String certHash) {
        this.certHash = certHash;
    }

    public byte[] getRawCert() {
        return rawCert;
    }

    public void setRawCert(byte[] rawCert) throws CertificateEncodingException, CertificateException {
        this.rawCert = rawCert;
        this.updateCert();
    }

    public Date getNotValidBefore() {
        return notValidBefore;
    }

    public void setNotValidBefore(Date notValidBefore) {
        this.notValidBefore = notValidBefore;
    }

    public Date getNotValidAfter() {
        return notValidAfter;
    }

    public void setNotValidAfter(Date notValidAfter) {
        this.notValidAfter = notValidAfter;
    }

    public String getDN() {
        return DN;
    }

    public void setDN(String DN) {
        this.DN = DN;
    }

    public String getCN() {
        return CN;
    }

    public void setCN(String CN) {
        this.CN = CN;
    }

    public Date getDateSigned() {
        return dateSigned;
    }

    public void setDateSigned(Date dateSigned) {
        this.dateSigned = dateSigned;
    }

    public Boolean getIsRevoked() {
        return isRevoked;
    }

    public void setIsRevoked(Boolean isRevoked) {
        this.isRevoked = isRevoked;
    }

    public Date getDateRevoked() {
        return dateRevoked;
    }

    public void setDateRevoked(Date dateRevoked) {
        this.dateRevoked = dateRevoked;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }
    
    @Override
    public String toString() {
        return "CAcertsSigned{" + "serial=" + serial + ", subscriber=" + subscriber + ", cert=" + cert + ", certHash=" + certHash + ", rawCert=" + rawCert + ", notValidBefore=" + notValidBefore + ", notValidAfter=" + notValidAfter + ", DN=" + DN + ", CN=" + CN + ", dateSigned=" + dateSigned + ", isRevoked=" + isRevoked + ", dateRevoked=" + dateRevoked + ", revokedReason=" + revokedReason + '}';
    }
}
