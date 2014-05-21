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

import com.phoenix.db.DbProperty;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.hibernate.SessionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retrieves and stores Jive properties. Properties are stored in the database.
 *
 * @author Matt Tucker
 */
@Service
@Repository
@Transactional
public class JiveProperties implements Map<String, String> {
    private static final Logger Log = LoggerFactory.getLogger(JiveProperties.class);

    private static final String LOAD_PROPERTIES = "SELECT d FROM DbProperty d";
    private static final String UPDATE_PROPERTY = "UPDATE DbProperty d SET d.propValue=? WHERE d.name=?";
    private static final String DELETE_PROPERTY = "DELETE FROM DbProperty d WHERE d.name LIKE ?";

    private static JiveProperties instance = null;

    private Map<String, String> properties;
    
    // Mutex for synchronization with DB backend.
    private final Object syncMutex = new Object();
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @PersistenceContext
    protected EntityManager em;

    /**
     * Returns a singleton instance of JiveProperties.
     *
     * @return an instance of JiveProperties.
     */
    public synchronized static JiveProperties getInstance() {
    	/*if (instance == null) {
    		JiveProperties props = new JiveProperties();
    		props.init();
    		instance = props;
    	}
        return instance;*/
        return null;
    }
    
    public JiveProperties() { }

    /**
     * For internal use only. This method allows for the reloading of all properties from the
     * values in the database. This is required since it's quite possible during the setup
     * process that a database connection will not be available till after this class is
     * initialized. Thus, if there are existing properties in the database we will want to reload
     * this class after the setup process has been completed.
     */
    @PostConstruct
    public void init() {
        if (properties == null) {
            properties = new ConcurrentHashMap<String, String>();
        }
        else {
            properties.clear();
        }

        loadProperties();
        Log.info("Properties initialized");
    }

    @Override
    public int size() {
        return properties.size();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return properties.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return properties.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return properties.containsValue(value);
    }

