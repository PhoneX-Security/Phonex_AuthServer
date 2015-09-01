/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.db.*;
import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.executor.JobFinishedListener;
import com.phoenix.service.executor.JobRunnable;
import com.phoenix.service.pres.TransferRosterItem;
import com.phoenix.service.xmpp.RosterSyncElement;
import com.phoenix.utils.JiveGlobals;
import com.phoenix.utils.MiscUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.bouncycastle.util.encoders.Base64;
import org.codehaus.jackson.map.ObjectMapper;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaQuery;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Answers data questions for endpoint.
 * @author ph4r05
 */
@Service
@Repository
public class PhoenixDataService {
    private static final Logger log = LoggerFactory.getLogger(PhoenixDataService.class);
    public static final String PROP_DEBUG_ROSTER="phonex.svc.rostersync.debug";
    public static final String PROP_CLIST_CHANGE_V1_IMPLICIT_PAIRING="phonex.svc.clistchangev1.implicitPairing";

    /**
     * <p>No subscription is established.</p>
     */
    public static final int SUB_NONE = 0;
    /**
     * <p>The roster owner has a subscription to the roster item's presence.</p>
     */
    public static final int SUB_TO = 1;
    /**
     * <p>The roster item has a subscription to the roster owner's presence.</p>
     */
    public static final int SUB_FROM = 2;
    /**
     * <p>The roster item and owner have a mutual subscription.</p>
     */
    public static final int SUB_BOTH = 3;

    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private X509TrustManager trustManager;

    @Autowired
    private TaskExecutor executor;
    
    @Autowired
    private JiveGlobals jiveGlobals;

    @Autowired
    private AMQPListener amqpListener;

