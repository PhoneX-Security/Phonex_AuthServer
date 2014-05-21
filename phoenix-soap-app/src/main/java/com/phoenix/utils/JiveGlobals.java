/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phoenix.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/**
 * Controls Jive properties. Jive properties are only meant to be set and retrieved
 * by core Jive classes. Some properties may be stored in XML format while others in the
 * database.<p>
 *
 * When starting up the application this class needs to be configured so that the initial
 * configuration of the application may be loaded from the configuration file. The configuration
 * file holds properties stored in XML format, database configuration and user authentication
 * configuration. Use {@link #setHomeDirectory(String)} and {@link #setConfigName(String)} for
 * setting the home directory and path to the configuration file.<p>
 *
 * XML property names must be in the form <code>prop.name</code> - parts of the name must
 * be separated by ".". The value can be any valid String, including strings with line breaks.
 */
@Service
@Scope(value = "singleton")
public class JiveGlobals {
    private static final Logger Log = LoggerFactory.getLogger(JiveGlobals.class);

    public boolean failedLoading = false;
    
    @Autowired
    private JiveProperties properties;
    
    public static boolean isSetupMode(){
        return false;
    }
    
    /**
     * Returns a Jive property.
     *
     * @param name the name of the property to return.
     * @return the property value specified by name.
     */
    public  String getProperty(String name) {
        if (properties == null) {
            if (isSetupMode()) {
                return null;
            }
            properties = JiveProperties.getInstance();
        }
        return properties.get(name);
    }

    /**
     * Returns a Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return the property value specified by name.
     */
    public  String getProperty(String name, String defaultValue) {
        if (properties == null) {
            if (isSetupMode()) {
                return defaultValue;
            }
            properties = JiveProperties.getInstance();
        }
        String value = properties.get(name);
        if (value != null) {
            return value;
        }
        else {
            return defaultValue;
        }
    }

    /**
     * Returns an integer value Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public  int getIntProperty(String name, int defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        return defaultValue;
    }

    /**
     * Returns a long value Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public  long getLongProperty(String name, long defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            try {
                return Long.parseLong(value);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        return defaultValue;
    }

    /**
     * Returns a boolean value Jive property.
     *
     * @param name the name of the property to return.
     * @return true if the property value exists and is set to <tt>"true"</tt> (ignoring case).
     *      Otherwise <tt>false</tt> is returned.
     */
    public  boolean getBooleanProperty(String name) {
        return Boolean.valueOf(getProperty(name));
    }

    /**
     * Returns a boolean value Jive property. If the property doesn't exist, the <tt>defaultValue</tt>
     * will be returned.
     *
     * If the specified property can't be found, or if the value is not a number, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return true if the property value exists and is set to <tt>"true"</tt> (ignoring case).
     *      Otherwise <tt>false</tt> is returned.
     */
    public  boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        else {
            return defaultValue;
        }
    }

    /**
     * Return all immediate children property names of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt> (<tt>C.D</tt> would not be returned using this method).<p>
     *
     * @param parent Parent "node" to find the children of.
     * @return a List of all immediate children property names (Strings).
     */
    public  List<String> getPropertyNames(String parent) {
        if (properties == null) {
            if (isSetupMode()) {
                return new ArrayList<String>();
            }
            properties = JiveProperties.getInstance();
        }
        return new ArrayList<String>(properties.getChildrenNames(parent));
    }

    /**
     * Return all immediate children property values of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and
     * <tt>X.Y.C</tt> (the value of <tt>X.Y.C.D</tt> would not be returned using this method).<p>
     *
     * @param parent the name of the parent property to return the children for.
     * @return all child property values for the given parent.
     */
    public  List<String> getProperties(String parent) {
        if (properties == null) {
            if (isSetupMode()) {
                return new ArrayList<String>();
            }
            properties = JiveProperties.getInstance();
        }

        Collection<String> propertyNames = properties.getChildrenNames(parent);
        List<String> values = new ArrayList<String>();
        for (String propertyName : propertyNames) {
            String value = getProperty(propertyName);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    /**
     * Returns all Jive property names.
     *
     * @return a List of all property names (Strings).
     */
    public  List<String> getPropertyNames() {
        if (properties == null) {
            if (isSetupMode()) {
                return new ArrayList<String>();
            }
            properties = JiveProperties.getInstance();
        }
        return new ArrayList<String>(properties.getPropertyNames());
    }

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created.
     *
     * @param name the name of the property being set.
     * @param value the value of the property being set.
     */
    public  void setProperty(String name, String value) {
        if (properties == null) {
            if (isSetupMode()) {
                return;
            }
            properties = JiveProperties.getInstance();
        }
        properties.put(name, value);
    }

   /**
     * Sets multiple Jive properties at once. If a property doesn't already exists, a new
     * one will be created.
     *
     * @param propertyMap a map of properties, keyed on property name.
     */
    public  void setProperties(Map<String, String> propertyMap) {
        if (properties == null) {
            if (isSetupMode()) {
                return;
            }
            properties = JiveProperties.getInstance();
        }

        properties.putAll(propertyMap);
    }

    /**
     * Deletes a Jive property. If the property doesn't exist, the method
     * does nothing. All children of the property will be deleted as well.
     *
     * @param name the name of the property to delete.
     */
    public  void deleteProperty(String name) {
        if (properties == null) {
            if (isSetupMode()) {
                return;
            }
            properties = JiveProperties.getInstance();
        }
        properties.remove(name);
    }

    /**
     * Read and re-write a given property to reset its encryption status
     * @param propertyName
     */
    private  boolean resetProperty(String propertyName) {
    	if (properties != null) {
			String propertyValue = properties.get(propertyName);
			if (propertyValue != null) {
				properties.remove(propertyName);
				properties.put(propertyName, propertyValue);
				return true;
			}
    	}
		return false;
    }
    
    /**
     * Re-loads properties from DB backend and notifies about changes.
     * Sync is performed only if object already exists, not for newly created
     * (in this method) object.
     * @return 
     */
    public  boolean syncProperties() {
        if (properties == null) {
            if (isSetupMode()) {
                return false;
            }
            properties = JiveProperties.getInstance();
            return false;
        } else {
            properties.sync();
            return true;
        }
    }

    public JiveProperties getProperties() {
        return properties;
    }

    public void setProperties(JiveProperties properties) {
        this.properties = properties;
    }
}