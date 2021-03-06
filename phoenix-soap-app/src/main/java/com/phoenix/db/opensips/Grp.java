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
import javax.persistence.UniqueConstraint;

/**
 * Grp generated by hbm2java
 */
@Entity
@Table(name = "grp", uniqueConstraints = @UniqueConstraint(columnNames = {
		"username", "domain", "grp" }))
public class Grp implements java.io.Serializable {

	private Integer id;
	private String username;
	private String domain;
	private String grp;
	private Date lastModified;

	public Grp() {
	}

	public Grp(String username, String domain, String grp, Date lastModified) {
		this.username = username;
		this.domain = domain;
		this.grp = grp;
		this.lastModified = lastModified;
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

	@Column(name = "username", nullable = false, length = 64)
	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Column(name = "domain", nullable = false, length = 64)
	public String getDomain() {
		return this.domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	@Column(name = "grp", nullable = false, length = 64)
	public String getGrp() {
		return this.grp;
	}

	public void setGrp(String grp) {
		this.grp = grp;
	}

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "last_modified", nullable = false, length = 19)
	public Date getLastModified() {
		return this.lastModified;
	}

	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}

}
