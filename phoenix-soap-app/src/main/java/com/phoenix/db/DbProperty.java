/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.phoenix.db;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

/**
 *
 * @author ph4r05
 */
@Entity
@Table(name = "dbproperty")
public class DbProperty implements Serializable {
    @Id
    @Column(name="name", nullable = false, columnDefinition = "VARCHAR(128)")
    private String name;  
    
    @Lob
    @Column(name="propValue", nullable = false)
    private String propValue;

    public DbProperty() {
    }
    
    public DbProperty(String name, String propValue) {
        this.name = name;
        this.propValue = propValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPropValue() {
        return propValue;
    }

    public void setPropValue(String propValue) {
        this.propValue = propValue;
    }

    @Override
    public String toString() {
        return "DbProperty{" + "name=" + name + ", propValue=" + propValue + '}';
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 29 * hash + (this.propValue != null ? this.propValue.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DbProperty other = (DbProperty) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        if ((this.propValue == null) ? (other.propValue != null) : !this.propValue.equals(other.propValue)) {
            return false;
        }
        return true;
    }
}
