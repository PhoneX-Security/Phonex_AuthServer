package com.phoenix.db.opensips;

// Generated Dec 2, 2012 7:18:24 PM by Hibernate Tools 3.4.0.CR1

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import static javax.persistence.GenerationType.IDENTITY;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * DrGateways generated by hbm2java
 */
@Entity
@Table(name = "dr_gateways")
public class DrGateways implements java.io.Serializable {

	private Integer gwid;
	private int type;
	private String address;
	private int strip;
	private String priPrefix;
	private String attrs;
	private int probeMode;
	private String description;

	public DrGateways() {
	}

	public DrGateways(int type, String address, int strip, int probeMode,
			String description) {
		this.type = type;
		this.address = address;
		this.strip = strip;
		this.probeMode = probeMode;
		this.description = description;
	}

	public DrGateways(int type, String address, int strip, String priPrefix,
			String attrs, int probeMode, String description) {
		this.type = type;
		this.address = address;
		this.strip = strip;
		this.priPrefix = priPrefix;
		this.attrs = attrs;
		this.probeMode = probeMode;
		this.description = description;
	}

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "gwid", unique = true, nullable = false)
	public Integer getGwid() {
		return this.gwid;
	}

	public void setGwid(Integer gwid) {
		this.gwid = gwid;
	}

	@Column(name = "type", nullable = false)
	public int getType() {
		return this.type;
	}

	public void setType(int type) {
		this.type = type;
	}

	@Column(name = "address", nullable = false, length = 128)
	public String getAddress() {
		return this.address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	@Column(name = "strip", nullable = false)
	public int getStrip() {
		return this.strip;
	}

	public void setStrip(int strip) {
		this.strip = strip;
	}

	@Column(name = "pri_prefix", length = 16)
	public String getPriPrefix() {
		return this.priPrefix;
	}

	public void setPriPrefix(String priPrefix) {
		this.priPrefix = priPrefix;
	}

	@Column(name = "attrs")
	public String getAttrs() {
		return this.attrs;
	}

	public void setAttrs(String attrs) {
		this.attrs = attrs;
	}

	@Column(name = "probe_mode", nullable = false)
	public int getProbeMode() {
		return this.probeMode;
	}

	public void setProbeMode(int probeMode) {
		this.probeMode = probeMode;
	}

	@Column(name = "description", nullable = false, length = 128)
	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
