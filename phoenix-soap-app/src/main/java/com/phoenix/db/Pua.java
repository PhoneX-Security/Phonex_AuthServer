package com.phoenix.db;

// Generated Dec 2, 2012 7:18:24 PM by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * Pua generated by hbm2java
 */
@Entity
@Table(name = "pua", catalog = "phoenixdb")
public class Pua implements java.io.Serializable {

	private Integer id;
	private Integer version;
	private String presUri;
	private String presId;
	private int event;
	private int expires;
	private int desiredExpires;
	private int flag;
	private String etag;
	private String tupleId;
	private String watcherUri;
	private String toUri;
	private String callId;
	private String toTag;
	private String fromTag;
	private Integer cseq;
	private String recordRoute;
	private String contact;
	private String remoteContact;
	private String extraHeaders;

	public Pua() {
	}

	public Pua(String presUri, String presId, int event, int expires,
			int desiredExpires, int flag) {
		this.presUri = presUri;
		this.presId = presId;
		this.event = event;
		this.expires = expires;
		this.desiredExpires = desiredExpires;
		this.flag = flag;
	}

	public Pua(String presUri, String presId, int event, int expires,
			int desiredExpires, int flag, String etag, String tupleId,
			String watcherUri, String toUri, String callId, String toTag,
			String fromTag, Integer cseq, String recordRoute, String contact,
			String remoteContact, String extraHeaders) {
		this.presUri = presUri;
		this.presId = presId;
		this.event = event;
		this.expires = expires;
		this.desiredExpires = desiredExpires;
		this.flag = flag;
		this.etag = etag;
		this.tupleId = tupleId;
		this.watcherUri = watcherUri;
		this.toUri = toUri;
		this.callId = callId;
		this.toTag = toTag;
		this.fromTag = fromTag;
		this.cseq = cseq;
		this.recordRoute = recordRoute;
		this.contact = contact;
		this.remoteContact = remoteContact;
		this.extraHeaders = extraHeaders;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id", unique = true, nullable = false)
	public Integer getId() {
		return this.id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Version
	@Column(name = "version")
	public Integer getVersion() {
		return this.version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	@Column(name = "pres_uri", nullable = false, length = 128)
	public String getPresUri() {
		return this.presUri;
	}

	public void setPresUri(String presUri) {
		this.presUri = presUri;
	}

	@Column(name = "pres_id", nullable = false)
	public String getPresId() {
		return this.presId;
	}

	public void setPresId(String presId) {
		this.presId = presId;
	}

	@Column(name = "event", nullable = false)
	public int getEvent() {
		return this.event;
	}

	public void setEvent(int event) {
		this.event = event;
	}

	@Column(name = "expires", nullable = false)
	public int getExpires() {
		return this.expires;
	}

	public void setExpires(int expires) {
		this.expires = expires;
	}

	@Column(name = "desired_expires", nullable = false)
	public int getDesiredExpires() {
		return this.desiredExpires;
	}

	public void setDesiredExpires(int desiredExpires) {
		this.desiredExpires = desiredExpires;
	}

	@Column(name = "flag", nullable = false)
	public int getFlag() {
		return this.flag;
	}

	public void setFlag(int flag) {
		this.flag = flag;
	}

	@Column(name = "etag", length = 64)
	public String getEtag() {
		return this.etag;
	}

	public void setEtag(String etag) {
		this.etag = etag;
	}

	@Column(name = "tuple_id", length = 64)
	public String getTupleId() {
		return this.tupleId;
	}

	public void setTupleId(String tupleId) {
		this.tupleId = tupleId;
	}

	@Column(name = "watcher_uri", length = 128)
	public String getWatcherUri() {
		return this.watcherUri;
	}

	public void setWatcherUri(String watcherUri) {
		this.watcherUri = watcherUri;
	}

	@Column(name = "to_uri", length = 128)
	public String getToUri() {
		return this.toUri;
	}

	public void setToUri(String toUri) {
		this.toUri = toUri;
	}

	@Column(name = "call_id", length = 64)
	public String getCallId() {
		return this.callId;
	}

	public void setCallId(String callId) {
		this.callId = callId;
	}

	@Column(name = "to_tag", length = 64)
	public String getToTag() {
		return this.toTag;
	}

	public void setToTag(String toTag) {
		this.toTag = toTag;
	}

	@Column(name = "from_tag", length = 64)
	public String getFromTag() {
		return this.fromTag;
	}

	public void setFromTag(String fromTag) {
		this.fromTag = fromTag;
	}

	@Column(name = "cseq")
	public Integer getCseq() {
		return this.cseq;
	}

	public void setCseq(Integer cseq) {
		this.cseq = cseq;
	}

	@Column(name = "record_route", length = 65535)
	public String getRecordRoute() {
		return this.recordRoute;
	}

	public void setRecordRoute(String recordRoute) {
		this.recordRoute = recordRoute;
	}

	@Column(name = "contact", length = 128)
	public String getContact() {
		return this.contact;
	}

	public void setContact(String contact) {
		this.contact = contact;
	}

	@Column(name = "remote_contact", length = 128)
	public String getRemoteContact() {
		return this.remoteContact;
	}

	public void setRemoteContact(String remoteContact) {
		this.remoteContact = remoteContact;
	}

	@Column(name = "extra_headers", length = 65535)
	public String getExtraHeaders() {
		return this.extraHeaders;
	}

	public void setExtraHeaders(String extraHeaders) {
		this.extraHeaders = extraHeaders;
	}

}
