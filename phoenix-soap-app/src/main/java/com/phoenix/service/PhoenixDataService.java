/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.db.CAcertsSigned;
import com.phoenix.db.Contactlist;
import com.phoenix.db.OneTimeToken;
import com.phoenix.db.RemoteUser;
import com.phoenix.db.Whitelist;
import com.phoenix.db.WhitelistDstObj;
import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.opensips.Subscriber;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.bouncycastle.util.encoders.Base64;
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
    public static final String PRESENCE_RULES_TEMPLATE = "pres-rules-template.tpl";
    
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
            query.setParameter("sip", sip).setMaxResults(1);
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
     * Loads all subscribers in internal contact list.
     * @param owner
     * @param target
     * @return 
     */
    public List<Contactlist> getContactlistForSubscriber(Subscriber owner){
        if (owner==null){
            throw new IllegalArgumentException("Some of argument is NULL, what is forbidden");
        }
        
        // now loading whitelist entries from database for owner, for intern user destination
        String queryGet = "SELECT cl FROM contactlist cl "
                    + " WHERE cl.owner=:owner";
        TypedQuery<Contactlist> query = em.createQuery(queryGet, Contactlist.class);
        query.setParameter("owner", owner);
        List<Contactlist> resultList = query.getResultList();
        return resultList;
    }
    
    /**
     * Fetches internal user from contact list and returns mapping subscriber id -> subscriber.
     * @param clist
     * @return 
     */
    public Map<Integer, Subscriber> getInternalUsersInContactlist(List<Contactlist> clist){
        if (clist==null){
            throw new NullPointerException("contact list cannot be empty");
        }
        
        Map<Integer, Subscriber> ret = new HashMap<Integer, Subscriber>();
        Set<Integer> usr2load = new HashSet<Integer>();
        for(Contactlist ce : clist){
            ContactlistObjType ctype = ce.getObjType();
            if (ctype!=ContactlistObjType.INTERNAL_USER){
                continue;
            }
            
            Subscriber s = ce.getObj().getIntern_user();
            if (s==null){
                log.error("User is internal and still has null subscriber: " + ce.toString());
                continue;
            }
            
            ret.put(s.getId(), s);
        }
        
        return ret;
    }
    
    /**
     * Loads presence rules policy template from resources.
     * @param sips
     * @return 
     */
    public String loadPresenceRulesPolicyTemplate() throws IOException{
        InputStream resourceAsStream = PhoenixDataService.class.getClassLoader().getResourceAsStream(PRESENCE_RULES_TEMPLATE);
        return convertStreamToStr(resourceAsStream);
    }
    
    /**
     * Parses input template and builds new template adding all sips to the whitelist.
     * @param sips
     * @return 
     */
    public String completePresenceRulesPolicyTemplate(String template, List<String> sips){
        if (sips==null){
            throw new NullPointerException("Sips list is null");
        }
        
        StringBuilder sb = new StringBuilder();
        for(String s : sips){
            // check string s for valid characters
            if (s.matches("[a-zA-Z0-9_\\-]+@[a-zA-Z0-9\\._\\-]+")==false){
                log.warn("Illegal sip passed: " + s);
                continue;
            }
            sb.append("                   <cp:one id=\"sip:").append(s).append("\" />\n");
        }
        
        return template.replace("[[[RULES]]]", sb.toString());
    }
    
    /**
     * Returns true if user has stored certificate with this hash and it is valid
     * @param s
     * @param hash
     * @return 
     */
    public boolean isProvidedHashValid(Subscriber s, String hash){
        try {
            if (s==null) {
                throw new NullPointerException("Passed null subscriber");
            }
            
            // build string with 
            String querySIP2ID = "SELECT 1 FROM CAcertsSigned cs "
                    + " WHERE subscriber=:s AND certHash=:h "
                    + " AND isRevoked=false "
                    + " AND cs.notValidAfter>:n ";
            Query query = em.createQuery(querySIP2ID);
            query.setParameter("s", s).setParameter("h", hash).setParameter("n", new Date());
            query.setMaxResults(1);
            List resultList = query.getResultList();
            
            return resultList!=null && resultList.size()==1;
        } catch(Exception ex){
            log.info("Problem occurred during loading user from database", ex);
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Returns remote subscriber from user SIP
     * @param sip
     * @return 
     */
    public CAcertsSigned getCertificateForUser(Subscriber s){
        try {
            if (s==null) {
                throw new NullPointerException("Passed null subscriber");
            }
            
            CAcertsSigned userCert = em.createQuery("select cs from CAcertsSigned cs "
                            + " WHERE cs.subscriber=:s "
                            + " AND cs.isRevoked=false"
                            + " AND cs.notValidAfter>:n"    
                            + " ORDER BY cs.dateSigned DESC", CAcertsSigned.class)
                            .setParameter("s", s)
                            .setParameter("n", new Date())
                            .setMaxResults(1)
                            .getSingleResult();
            return userCert;
        } catch(Exception ex){
            //log.info("Problem occurred during loading user from database", ex);
            return null;
        }
    }
    
    /**
     * Generates randomized hash on base64 encoding from given seed
     * @param seed
     * @param randomized    if true, random number and current time in ms are appended to seed.
     * @return 
     */
    public static String generateHash(String seed, boolean randomized) throws NoSuchAlgorithmException{
    	return generateHash(seed, randomized, 1);
    }
	 /**
     * Generates randomized hash on base64 encoding from given seed
     * @param seed
     * @param randomized    if true, random number and current time in ms are appended to seed.
     * @return 
     */
    public static String generateHash(String seed, boolean randomized, int iterations) throws NoSuchAlgorithmException{
    	java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-512");
        String sseed = seed;
        
        if (randomized) {
            Random rand = new Random();
            StringBuilder sb = new StringBuilder(seed)
                        .append(":").append(System.currentTimeMillis())
                        .append(":").append(rand.nextLong());
            sseed = sb.toString();
        }
        
        byte[] input = sseed.getBytes();
        byte[] digest = null;
        for(int i=0; i<iterations; i++){
        	digest = sha.digest(input);
        	input = digest;
        }
        
        return new String(Base64.encode(digest));
    }
    
    /**
     * Generates one time token, stores it to database.
     * 
     * @param user
     * @param userToken
     * @param validityMillisec
     * @return 
     */
    @Transactional
    public String generateOneTimeToken(String user, String userToken, Long validityMillisec, String fprint) throws NoSuchAlgorithmException{
        if (user==null || userToken==null || user.isEmpty() || userToken.isEmpty()){
            throw new IllegalArgumentException("Not generating token from empty data");
        }
        
        // check if user exists in database
        Subscriber localUser = this.getLocalUser(user);
        if (localUser==null){
            log.warn("User ["+user+"] wants one time token but not found in DB");
            throw new IllegalArgumentException("Invalid user");
        }
        
        // for given user allow new token only if prev request is older than 10 seconds
        boolean tooRecent=false;
        try {
            String query = "SELECT MAX(ott.inserted) FROM oneTimeToken ott WHERE ott.userSIP=:s";
            Date singleResult = em.createQuery(query, Date.class).setParameter("s", user).getSingleResult();
            if (singleResult!=null){
                log.info("Single result is not null: " + singleResult);
                Date tolerance = new Date(System.currentTimeMillis() - 1000*10);
                if (singleResult.after(tolerance)){
                    tooRecent=true;
                }
            }
        } catch(Exception e){
            log.info("Query failed", e);
        }
        
        if (tooRecent){
            throw new RuntimeException("Last query too recent");
        }
        
        // generate server token
        StringBuilder sb = new StringBuilder()
                .append(user).append(":")
                .append(userToken).append(":")
                .append(fprint).append(":")
                .append(validityMillisec);
        String serverToken = this.generateHash(sb.toString(), true);
        
        // store to database and return 
        OneTimeToken ott = new OneTimeToken();
        ott.setFprint(fprint);
        ott.setInserted(new Date());
        ott.setNotValidAfter(new Date(System.currentTimeMillis() + validityMillisec));
        ott.setToken(serverToken);
        ott.setUserSIP(user);
        ott.setUserToken(userToken);
        em.persist(ott);
        em.flush();
        
        return serverToken;
    }
    
    /**
     * Checks validity of one time token and deletes it immediatelly
     * @param user
     * @param userToken
     * @param validityMillisec
     * @param fprint
     * @return 
     */
    @Transactional
    public boolean isOneTimeTokenValid(String user, String userToken, String serverToken, String fprint){
        if (user==null || userToken==null || serverToken==null || user.isEmpty() || userToken.isEmpty()){
            throw new IllegalArgumentException("Not generating token from empty data");
        }
        
        String query = "SELECT ott FROM oneTimeToken ott "
                + " WHERE ott.notValidAfter >= :n"
                + " AND ott.userSIP = :u"
                + " AND ott.userToken = :ut"
                + " AND ott.token = :st";
        try {
            OneTimeToken ott = em.createQuery(query, OneTimeToken.class)
                    .setParameter("n", new Date())
                    .setParameter("u", user)
                    .setParameter("ut", userToken)
                    .setParameter("st", serverToken)
                    .getSingleResult();            
            em.remove(ott);
            em.flush();
            return true;
        } catch(Exception ex){
            log.info("Problem during one time token verification", ex);
        }
        
        return false;
    }
    
    /**
     * Generates string base for encryption and auth token
     * 
     * @param sip
     * @param ha1
     * @param usrToken
     * @param serverToken
     * @param milliWindow
     * @param offset
     * @return
     * @throws NoSuchAlgorithmException 
     */
    public String generateUserTokenBase(String sip, String ha1, String usrToken, String serverToken, long milliWindow, int offset) {
        // determine current time window
        long curTime = System.currentTimeMillis();
        long curTimeSlot = ((long) Math.floor(curTime / (double)milliWindow)) + offset;
        StringBuilder sb = new StringBuilder()
                .append(sip).append(':')
                .append(ha1).append(':')
                .append(usrToken).append(':')
                .append(serverToken).append(':')
                .append(curTimeSlot).append(':');
        return sb.toString();
    }
    
    /**
     * Generates user auth token for defined set of parameters.
     * Method does not use database.
     * @param sip               user sip
     * @param ha1               ha1 field from database
     * @param usrToken  
     * @param serverToken       
     * @param milliWindow       length of one time slot
     * @param offset            time window offset from NOW
     * @return 
     */
    public String generateUserAuthToken(String sip, String ha1, String usrToken, String serverToken, long milliWindow, int offset) throws NoSuchAlgorithmException{
        // determine current time window
        String base = generateUserTokenBase(sip, ha1, usrToken, serverToken, milliWindow, offset);
        return generateHash(base+"PHOENIX_AUTH", false, 3779);
    }
    
      /**
     * Generates user encryption token for defined set of parameters.
     * Method does not use database.
     * @param sip               user sip
     * @param ha1               ha1 field from database
     * @param usrToken  
     * @param serverToken       
     * @param milliWindow       length of one time slot
     * @param offset            time window offset from NOW
     * @return 
     */
    public String generateUserEncToken(String sip, String ha1, String usrToken, String serverToken, long milliWindow, int offset) throws NoSuchAlgorithmException{
        // determine current time window
        String base = generateUserTokenBase(sip, ha1, usrToken, serverToken, milliWindow, offset);
        return generateHash(base+"PHOENIX_ENC", false, 11);
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
     * To convert the InputStream to String we use the Reader.read(char[]
     * buffer) method. We iterate until the Reader return -1 which means
     * there's no more data to read. We use the StringWriter class to
     * produce the string.
     */
    public static String convertStreamToStr(InputStream is) throws IOException {
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
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
