package com.phoenix.db;

// Generated Dec 2, 2012 7:18:24 PM by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Watchers generated by hbm2java
 */
@Entity
@Table(name = "watchers", catalog = "phoenixdb", uniqueConstraints = @UniqueConstraint(columnNames = {
		"presentity_uri", "watcher_username", "watcher_domain", "event" }))
public class Watchers implements java.io.Serializable {

	private Integer id;
	private String presentityUri;
	private String watcherUsername;
	private String watcherDomain;
	private String event;
	private int status;
	private String reason;
	private int insertedTime;

	public Watchers() {
	}

	public Watchers(String presentityUri, String watcherUsername,
			String watcherDomain, String event, int status, int insertedTime) {
		this.presentityUri = presentityUri;
		this.watcherUsername = watcherUsername;
		this.watcherDomain = watcherDomain;
		this.event = event;
		this.status = status;
		this.insertedTime = insertedTime;
	}

	public Watchers(String presentityUri, String watcherUsername,
			String watcherDomain, String event, int status, String reason,
			int insertedTime) {
		this.presentityUri = presentityUri;
		this.watcherUsername = watcherUsername;
		this.watcherDomain = watcherDomain;
		this.event = event;
		this.status = status;
		this.reason = reason;
		this.insertedTime = insertedTime;
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

	@Column(name = "presentity_uri", nullable = false, length = 128)
	public String getPresentityUri() {
		return this.presentityUri;
	}

	public void setPresentityUri(String presentityUri) {
		this.presentityUri = presentityUri;
	}

	@Column(name = "watcher_username", nullable = false, length = 64)
	public String getWatcherUsername() {
		return this.watcherUsername;
	}

	public void setWatcherUsername(String watcherUsername) {
		this.watcherUsername = watcherUsername;
	}

	@Column(name = "watcher_domain", nullable = false, length = 64)
	public String getWatcherDomain() {
		return this.watcherDomain;
	}

	public void setWatcherDomain(String watcherDomain) {
		this.watcherDomain = watcherDomain;
	}

	@Column(name = "event", nullable = false, length = 64)
	public String getEvent() {
		return this.event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	@Column(name = "status", nullable = false)
	public int getStatus() {
		return this.status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	@Column(name = "reason", length = 64)
	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	@Column(name = "inserted_time", nullable = false)
	public int getInsertedTime() {
		return this.insertedTime;
	}

	public void setInsertedTime(int insertedTime) {
		this.insertedTime = insertedTime;
	}

}