    /**
     * Returns SIP address from subscriber record
     * @param s
     * @return 
     */
    public static String getSIP(Subscriber s){
        if (s==null) return "";
        return (s.getUsername() + "@" + s.getDomain());
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
     * Gets contact group by its primary key
     * @param id
     * @return
     */
    public ContactGroup getContactGroup(Integer id){
        return id == null ? null : getContactGroup(id.longValue());
    }

    /**
     * Gets contact group by its primary key
     * @param id
     * @return
     */
    public ContactGroup getContactGroup(Long id){
        try {
            if (id==null){
                return null;
            }

            return em.find(ContactGroup.class, id);
        } catch(Exception ex){
            log.info("Problem occurred during loading contact group from database", ex);
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
     * Specialized method to just extract contactlist for one owner and one target (local/remote),
     * if exists, otherwise returns null.
     * @param owner
     * @param target
     * @return
     */
    public Contactlist getContactlistForSubscriber(Subscriber owner, String target){
        final Subscriber caller = getLocalUser(target);
        final RemoteUser callerRemote = caller != null ? null : getRemoteUser(target);
        if (caller != null){
            return getContactlistForSubscriber(owner, caller);
        } else if (callerRemote != null){
            return getContactlistForSubscriber(owner, callerRemote);
        }

        return null;
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
     * Specialized method to just extract contactlist for one owner and one target subscriber,
     * if exists, otherwise returns null. Target represents remote contact list entry, not stored on this server.
     * @param owner
     * @param remoteUser
     * @return
     */
    public Contactlist getContactlistForSubscriber(Subscriber owner, RemoteUser remoteUser){
        if (owner==null || remoteUser==null){
            throw new IllegalArgumentException("Some of argument is NULL, what is forbidden");
        }

        // now loading whitelist entries from database for owner, for intern user destination
        String queryGet = "SELECT cl FROM contactlist cl "
                + " WHERE cl.owner=:owner AND cl.obj.extern_user=:target";
        TypedQuery<Contactlist> query = em.createQuery(queryGet, Contactlist.class);
        query.setParameter("owner", owner);
        query.setParameter("target", remoteUser);
        List<Contactlist> resultList = query.getResultList();
        return resultList.isEmpty() ? null : resultList.get(0);
    }
    
    /**
     * Loads all subscribers in internal contact list.
     * @param owner
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
     * Sets parameters in the map to the query.
     * @param query
     */
    public void setQueryParameters(Query query, Map<String, Object> params){
        if (params == null || params.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Builds final query string.
     * queryBase + glue + join(" AND ", criteria).
     * @param queryBase
     * @param criteria
     * @param suffix
     * @return
     */
    public String buildQueryString(String queryBase, Collection<String> criteria, String suffix){
        StringBuilder sb = new StringBuilder(queryBase);
        sb.append(" ( ");
        sb.append(MiscUtils.join(criteria, " ) AND ( "));
        sb.append(" ) ");
        sb.append(suffix);
        return sb.toString();
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
     * Builds roster data from contactlist.
     * @param clist
     * @return 
     */
    public List<TransferRosterItem> buildRoster(List<Contactlist> clist){
        List<TransferRosterItem> tRosterItems = new LinkedList<TransferRosterItem>();
        
        // Delete non-existent roster items from the roster.
        for(Contactlist ce : clist){
            TransferRosterItem tri = new TransferRosterItem();
            
            ContactlistObjType ctype = ce.getObjType();
            if (ctype!=ContactlistObjType.INTERNAL_USER){
                continue;
            }
            
            Subscriber s = ce.getObj().getIntern_user();
            if (s==null || s.isDeleted()){
                continue;
            }
            
            String sip = PhoenixDataService.getSIP(s);
            
            tri.jid = sip;
            tri.name = ce.getDisplayName();
            tri.askStatus = null;
            tri.recvStatus = null;
            
            // It username starts with ~ then subscription is from, both otherwise.
            tri.subscription = (tri.name != null && tri.name.startsWith("~")) ? SUB_FROM : SUB_BOTH;
            tri.groups = "";
            
            tRosterItems.add(tri);
        }
        
        return tRosterItems;
    }

    /**
     * Builds roster data for roster resync from resync element.
     * @param rsyncElem
     * @return
     */
    public List<TransferRosterItem> buildRoster(RosterSyncElement rsyncElem){
        List<TransferRosterItem> tRosterItems = new LinkedList<TransferRosterItem>();

        final ArrayList<Contactlist> clist = rsyncElem.getClist();
        final ArrayList<Subscriber> clistSubs = rsyncElem.getClistSubs();
        final int clistSize = clist.size();

        if (clistSize != clistSubs.size()){
            throw new RuntimeException("Size of the contact list does not match size of contacts subscribers records." + clistSize);
        }

        for(int i = 0; i < clistSize; i++){
            final TransferRosterItem tri = new TransferRosterItem();
            final Contactlist ce = clist.get(i);

            ContactlistObjType ctype = ce.getObjType();
            if (ctype!=ContactlistObjType.INTERNAL_USER){
                continue;
            }

            final Subscriber s = clistSubs.get(i);
            if (s==null || s.isDeleted()){
                continue;
            }

            final String sip = PhoenixDataService.getSIP(s);

            tri.jid = sip;
            tri.name = ce.getDisplayName();
            tri.askStatus = null;
            tri.recvStatus = null;

            // It username starts with ~ then subscription is from, both otherwise.
            tri.subscription = (tri.name != null && tri.name.startsWith("~"))? SUB_FROM : SUB_BOTH;
            tri.groups = "";

            tRosterItems.add(tri);
        }

        return tRosterItems;
    }

    /**
     * Loads data needed for roster resync for given list of subscribers.
     * @param user
     * @return
     */
    public Collection<RosterSyncElement> loadRosterSyncData(String user){
        return loadRosterSyncData(Collections.singletonList(getLocalUser(user)));
    }

    /**
     * Loads data needed for roster resync for given list of subscribers.
     * @param user
     * @return
     */
    public Collection<RosterSyncElement> loadRosterSyncData(Subscriber user){
        return loadRosterSyncData(Collections.singletonList(user));
    }

    /**
     * Loads data needed for roster resync for given list of subscribers.
     * @param users
     * @return
     */
    public Collection<RosterSyncElement> loadRosterSyncData(Collection<Subscriber> users){
        Map<String, RosterSyncElement> rosterDb = new HashMap<String, RosterSyncElement>();

        // Load contactlist for all users in the given set of local users.
        // Standard query to CL, for given user, now only internal user
        String getContactListQuery = "SELECT cl, ow, s, rm FROM contactlist cl "
                + " LEFT OUTER JOIN cl.obj.intern_user s "
                + " LEFT OUTER JOIN cl.owner ow "
                + " LEFT OUTER JOIN cl.obj.extern_user rm "
                + " WHERE cl.owner IN :owners "
                + " ORDER BY cl.owner.domain, cl.owner.username, s.domain, s.username";

        TypedQuery<Object[]> query = em.createQuery(getContactListQuery, Object[].class);
        query.setParameter("owners", users);

        List<Object[]> resultList = query.getResultList();
        for(Object[] o : resultList){
            final Contactlist cl = (Contactlist) o[0];
            final Subscriber owner = (Subscriber) o[1];
            Subscriber contact = null;
            RemoteUser rm = null;

            if (o.length == 3){
                if (o[2] instanceof Subscriber){
                    contact = (Subscriber) o[2];
                } else if (o[2] instanceof RemoteUser){
                    rm = (RemoteUser) o[2];
                } else {
                    log.error("Unknown object in o[1] for subscriber: " + owner.getUsername());
                    continue;
                }
            } else {
                contact = (Subscriber) o[2];
                rm = (RemoteUser) o[3];
            }

            // Synchronizing contact list with remote entries is not supported yet.
            if (cl == null || contact == null){
                log.warn("Contact or subscriber is null: cl: " + cl + "; ");
                continue;
            }

            // Fetch roster sync element / create a new one.
            final String ownerSip = PhoenixDataService.getSIP(owner);
            RosterSyncElement rs = rosterDb.get(ownerSip);
            if (rs == null){
                rs = new RosterSyncElement(owner);
                rosterDb.put(ownerSip, rs);
            }

            // Add current internal user to the contact list sync element.
            rs.getClist().add(cl);
            rs.getClistSubs().add(contact);
        }

        return rosterDb.values();
    }

    /**
     * Bulk roster sync call to the OpenFire service API.
     * Request has single POST requets attribute, jsonReq, containing JSON request body.
     * jsonReq: {"requests":[{},{},{},{}]}
     *
     * jsonReq requests contains objects: {owner: "test@phone-x.net", roster:[{TransferRosterItem1}, {TransferRosterItem2}]}
     *
     * @param rosterSync
     * @return
     */
    public int bulkSyncRoster(Collection<RosterSyncElement> rosterSync){
        final HttpClient client = new DefaultHttpClient();

        final HttpPost post = new HttpPost("http://phone-x.net:9090/plugins/userService/userservice");
        try {
            post.setHeader("User-Agent", "PhoneX-home-server");
            post.setHeader("Accept-Language", "en-US,en;q=0.5");

            // Building request body.
            JSONObject req = new JSONObject();
            JSONArray reqArray = new JSONArray();
            for (RosterSyncElement rse : rosterSync) {
                JSONObject reqElem = new JSONObject();
                reqElem.put("owner", PhoenixDataService.getSIP(rse.getOwner()));

                JSONArray roster = new JSONArray();
                final List<TransferRosterItem> transferRosterItems = buildRoster(rse);
                for (TransferRosterItem tri : transferRosterItems) {
                    roster.put(tri.toJSON());
                }

                reqElem.put("roster", roster);
                reqArray.put(reqElem);
            }
            req.put("requests", reqArray);

            // Request body is built, call HTTP REST interface.
            final List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(3);
            nameValuePairs.add(new BasicNameValuePair("type", "sync_roster_bulk"));
            nameValuePairs.add(new BasicNameValuePair("secret", "eequaixee1Bi5ied"));
            nameValuePairs.add(new BasicNameValuePair("jsonReq", req.toString()));
            post.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));

            final HttpResponse response = client.execute(post);
            if (response == null || response.getStatusLine() == null || (response.getStatusLine().getStatusCode() / 100) != 2){
                throw new RuntimeException("Error response code: " + response);
            }

            final BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            final StringBuilder responseBld = new StringBuilder();
            String inputLine;
            while ((inputLine = rd.readLine()) != null) {
                responseBld.append(inputLine);
            }
            rd.close();
            String respBody = responseBld.toString();

            // If response is OK, return 1, else return -1
            if (respBody.contains("<result>")){
                return 1;
            } else {
                log.debug("RosterSync error: " + respBody);
                return -1;
            }

        } catch (IOException e) {
            log.error("Exception in sync bulk roster call", e);
        } catch (JSONException e) {
            log.error("Exception in sync bulk roster call", e);
        }
        return -1;
    }

    /**
     * Bulk roster synchronization with retry counter.
     * Roster sync with OpenFire server, blocking call.
     *
     * @param rosterSync
     * @param retryCount
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    public int bulkSyncRosterWithRetry(Collection<RosterSyncElement> rosterSync, int retryCount) throws MalformedURLException, IOException{
        // Synchronize roster list.
        int syncRoster = -1;
        for(int retryCtr = 0; retryCtr < retryCount; retryCtr++){
            try {
                syncRoster = bulkSyncRoster(rosterSync);
                if (syncRoster > 0){
                    // success here!
                    log.info("Roster synchronization OK, cnt=" + MiscUtils.collectionSize(rosterSync));
                    break;
                } else {
                    // not successfull, but request was delivered.
                    log.info("Roster synchronization was not successfull, retrycount=" + retryCtr + "; err=" + syncRoster);
                    break;
                }
            } catch(Exception e){
                log.warn("Exception during roster synchronization", e);
            }
        }

        return syncRoster;
    }

    /**
     * Synchronizes contactlist with the roster for given user on the 
     * Openfire server - custom userservice plugin is needed for this.
     * 
     * TODO: use HTTPS connection for this. Take inspiration from HTTPS post 
     * transfer used for file transfer.
     * 
     * @param owner
     * @param clist
     * @return 
     * @throws java.net.MalformedURLException 
     */
    public int syncRoster(Subscriber owner, List<Contactlist> clist) throws MalformedURLException, IOException{
        final String destination = "http://phone-x.net:9090/plugins/userService/userservice";
        boolean debugRoster = jiveGlobals.getBooleanProperty(PROP_DEBUG_ROSTER, false);
        
        final String ownerSip = PhoenixDataService.getSIP(owner);
        final String username = owner.getUsername();
        
        URL obj = new URL(destination);
	    HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
        
        // Allow Inputs & Outputs
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", "PhoneX-home-server");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        
        // Build query parameters.
        StringBuilder urlParameters = new StringBuilder(
                String.format("type=sync_roster&secret=eequaixee1Bi5ied&username=%s&roster=", 
                        URLEncoder.encode(username, "UTF-8")));
        
        // Build roster data from contactlist.
        List<TransferRosterItem> rosterList = buildRoster(clist);
        // Convert it to JSON format.
        ObjectMapper mapper = new ObjectMapper();
        String rosterJSON = mapper.writeValueAsString(rosterList);
        // Convert JSON to base64
        byte[] b64Bytes = Base64.encode(rosterJSON.getBytes("UTF-8"));
        // Base64 has to be urlencoded since +=/ are not URL friendly.
        String rosterBase64 = URLEncoder.encode(new String(b64Bytes, "UTF-8"), "UTF-8");
        
        urlParameters.append(rosterBase64);
        
        if (debugRoster){
            log.info("roster sync for " + ownerSip + "; URL: " + urlParameters + ";;; JSON: " + rosterJSON);
        }
        
        // Send post request
        connection.setDoOutput(true);
        connection.setDoInput(true);
        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(urlParameters.toString());
        wr.flush();
        wr.close();

        int responseCode = connection.getResponseCode();
        if ((responseCode / 100) != 2){
            throw new RuntimeException("Error response code: " + responseCode);
        }
        
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
        }
        in.close();
        String respBody = response.toString();
        
        // If response is OK, return 1, else return -1
        if (respBody.contains("<result>")){
            return 1;
        } else {
            log.debug("RosterSync error: " + respBody);
            return -1;
        }
    }
    
    /**
     * Roster synchronization with retry counter.
     * Roster sync with OpenFire server, blocking call.
     *
     * @param owner
     * @param clist
     * @param retryCount
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    public int syncRosterWithRetry(Subscriber owner, List<Contactlist> clist, int retryCount) throws MalformedURLException, IOException{
        // Synchronize roster list.
        int syncRoster = -1;
        for(int retryCtr = 0; retryCtr < retryCount; retryCtr++){
            try {
                syncRoster = syncRoster(owner, clist);
                if (syncRoster > 0){
                    // success here!
                    log.info("Roster synchronization OK, usr=" + owner.getUsername());
                    break;
                } else {
                    // not successfull, but request was delivered.
                    log.info("Roster synchronization was not successfull, retrycount=" + retryCtr + "; err=" + syncRoster);
                    break;
                }
            } catch(Exception e){
                log.warn("Exception during roster synchronization", e);
            }
        }

        return syncRoster;
    }
    
     /**
     * Reads whole input stream to a byte array.
     *
     * @param is
     * @return
     * @throws IOException
     */
    public static byte[] readInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
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
     * @param s
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
     * Returns remote subscriber from user SIP.
     * Only the newest valid, non revoked certificate is returned for each user.
     *
     * @param s
     * @return
     */
    public Map<String, CAcertsSigned> getCertificatesForUsers(List<Subscriber> s){
        try {
            // If empty subscriber list, return empty map.
            if (s == null || s.isEmpty()){
                return new HashMap<String, CAcertsSigned>();
            }

            List<CAcertsSigned> allCerts = em.createQuery("SELECT cs FROM CAcertsSigned cs "
                    + " WHERE cs.subscriber IN :s "
                    + " AND cs.isRevoked=false"
                    + " AND cs.notValidAfter>:n"
                    + " ORDER BY cs.dateSigned DESC", CAcertsSigned.class)
                    .setParameter("s", s)
                    .setParameter("n", new Date())
                    .getResultList();

            Map<String, CAcertsSigned> ret = new HashMap<String, CAcertsSigned>();
            for(CAcertsSigned curCert : allCerts){
                final String sip = curCert.getSubscriberName();

                // Get previous, if is newer, keep the old one.
                final CAcertsSigned prevCert = ret.get(sip);
                if (prevCert != null
                        && prevCert.getDateSigned() != null
                        && prevCert.getDateSigned().after(curCert.getDateSigned())){
                    continue;
                }

                ret.put(sip, curCert);
            }

            return ret;
        } catch(Exception ex){
            log.info("Problem occurred during loading certificate from database", ex);
            return new HashMap<String, CAcertsSigned>();
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
     * @param serverToken
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
        return generateHash(base + "PHOENIX_AUTH", false, 3779);
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
    public void execute(String sql){
        em.createNativeQuery(sql).executeUpdate();
    }
    
    @Transactional
    public void persist(Object o){
        this.persist(o, false);
    }
    
    @Transactional(propagation = Propagation.REQUIRED)
    public int update(Query q){
        return q.executeUpdate();
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

    public <T> T tryGetSingleResult(TypedQuery<T> query){
        final List<T> resultList = query.getResultList();
        if (resultList == null || resultList.isEmpty()){
            return null;
        }

        return resultList.get(0);
    }

    public Query createQuery(String s) {
        return em.createQuery(s);
    }

    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
        return em.createQuery(criteriaQuery);
    }

    public <T> TypedQuery<T> createQuery(String s, Class<T> aClass) {
        return em.createQuery(s, aClass);
    }

    /**
     * To convert the InputStream to String we use the Reader.read(char[]
     * buffer) method. We iterate until the Reader return -1 which means
     * there's no more data to read. We use the StringWriter class to
     * produce the string.
     * @param is
     * @return 
     * @throws java.io.IOException 
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
    
    public void resyncRoster(Subscriber tuser) throws IOException{
        // Obsoleted, not using XCAP anymore.
//        Map<Integer, Subscriber> internalUsersInContactlist = getInternalUsersInContactlist(contactlistForSubscriber);
//        List<String> sips = new ArrayList(contactlistForSubscriber.size());
//        for(Map.Entry<Integer, Subscriber> e : internalUsersInContactlist.entrySet()){
//            String clsip = PhoenixDataService.getSIP(e.getValue());
//            sips.add(clsip);
//        }      
//        Xcap xcapEntity = pmanager.updateXCAPPolicyFile(tuser.getUsername(), tuser.getDomain(), sips);
//        log.info("XcapEntity persisted");
//        this.em.flush();

        // Synchronize roster list.
        bulkSyncRosterWithRetry(loadRosterSyncData(tuser), 5);
    }
    
    public void resyncRoster(String userName) throws IOException{
        // regenerating policy for given contact
        Subscriber tuser = getLocalUser(userName); 
        if (tuser == null){
            log.info("Cannot update roster for user " + userName + ", unknown destination");
            return;
        }

        this.resyncRoster(tuser);
    }

    /**
     * Returns list of subscribers with expired licenses that have not been notified about this yet.
     * @return
     */
    @Transactional
    public List<Subscriber> getAllExpiredNonNotifiedLicenses(){
        List<Subscriber> result = new LinkedList<Subscriber>();

        // now loading whitelist entries from database for owner, for intern user destination
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT s.* FROM subscriber s ");
        sb.append("LEFT JOIN licenseNotifications ln ON ln.subscriber=s.id ");
        sb.append("WHERE s.expires_on IS NOT NULL AND s.expires_on < NOW() AND ");
        sb.append("(ln.licenseDateExpire IS NULL OR ln.licenseDateExpire != s.expires_on)");

        Query nativeQuery = getEm().createNativeQuery(sb.toString(), Subscriber.class);
        List resultList = nativeQuery.getResultList();
        for(Object obj : resultList){
            Subscriber tmpS = (Subscriber) obj;
            result.add(tmpS);
        }

//        Query query = em.createQuery(sb.toString());
//        List<Object[]> resultList = query.getResultList();
//        for(Object[] o : resultList){
//            final Subscriber s = (Subscriber) o[0];
//            result.add(s);
//        }

        return result;
    }

    /**
     * Inserts / updates license notification for given user.
     * @param s
     */
    @Transactional
    public void setLicenseNotification(Subscriber s){
        final String olderThanQueryString = "DELETE FROM LicenseNotifications d WHERE d.subscriber = :s";
        Query delQuery = em.createQuery(olderThanQueryString);
        delQuery.setParameter("s", s);
        delQuery.executeUpdate();

        final LicenseNotifications ln = new LicenseNotifications();
        ln.setSubscriber(s);
        ln.setLicenseDateExpire(s.getExpires());
        ln.setLastNotificationDate(Calendar.getInstance());
        em.persist(ln);
    }

    /**
     * Notifies new certificate event to all account that have this account in their rosters.
     */
    @Transactional
    public void notifyNewCertificateToRoster(final Subscriber s, final long time, final String certHash){
        executor.submit("contactCertUpdatePush", new JobRunnable() {
            @Override
            public void run() {
                internalNotifyNewCertificateToRoster(s, time, certHash);
            }
        }, new JobFinishedListener() {
            @Override
            public void jobFinished(JobRunnable job, Future<?> future) {
                log.info("contact cert update finished.");
            }
        });
    }

    @Transactional
    private void internalNotifyNewCertificateToRoster(Subscriber s, long time, String certHash){
        try {
            final String sip = PhoenixDataService.getSIP(s);

            // Send notifications to all entries in our contact list.
            List<Contactlist> contactlistForSubscriber = getContactlistForSubscriber(s);
            if (contactlistForSubscriber == null || contactlistForSubscriber.isEmpty()){
                return;
            }

            // Delete non-existent roster items from the roster.
            for(Contactlist ce : contactlistForSubscriber){
                ContactlistObjType ctype = ce.getObjType();
                if (ctype!=ContactlistObjType.INTERNAL_USER){
                    continue;
                }

                final Subscriber peer = ce.getObj().getIntern_user();
                if (peer==null || peer.isDeleted()){
                    continue;
                }

                final String peerSip = PhoenixDataService.getSIP(peer);
                amqpListener.pushContactCertUpdate(peerSip, sip, time, certHash);
            }

        } catch(Exception e){
            log.error("Exception in new cert roster notification", e);
        }
    }

    @Transactional
    public long getCountOfTrialEvents(Subscriber s){
        TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(te) FROM TrialEventLog te WHERE te.owner=:owner", Long.class);
        countQuery.setParameter("owner", s);
        Long sfCount = countQuery.getSingleResult();
        return sfCount == null ? 0 : sfCount;
    }

    @Transactional
    public List<TrialEventLog> getTrialEventLogs(Subscriber s, Integer type){
        List<TrialEventLog> result = new LinkedList<TrialEventLog>();

        try {
            // now loading whitelist entries from database for owner, for intern user destination
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT te FROM TrialEventLog te WHERE te.owner=:owner ");
            if (type != null) {
                sb.append(" AND te.etype=:etype ");
            }

            TypedQuery<TrialEventLog> query = em.createQuery(sb.toString(), TrialEventLog.class);
            query.setParameter("owner", s);
            if (type != null) {
                query.setParameter("etype", type);
            }

            List<TrialEventLog> resultList = query.getResultList();
            result.addAll(resultList);

        } catch(Exception ex){
            log.error("Exception in getTrialLogs", ex);
        }

        return result;
    }

    @Transactional
    public int cleanTrialLogsOlderThan(Date date){
        final String olderThanQueryString = "DELETE FROM TrialEventLog d WHERE d.dateCreated < :de";
        Query delQuery = em.createQuery(olderThanQueryString);
        delQuery.setParameter("de", date);
        return delQuery.executeUpdate();
    }

    /**
     * Transactional wrapper for IN(...) select.
     * @param typeParameterClass
     * @param sqlStatement
     * @param argsNames
     * @param argsWhere
     * @param whereInColumn
     * @param whereObjs
     * @return
     */
    @Transactional
    public List queryIn(Class typeParameterClass, String sqlStatement, String[] argsNames, Object[] argsWhere, String whereInColumn, List whereObjs){
        final int argLen = argsNames == null ? 0 : argsNames.length;

        // Create query object.
        final TypedQuery query = em.createQuery(sqlStatement, typeParameterClass);

        // Set provided parameters to the query.
        for(int i=0; i < argLen; i++){
            query.setParameter(argsNames[i], argsWhere[i]);
        }

        // Set list parameters.
        query.setParameter(whereInColumn, whereObjs);
        return query.getResultList();
    }

    /**
     * Adds support contact elements to the object given.
     * @param s
     * @param objToSet
     */
    public void setSupportContacts(Subscriber s, JSONObject objToSet) throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put("phonex-support@phone-x.net");
        objToSet.put("support_contacts", arr);
    }

    /**
     * Converts eventlog to JSON.
     * @param log
     * @return
     */
    public JSONObject eventLogToJson(List<TrialEventLog> log, Subscriber s) throws JSONException {
        JSONObject obj = new JSONObject();

        // Destination user this push message is designated.
        obj.put("user", getSIP(s));

        // Array of push messages.
        JSONArray msgArray = new JSONArray();
        for (TrialEventLog evt : log) {
            JSONObject evtObj = new JSONObject();
            evtObj.put("id", evt.getId());
            evtObj.put("type", evt.getEtype());
            evtObj.put("date", evt.getDateCreated().getTime());
            msgArray.put(evtObj);
        }

        obj.put("events", msgArray);
        return obj;
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

    public JiveGlobals getJiveGlobals() {
        return jiveGlobals;
    }

    public void setJiveGlobals(JiveGlobals jiveGlobals) {
        this.jiveGlobals = jiveGlobals;
    }

    public TaskExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(TaskExecutor executor) {
        this.executor = executor;
    }

    public AMQPListener getAmqpListener() {
        return amqpListener;
    }

    public void setAmqpListener(AMQPListener amqpListener) {
        this.amqpListener = amqpListener;
    }
}