    @Override
    public Collection<String> values() {
        return Collections.unmodifiableCollection(properties.values());
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> t) {
        for (Map.Entry<? extends String, ? extends String> entry : t.entrySet() ) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<Map.Entry<String, String>> entrySet() {
        return Collections.unmodifiableSet(properties.entrySet());
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    @Override
    public String get(Object key) {
        return properties.get(key);
    }

    /**
     * Return all children property names of a parent property as a Collection
     * of String objects. For example, given the properties <tt>X.Y.A</tt>,
     * <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>, then the child properties of
     * <tt>X.Y</tt> are <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>. The method
     * is not recursive; ie, it does not return children of children.
     *
     * @param parentKey the name of the parent property.
     * @return all child property names for the given parent.
     */
    public Collection<String> getChildrenNames(String parentKey) {
        Collection<String> results = new HashSet<String>();
        for (String key : properties.keySet()) {
            if (key.startsWith(parentKey + ".")) {
                if (key.equals(parentKey)) {
                    continue;
                }
                int dotIndex = key.indexOf(".", parentKey.length()+1);
                if (dotIndex < 1) {
                    if (!results.contains(key)) {
                        results.add(key);
                    }
                }
                else {
                    String name = parentKey + key.substring(parentKey.length(), dotIndex);
                    results.add(name);
                }
            }
        }
        return results;
    }

    /**
     * Returns all property names as a Collection of String values.
     *
     * @return all property names.
     */
    public Collection<String> getPropertyNames() {
        return properties.keySet();
    }

    /**
     *
     * @param key
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    @Override
    public String remove(Object key) {
        String value;
        synchronized (this) {
            value = properties.remove(key);
            // Also remove any children.
            Collection<String> propNames = getPropertyNames();
            for (String name : propNames) {
                if (name.startsWith((String)key)) {
                    properties.remove(name);
                }
            }
            deleteProperty((String)key);
        }

        // Generate event.
        Map<String, Object> params = Collections.emptyMap();
        PropertyEventDispatcher.dispatchEvent((String)key, PropertyEventDispatcher.EventType.property_deleted, params);

        // Send update to other cluster members.
        //CacheFactory.doClusterTask(PropertyClusterEventTask.createDeleteTask((String) key));

        return value;
    }

    void localRemove(String key) {
        properties.remove(key);
        // Also remove any children.
        Collection<String> propNames = getPropertyNames();
        for (String name : propNames) {
            if (name.startsWith(key)) {
                properties.remove(name);
            }
        }

        // Generate event.
        Map<String, Object> params = Collections.emptyMap();
        PropertyEventDispatcher.dispatchEvent(key, PropertyEventDispatcher.EventType.property_deleted, params);
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    @Override
    public String put(String key, String value) {
        if (value == null) {
            // This is the same as deleting, so remove it.
            return remove(key);
        }
        if (key == null) {
            throw new NullPointerException("Key cannot be null. Key=" +
                    key + ", value=" + value);
        }
        if (key.endsWith(".")) {
            key = key.substring(0, key.length()-1);
        }
        key = key.trim();
        String result;
        synchronized (this) {
            if (properties.containsKey(key)) {
                if (!properties.get(key).equals(value)) {
                    updateProperty(key, value);
                }
            }
            else {
                insertProperty(key, value);
            }

            result = properties.put(key, value);
        }

        // Generate event.
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("value", value);
        PropertyEventDispatcher.dispatchEvent(key, PropertyEventDispatcher.EventType.property_set, params);

        // Send update to other cluster members.
        //CacheFactory.doClusterTask(PropertyClusterEventTask.createPutTask(key, value));

        return result;
    }

    void localPut(String key, String value) {
        properties.put(key, value);

        // Generate event.
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("value", value);
        PropertyEventDispatcher.dispatchEvent(key, PropertyEventDispatcher.EventType.property_set, params);
    }

    public String getProperty(String name, String defaultValue) {
        String value = properties.get(name);
        if (value != null) {
            return value;
        }
        else {
            return defaultValue;
        }
    }

    public boolean getBooleanProperty(String name) {
        return Boolean.valueOf(get(name));
    }

    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = get(name);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        else {
            return defaultValue;
        }
    }

    private void insertProperty(String name, String value) {
        try {
            DbProperty prop = new DbProperty(name, value);
            em.persist(prop);
            em.flush();
            Log.info("Inserted name=" + name);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    private void updateProperty(String name, String value) {
        try {
            Query query = em.createQuery(UPDATE_PROPERTY);
            query.setParameter(1, value);
            query.setParameter(2, name);
            query.executeUpdate();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    private void deleteProperty(String name) {
        try {
            Query dq = em.createQuery(DELETE_PROPERTY);
            dq.setParameter(1, name + "%");
            dq.executeUpdate();
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    private void loadProperties() {
        try {
            TypedQuery<DbProperty> query = em.createQuery(LOAD_PROPERTIES, DbProperty.class);
            List<DbProperty> resultList = query.getResultList();
            if (resultList==null) return;
            
            for(DbProperty dbp : resultList){
                if (dbp != null && dbp.getPropValue()!=null) { 
                    properties.put(dbp.getName(), dbp.getPropValue()); 
                }
            }
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }
    
    /**
     * Tries to synchronize properties with the database. 
     * Assumption: data flow from application to database is complete,
     * this kind of transactions is commited to the database. 
     * Only situation with new data in database can occur. 
     */
    public void sync(){
        try {
            // Store objects that generate events.
            // Key -> updated? if not, then deleted.
            Map<String, Boolean> updatedProperties = new HashMap<String, Boolean>();
            
            // Has to be synchronized, we dont want any update to happen right now. 
            synchronized(this){
                // Copy current state of the properties to the temporary structure
                // to be able to tell whether there is some change or not.
                Map<String, String> oldProperties = new HashMap<String, String>(properties);
                
                // Load properties from the database.
                loadProperties();
                
                // Iterate over new properties, determine if something has changed.
                for(Entry<String, String> e : properties.entrySet()){
                    final String key=e.getKey();
                    final String val=e.getValue();

                    // Added
                    if (oldProperties.containsKey(key)==false){
                        updatedProperties.put(key, true);
                        continue;
                    }

                    // Updated
                    final String oldVal = oldProperties.get(key);
                    if ((val!=null && val.equals(oldVal)==false) || (val==null && oldVal!=null)){
                        updatedProperties.put(key, true);
                    }
                }
                
                // Look for deleted entries = present in old properties but not 
                // in current ones.
                for(Entry<String, String> e : oldProperties.entrySet()){
                    final String key=e.getKey();
                    if (properties.containsKey(key)==false){
                        updatedProperties.put(key, false);
                    }
                }
            } 
            
            // Generate property changed event if applicable.
            if (!updatedProperties.isEmpty()){
                for(Entry<String, Boolean> e : updatedProperties.entrySet()){
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("value", properties.get(e.getKey()));
                    PropertyEventDispatcher.dispatchEvent(
                            e.getKey(), 
                            e.getValue() ? 
                                    PropertyEventDispatcher.EventType.property_set : 
                                    PropertyEventDispatcher.EventType.property_deleted, 
                            params);
                }
            }
        } catch(Exception e){
            Log.error(e.getMessage(), e);
        }
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }
}