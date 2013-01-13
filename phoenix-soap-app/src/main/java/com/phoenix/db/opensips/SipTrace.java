package com.phoenix.db.opensips;

// Generated Dec 2, 2012 7:18:24 PM by Hibernate Tools 3.4.0.CR1

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * SipTrace generated by hbm2java
 */
@Entity
@Table(name = "sip_trace")
public class SipTrace implements java.io.Serializable {

	private Integer id;
	private Date timeStamp;
	private String callid;
	private String tracedUser;
	private String msg;
	private String method;
	private String status;
	private String fromip;
	private String toip;
	private String fromtag;
	private String direction;

	public SipTrace() {
	}

	public SipTrace(Date timeStamp, String callid, String msg, String method,
			String fromip, String toip, String fromtag, String direction) {
		this.timeStamp = timeStamp;
		this.callid = callid;
		this.msg = msg;
		this.method = method;
		this.fromip = fromip;
		this.toip = toip;
		this.fromtag = fromtag;
		this.direction = direction;
	}

	public SipTrace(Date timeStamp, String callid, String tracedUser,
			String msg, String method, String status, String fromip,
			String toip, String fromtag, String direction) {
		this.timeStamp = timeStamp;
		this.callid = callid;
		this.tracedUser = tracedUser;
		this.msg = msg;
		this.method = method;
		this.status = status;
		this.fromip = fromip;
		this.toip = toip;
		this.fromtag = fromtag;
		this.direction = direction;
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

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "time_stamp", nullable = false, length = 19)
	public Date getTimeStamp() {
		return this.timeStamp;
	}

	public void setTimeStamp(Date timeStamp) {
		this.timeStamp = timeStamp;
	}

	@Column(name = "callid", nullable = false)
	public String getCallid() {
		return this.callid;
	}

	public void setCallid(String callid) {
		this.callid = callid;
	}

	@Column(name = "traced_user", length = 128)
	public String getTracedUser() {
		return this.tracedUser;
	}

	public void setTracedUser(String tracedUser) {
		this.tracedUser = tracedUser;
	}

	@Column(name = "msg", nullable = false, length = 65535)
	public String getMsg() {
		return this.msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	@Column(name = "method", nullable = false, length = 32)
	public String getMethod() {
		return this.method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	@Column(name = "status", length = 128)
	public String getStatus() {
		return this.status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Column(name = "fromip", nullable = false, length = 50)
	public String getFromip() {
		return this.fromip;
	}

	public void setFromip(String fromip) {
		this.fromip = fromip;
	}

	@Column(name = "toip", nullable = false, length = 50)
	public String getToip() {
		return this.toip;
	}

	public void setToip(String toip) {
		this.toip = toip;
	}

	@Column(name = "fromtag", nullable = false, length = 64)
	public String getFromtag() {
		return this.fromtag;
	}

	public void setFromtag(String fromtag) {
		this.fromtag = fromtag;
	}

	@Column(name = "direction", nullable = false, length = 4)
	public String getDirection() {
		return this.direction;
	}

	public void setDirection(String direction) {
		this.direction = direction;
	}

}
