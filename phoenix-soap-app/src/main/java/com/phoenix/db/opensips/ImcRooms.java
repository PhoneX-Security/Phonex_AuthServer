package com.phoenix.db.opensips;

// Generated Dec 2, 2012 7:18:24 PM by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * ImcRooms generated by hbm2java
 */
@Entity
@Table(name = "imc_rooms", uniqueConstraints = @UniqueConstraint(columnNames = {
		"name", "domain" }))
public class ImcRooms implements java.io.Serializable {

	private Integer id;
	private String name;
	private String domain;
	private int flag;

	public ImcRooms() {
	}

	public ImcRooms(String name, String domain, int flag) {
		this.name = name;
		this.domain = domain;
		this.flag = flag;
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

	@Column(name = "name", nullable = false, length = 64)
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Column(name = "domain", nullable = false, length = 64)
	public String getDomain() {
		return this.domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	@Column(name = "flag", nullable = false)
	public int getFlag() {
		return this.flag;
	}

	public void setFlag(int flag) {
		this.flag = flag;
	}

}
