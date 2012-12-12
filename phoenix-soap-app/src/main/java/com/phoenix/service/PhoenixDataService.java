/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.db.Contactlist;
import com.phoenix.db.RemoteUser;
import com.phoenix.db.SubscriberCertificate;
import com.phoenix.db.Whitelist;
import com.phoenix.db.WhitelistDstObj;
import com.phoenix.db.opensips.Subscriber;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers data questions for endpoint.
 * @author ph4r05
 */
@Service
@Repository
public class PhoenixDataService {
    private static final Logger log = LoggerFactory.getLogger(PhoenixDataService.class);
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private X509TrustManager trustManager;

    /**
     * Returns SIP address from subscriber record
     * @param s
     * @return 
     */
    public static String getSIP(Subscriber s){
        if (s==null) return "";
        return (new StringBuilder().append(s.getUsername()).append("@").append(s.getDomain()).toString());
    }
    
    /**
     * Returns local subscriber from user SIP
     * @param sip
     * @return 
     */
    public Subscriber getLocalUser(String sip){
        try {
            if (sip==null){
                return null;
            }
            
            // build string with IN (...)
            String querySIP2ID = "SELECT u FROM Subscriber u WHERE CONCAT(u.username, '@', u.domain) = :sip";
            TypedQuery<Subscriber> query = em.createQuery(querySIP2ID, Subscriber.class);
            query.setParameter("sip", sip);
            // iterate over result set and add ID 
            List<Subscriber> resultList = query.getResultList();
            return resultList.isEmpty() ? null : resultList.get(0);
            
        } catch(Exception ex){
            log.info("Problem occurred during loading user from database", ex);
            return null;
        }
    }
    
    /**
     * Returns remote subscriber from user SIP
     * @param sip
     * @return 
     */
    public RemoteUser getRemoteUser(String sip){
        try {
            if (sip==null){
                return null;
            }
            
            // build string with IN (...)
            String querySIP2ID = "SELECT u FROM remoteUser u WHERE sip = :sip";
            TypedQuery<RemoteUser> query = em.createQuery(querySIP2ID, RemoteUser.class);
            query.setParameter("sip", sip);
            List<RemoteUser> resultList = query.getResultList();
            return resultList.isEmpty() ? null : resultList.get(0);
            
        } catch(Exception ex){
            log.info("Problem occurred during loading user from database", ex);
            return null;
        }
    }
    
    /**
     * Getys local user by its primary key
     * @param id
     * @return 
     */
    public Subscriber getLocalUser(Long id){
        try {
            if (id==null){
                return null;
            }
            
            return em.find(Subscriber.class, id.intValue());
        } catch(Exception ex){
            log.info("Problem occurred during loading user from database", ex);
            return null;
        }
    }
    
    /**
     * Answers basic question - are given users in whitelist of owner?
     * Groups and extern users are not considered now.
     * 
     * Result is mapping SIP -> Whitelist entry
     * @param owner
     * @param intern     if null, while whitelsit is loaded, otherwise only subs matching this
     * @param extern
     * @return 
     */
    public Map<String, Whitelist> getWhitelistForUsers(Subscriber owner, Collection<Subscriber> intern, Collection<RemoteUser> extern){
        Map<String, Whitelist> result = new HashMap<String, Whitelist>();
        
        // now loading whitelist entries from database for owner, for intern user destination
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT wl, s FROM whitelist wl");
        sb.append(" LEFT OUTER JOIN wl.dst.intern_user s");
        sb.append(" WHERE wl.src.intern_user=:owner");
        if (intern!=null){
            sb.append(" AND s IN :targets ");
        }
        // finally sort by domain and user
        sb.append(" ORDER BY s.domain, s.username");
        
        Query query = em.createQuery(sb.toString());
        query.setParameter("owner", owner);
        if (intern!=null){
            query.setParameter("targets", intern);
        }
        
        List<Object[]> resultList = query.getResultList();
        for(Object[] o : resultList){
            final Subscriber s = (Subscriber) o[1];
            final Whitelist wl = (Whitelist) o[0];
            result.put(getSIP(s), wl);
        }
        
        return result;
    }
    
    
    /**
     * Specialized method to just extract whitelist for one owner and one target subscriber,
     * if exists, otherwise returns null.
     * @param owner
     * @param target
     * @return 
     */
    public Whitelist getWhitelistForSubscriber(Subscriber owner, WhitelistDstObj target){
        if (owner==null || target==null){
            throw new IllegalArgumentException("Some of argument is NULL, what is forbidden");
        }
        
        // This old way didn't worked - it cannot match whole object if it has 
        // nulls in it... Comparisson with null object is NULL
//        String queryWhitelist = "SELECT wl FROM whitelist wl"
//                    + " WHERE wl.src.intern_user=:owner AND wl.dst=:target ";

        // now loading whitelist entries from database for owner, for intern user destination
        String queryWhitelist = "SELECT wl FROM whitelist wl"
                    + " WHERE wl.src.intern_user=:owner AND "
                + " (( wl.dst.intern_user IS NOT NULL AND wl.dst.intern_user=:iu)"
                + " OR (wl.dst.extern_user IS NOT NULL AND wl.dst.extern_user=:eu)"
                + " OR (wl.dst.intern_group IS NOT NULL AND wl.dst.intern_group=:ig))";
        TypedQuery<Whitelist> query = em.createQuery(queryWhitelist, Whitelist.class);
        query.setParameter("owner", owner);
        query.setParameter("iu", target.getIntern_user());
        query.setParameter("eu", target.getExtern_user());
        query.setParameter("ig", target.getIntern_group());
        List<Whitelist> resultList = query.getResultList();
        return resultList.isEmpty() ? null : resultList.get(0);
    }
    
