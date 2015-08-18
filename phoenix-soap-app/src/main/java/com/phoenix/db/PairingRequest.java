package com.phoenix.db;

import com.phoenix.db.extra.ContactlistStatus;
import com.phoenix.db.extra.PairingRequestResolution;
import com.phoenix.db.opensips.Subscriber;

import javax.persistence.*;
import java.util.Date;

/**
 * Pairing request, "add me to your contact list" request.
 * @author ph4r05
 */
@Entity(name = "pairingRequest")
public class PairingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;

    // owner of this contact list
//    @JoinTable(name = "Subscriber", joinColumns = { @JoinColumn(name = "id", nullable = false) })
    @Column(name = "to_user", nullable = false, length = 255)
    private String toUser;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "tstamp", nullable = false, columnDefinition = "TIMESTAMP")
    private Date tstamp;

    @Column(name = "from_user", nullable = false, length = 255)
    private String fromUser;

    @Column(name = "from_user_resource", nullable = false, length = 64)
    private String fromUserResource;

    @Lob
    @Column(name = "from_user_aux", nullable = true, columnDefinition="TEXT")
    private String fromUserAux;

    @Lob
    @Column(name = "request_message", nullable = true, columnDefinition="TEXT")
    private String requestMessage;

    @Lob
    @Column(name = "request_aux", nullable = true, columnDefinition="TEXT")
    private String requestAux;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition="char(20) default 'NONE'")
    private PairingRequestResolution resolution;

    @Column(name = "resolution_resource", nullable = true, length = 64)
    private String resolutionResource;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "resolution_tstamp", nullable = true, columnDefinition = "TIMESTAMP")
    private Date resolutionTstamp;

    @Lob
    @Column(name = "resolution_message", nullable = true, columnDefinition="TEXT")
    private String resolutionMessage;

    @Lob
    @Column(name = "resolution_aux", nullable = true, columnDefinition="TEXT")
    private String resolutionAux;

    public PairingRequest() {
    }

    public PairingRequest(Long id, String toUser, Date tstamp, String fromUser, String fromUserResource, String fromUserAux, String requestMessage, String requestAux, PairingRequestResolution resolution, Date resolutionTstamp, String resolutionMessage, String resolutionAux) {
        this.id = id;
        this.toUser = toUser;
        this.tstamp = tstamp;
        this.fromUser = fromUser;
        this.fromUserResource = fromUserResource;
        this.fromUserAux = fromUserAux;
        this.requestMessage = requestMessage;
        this.requestAux = requestAux;
        this.resolution = resolution;
        this.resolutionTstamp = resolutionTstamp;
        this.resolutionMessage = resolutionMessage;
        this.resolutionAux = resolutionAux;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToUser() {
        return toUser;
    }

    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public Date getTstamp() {
        return tstamp;
    }

    public void setTstamp(Date tstamp) {
        this.tstamp = tstamp;
    }

    public String getFromUser() {
        return fromUser;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public String getFromUserResource() {
        return fromUserResource;
    }

    public void setFromUserResource(String fromUserResource) {
        this.fromUserResource = fromUserResource;
    }

    public String getFromUserAux() {
        return fromUserAux;
    }

    public void setFromUserAux(String fromUserAux) {
        this.fromUserAux = fromUserAux;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(String requestMessage) {
        this.requestMessage = requestMessage;
    }

    public String getRequestAux() {
        return requestAux;
    }

    public void setRequestAux(String requestAux) {
        this.requestAux = requestAux;
    }

    public PairingRequestResolution getResolution() {
        return resolution;
    }

    public void setResolution(PairingRequestResolution resolution) {
        this.resolution = resolution;
    }

    public Date getResolutionTstamp() {
        return resolutionTstamp;
    }

    public void setResolutionTstamp(Date resolutionTstamp) {
        this.resolutionTstamp = resolutionTstamp;
    }

    public String getResolutionMessage() {
        return resolutionMessage;
    }

    public void setResolutionMessage(String resolutionMessage) {
        this.resolutionMessage = resolutionMessage;
    }

    public String getResolutionAux() {
        return resolutionAux;
    }

    public void setResolutionAux(String resolutionAux) {
        this.resolutionAux = resolutionAux;
    }

    public String getResolutionResource() {
        return resolutionResource;
    }

    public void setResolutionResource(String resolutionResource) {
        this.resolutionResource = resolutionResource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PairingRequest that = (PairingRequest) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (!toUser.equals(that.toUser)) return false;
        if (!tstamp.equals(that.tstamp)) return false;
        if (!fromUser.equals(that.fromUser)) return false;
        if (fromUserResource != null ? !fromUserResource.equals(that.fromUserResource) : that.fromUserResource != null)
            return false;
        if (fromUserAux != null ? !fromUserAux.equals(that.fromUserAux) : that.fromUserAux != null) return false;
        if (requestMessage != null ? !requestMessage.equals(that.requestMessage) : that.requestMessage != null)
            return false;
        if (requestAux != null ? !requestAux.equals(that.requestAux) : that.requestAux != null) return false;
        if (resolution != that.resolution) return false;
        if (resolutionTstamp != null ? !resolutionTstamp.equals(that.resolutionTstamp) : that.resolutionTstamp != null)
            return false;
        return !(resolutionMessage != null ? !resolutionMessage.equals(that.resolutionMessage) : that.resolutionMessage != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + toUser.hashCode();
        result = 31 * result + tstamp.hashCode();
        result = 31 * result + fromUser.hashCode();
        result = 31 * result + (fromUserResource != null ? fromUserResource.hashCode() : 0);
        result = 31 * result + (fromUserAux != null ? fromUserAux.hashCode() : 0);
        result = 31 * result + (requestMessage != null ? requestMessage.hashCode() : 0);
        result = 31 * result + (requestAux != null ? requestAux.hashCode() : 0);
        result = 31 * result + resolution.hashCode();
        result = 31 * result + (resolutionTstamp != null ? resolutionTstamp.hashCode() : 0);
        result = 31 * result + (resolutionMessage != null ? resolutionMessage.hashCode() : 0);
        return result;
    }

}