    /**
     * Specialized method to just extract whitelist for one owner and one target subscriber,
     * if exists, otherwise returns null.
     * @param owner
     * @param target
     * @return 
     */
    public Whitelist getWhitelistForSubscriber(Subscriber owner, Subscriber target){
        return this.getWhitelistForSubscriber(owner, new WhitelistDstObj(target));
    }
    
    /**
     * Specialized method to just extract whitelist for one owner and one target subscriber,
     * if exists, otherwise returns null.
     * @param owner
     * @param target
     * @return 
     */
    public Whitelist getWhitelistForSubscriber(Subscriber owner, RemoteUser target){
        return this.getWhitelistForSubscriber(owner, new WhitelistDstObj(target));
    }
    
    /**
     * Specialized method to just extract contactlist for one owner and one target subscriber,
     * if exists, otherwise returns null.
     * @param owner
     * @param target
     * @return 
     */
    public Contactlist getContactlistForSubscriber(Subscriber owner, Subscriber target){
        if (owner==null || target==null){
            throw new IllegalArgumentException("Some of argument is NULL, what is forbidden");
        }
        
        // now loading whitelist entries from database for owner, for intern user destination
        String queryGet = "SELECT cl FROM contactlist cl "
                    + " WHERE cl.owner=:owner AND cl.obj.intern_user=:target";
        TypedQuery<Contactlist> query = em.createQuery(queryGet, Contactlist.class);
        query.setParameter("owner", owner);
        query.setParameter("target", target);
        List<Contactlist> resultList = query.getResultList();
        return resultList.isEmpty() ? null : resultList.get(0);
    }
    
    /**
     * Returns remote subscriber from user SIP
     * @param sip
     * @return 
     */
    public SubscriberCertificate getCertificateForUser(Subscriber s){
        try {
            if (s==null) {
                throw new NullPointerException("Passed null subscriber");
            }
            
            // build string with 
            String querySIP2ID = "SELECT u FROM subscriberCertificate u WHERE subscriber = :s";
            TypedQuery<SubscriberCertificate> query = em.createQuery(querySIP2ID, SubscriberCertificate.class);
            query.setParameter("s", s);
            List<SubscriberCertificate> resultList = query.getResultList();
            return resultList.isEmpty() ? null : resultList.get(0);
            
        } catch(Exception ex){
            log.info("Problem occurred during loading user from database", ex);
            return null;
        }
    }
    
    @Transactional
    public void persist(Object o){
        this.persist(o, false);
    }
    
    @Transactional
    public void persist(Object o, boolean flush){
        this.em.persist(o);
        if (flush){
            this.em.flush();
        }
    }
    
    @Transactional
    public void remove(Object o, boolean flush){
        this.em.remove(o);
        if (flush){
            this.em.flush();
        }
    }
    
    /**
     * Unwraps hibernate session from JPA 2
     * @return 
     */
    public Session getHibernateSession(){
         HibernateEntityManager hem = em.unwrap(HibernateEntityManager.class);
         return hem.getSession();
    }
    
    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public EndpointAuth getAuth() {
        return auth;
    }

    public void setAuth(EndpointAuth auth) {
        this.auth = auth;
    }

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    public X509TrustManager getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(X509TrustManager trustManager) {
        this.trustManager = trustManager;
    }
}
