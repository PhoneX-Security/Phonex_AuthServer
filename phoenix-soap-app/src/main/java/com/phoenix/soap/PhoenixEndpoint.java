/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import com.phoenix.db.*;
import com.phoenix.db.extra.*;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.db.tools.DBObjectLoader;
import com.phoenix.service.*;
import com.phoenix.service.files.FileManager;
import com.phoenix.service.pres.PresenceManager;
import com.phoenix.soap.beans.*;
import com.phoenix.utils.*;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;

/**
 * Basic phoenix service endpoint for whitelist and contactlist manipulation
 * @author ph4r05
 */
@Endpoint
public class PhoenixEndpoint {
    private static final Logger log = LoggerFactory.getLogger(PhoenixEndpoint.class);
    private static final String NAMESPACE_URI = "http://phoenix.com/hr/schemas";
    
    private static final boolean CLIENT_DEBUG=true;
    private static final int CLIST_CHANGE_ERROR_GENERIC = -1;
    private static final int CLIST_CHANGE_ERROR_EMPTY_REQUEST_LIST = -3;
    private static final int CLIST_CHANGE_ERROR_ALREADY_ADDED = -2;
    private static final int CLIST_CHANGE_ERROR_INVALID_NAME = -5;
    private static final int CLIST_CHANGE_ERROR_NO_USER = -6;

    private static final String AUTH_TURN_PASSWD_KEY = "turnPwd";

    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private HttpServletRequest request;
    
    @Autowired(required = true)
    private TrustVerifier trustManager;
    
    @Autowired(required = true)
    private PhoenixDataService dataService;
    
    @Autowired(required = true)
    private PhoenixServerCASigner signer;
    
    @Autowired(required = true)
    private FileManager fmanager;
    
    @Autowired(required = true)
    private PresenceManager pmanager;
    
    @Autowired(required = true)
    private ServerCommandExecutor executor;

    @Autowired
    private AMQPListener amqpListener;

    @Autowired
    private UserPairingManager pairingMgr;

    @Autowired
    private JiveGlobals jiveGlobals;
    
    // owner SIP obtained from certificate
    private String owner_sip;
    public static final String DISPLAY_NAME_REGEX="^[a-zA-Z0-9_\\-\\s\\./!;\\(\\)@#&*\\[\\]\\^]+$";
    
    // Calendar for year 1971, used to check null-like dates.
    public static Calendar c1971;

    public PhoenixEndpoint() {
    }
    
    /**
     * Returns calendar object dated to 1971.
     * Used to compare with database dates, which may be 1970 indicating a not filled
     * in date.
     * @return 
     */
    public static Calendar get1971(){
        if (c1971 == null){
            c1971 = Calendar.getInstance();
            c1971.set(Calendar.YEAR, 1971);   
            c1971.set(Calendar.MONTH, 1);
            c1971.set(Calendar.DAY_OF_MONTH, 1);
        }
        
        return c1971;
    }

    /**
     * Converts Date to XMLGregorianCalendar used in SOAP responses.
     *
     * @param d
     * @return
     * @throws DatatypeConfigurationException
     */
    public static XMLGregorianCalendar getXMLDate(Date d) throws DatatypeConfigurationException{
        GregorianCalendar c = new GregorianCalendar();
        c.setTime(d);
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
    }

    /**
     * Converts XML:GregorianCalendar to Date.
     * @param c
     * @return
     */
    public static Date getDate(XMLGregorianCalendar c){
        if(c == null) {
            return null;
        }
        return c.toGregorianCalendar().getTime();
    }

    /**
     * Serializes list of nonces.
     * @param lst
     * @return
     */
    public static String serializeList(List<String> lst){
        StringBuilder sb = new StringBuilder();
        for(int i=0, cn=lst.size(); i < cn; i++){
            sb.append(lst.get(i));
            sb.append("|");
        }

        return sb.toString();
    }

    /**
     * Authenticate user from its certificate, returns subscriber data.
     * @param context
     * @param request
     * @return
     * @throws CertificateException 
     */
    public Subscriber authUserFromCert(MessageContext context, HttpServletRequest request) throws CertificateException {
        try {
            auth.check(context, this.request);
                    
            // auth passed, now extract SIP
            String sip = auth.getSIPFromCertificate(context, request);
            if (sip==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            log.info("AuthCheckFromCert Request came from user: [" + sip + "]");
            this.owner_sip = sip;
            Subscriber subs = this.dataService.getLocalUser(sip);
            if (subs==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            // Is user is deleted/disabled, no further operation is allowed
            if (subs.isDeleted()){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            return subs;
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }
    
    /**
     * Authenticate remote user.
     * 1. check certificate validity & signature by CA
     * 2. extract SIP string from certificate
     * 
     * @param context
     * @param request
     * @return
     * @throws CertificateException 
     */
    public String authRemoteUserFromCert(MessageContext context, HttpServletRequest request) throws CertificateException {
        try {
            auth.check(context, this.request);
                    
            // auth passed, now extract SIP
            String sip = auth.getSIPFromCertificate(context, request);
            if (sip==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            return sip;
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }
    
    /**
     * Checks whether user is using HTTPS (client certificate not required)
     * @param context
     * @param request
     * @throws CertificateException 
     */
    public void checkOneSideSSL(MessageContext context, HttpServletRequest request) throws CertificateException {
        try {
            auth.checkOneSideSSL(context, this.request);
        } catch (CertificateException ex) {
            log.info("One side SSL check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }

    /**
     * Whitelist manipulation request - with this is user changing its white list
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "whitelistRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public WhitelistResponse whitelistRequest(@RequestPayload WhitelistRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (whitelistRequest): " + ownerSip);
        
        // obtain all requests to modify whitelist from message
        List<WhitelistRequestElement> whitelistrequestElement =
                request.getWhitelistrequestElement();

        // prepare response
        WhitelistResponse response = new WhitelistResponse();
        
        // for pre-caching define this two lists and maps.
        // main idea is to load all users at once, load all their related whitelists,
        // then in loop again go trough rules and change it accordingly (insert vs. update)
        // ... This is not implemented now, usually this request will contain just one
        // element, thus this pre-caching is use-less...
        
        // iterate over every request in order it came.
        // now implemented that it require 3 queries to finish one round for-loop.
        // 1. get target subscriber
        // 2. get existing wl record
        // 3. update/insert wl record
        for (WhitelistRequestElement element : whitelistrequestElement) {
            log.info("WHreq on: [" + element.getUser() + "] do [" + element.getAction().value() + "]");
            
            // At first obtain user object we are talking about.
            // Now assume only local user, we will have procedure for extern and groups also
            // in future (I hope so:)).
            Subscriber s = null;
            String sip = element.getUser().getUserSIP();
            Long userID = element.getUser().getUserID();
            if (sip!=null && !sip.isEmpty()){
                s = dataService.getLocalUser(sip);
            } else if (userID!=null && userID>0){
                s = dataService.getLocalUser(userID);
            } else {
                throw new RuntimeException("Both user identifiers are null");
            }
            
            // null subscriber is not implemented yet
            if (s==null){
                log.info("Subscriber defined is null, not implemented yet.");
                response.getReturn().add(-1);
                continue;
            }
            
            // now do the action in whitelist wanted by user
            WhitelistAction action = element.getAction();
            
            // get whitelist for given user
            Whitelist wl = this.dataService.getWhitelistForSubscriber(owner, s);
            if (wl!=null){
                // whitelist already exists, so do something
                try {
                    // removal action
                    if (action == WhitelistAction.REMOVE){
                        this.dataService.remove(wl, true);
                        response.getReturn().add(1);
                        
                        continue;
                    }
                    
                    // another action - involve updating
                    wl.setAction(action == WhitelistAction.DISABLE ? WhitelistStatus.DISABLED : WhitelistStatus.ENABLED);
                    this.dataService.persist(wl, true);
                    response.getReturn().add(1);
                    
                } catch(Exception e){
                    response.getReturn().add(-1);
                    log.warn("Manipulation with entity failed: " + wl, e);
                }
            } else {
                // whitelist is new, create entity and persist...
                if (action == WhitelistAction.REMOVE){
                    response.getReturn().add(-1);
                    log.info("Wanted to delete non-existing whitelist record");
                    continue;
                }
                
                // now create wl and persist
                wl = new Whitelist();
                wl.setAction(action == WhitelistAction.DISABLE ? WhitelistStatus.DISABLED : WhitelistStatus.ENABLED);
                wl.setDateCreated(new Date());
                wl.setDateLastEdit(new Date());
                wl.setDstType(WhitelistObjType.INTERNAL_USER);
                wl.setSrcType(WhitelistObjType.INTERNAL_USER);
                wl.setSrc(new WhitelistSrcObj(owner));
                wl.setDst(new WhitelistDstObj(s));
                try {
                    this.dataService.persist(wl, true);
                    response.getReturn().add(1);
                } catch(Exception e){
                    response.getReturn().add(-1);
                    log.warn("Could not persis entity: " + wl, e);
                }
            }
        }

        logAction(ownerSip, "whitelistRequest", null);
        return response;
    }

    /**
     * Whitelist get request  - returns whole whitelist for requesting user
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "whitelistGetRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public WhitelistGetResponse whitelistGetRequest(@RequestPayload WhitelistGetRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (whitelistGetRequest): " + ownerSip);
        
        // constructing whitelist response
        WhitelistGetResponse response = new WhitelistGetResponse();
        
        // get whole white list with this request (null, null)
        Map<String, Whitelist> wl = this.dataService.getWhitelistForUsers(owner, null, null);
        log.info("WL size: " + wl.size());
        for(Entry<String, Whitelist> e: wl.entrySet()){
            WhitelistElement wle = new WhitelistElement();
            wle.setUsersip(e.getKey());
            wle.setUserid(e.getValue().getDst().getIntern_user().getId());
            
            WhitelistStatus action = e.getValue().getAction();
            wle.setWhitelistStatus(action==WhitelistStatus.ENABLED ? UserWhitelistStatus.IN : UserWhitelistStatus.DISABLED);
            
            response.getReturn().add(wle);
        }

        logAction(ownerSip, "whitelistGetRequest", null);
        return response;
    }
    
    /**
     * Contactlist get request - returns contact list. 
     * If request contains some particular users, only subset of this users from
     * contactlist is returned. Otherwise whole contact list is returned.
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "contactlistGetRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public ContactlistGetResponse contactlistGetRequest(@RequestPayload ContactlistGetRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (contactlistGetRequest): " + ownerSip);
        
        // subscriber list
        List<Subscriber> subs = new LinkedList<Subscriber>();
        Map<Integer, Contactlist> clistEntries = new HashMap<Integer, Contactlist>();
        String targetUser = request.getTargetUser();
        if (targetUser==null || targetUser.isEmpty()){
            targetUser=ownerSip;
        }
        
        // TODO: implement this, obtaining contact list for different person
        if (ownerSip.equals(targetUser)==false){
            log.warn("Obtaining contact list for different person is not allowed");
            throw new RuntimeException("Not implemented yet");
        }
        
        // analyze request - extract user identifiers to load from contact list.
        // At first extract all users with SIP name from local user table and convert
        // to user ID
        List<Long> userIDS = null;
        List<UserIdentifier> alias = request.getUsers();
        if (alias!=null && !alias.isEmpty() && alias.get(0)!=null){
            // OK now load only part of contactlist we are interested in...
            //
            log.info(alias.get(0).toString());
            
            List<String> userSIP = new ArrayList<String>(alias.size());
            userIDS = new ArrayList<Long>(alias.size());
            
            log.info("Alias is not null; size: " + alias.size());
            for(UserIdentifier al:alias){
                log.info("AliasFromList: " + al);
                
                // extract user sip
                if (al.getUserSIP()!=null){
                    log.info("UserSIP: " + al.getUserSIP());
                    userSIP.add(al.getUserSIP());
                } else if (al.getUserID()!=null){
                    log.info("UserID: " + al.getUserID());
                    userIDS.add(al.getUserID());
                }
            }
            
            // now iterave over user SIP and determine their IDs
            try {
                // build string with IN (...)
                String querySIP2ID = "SELECT u FROM Subscriber u WHERE CONCAT(u.username, '@', u.domain) IN :sip";
                TypedQuery<Subscriber> query = em.createQuery(querySIP2ID, Subscriber.class);
                query.setParameter("sip", userSIP);
                // iterate over result set and add ID 
                List<Subscriber> resultList = query.getResultList();
                for(Subscriber s : resultList){
                    userIDS.add(Long.valueOf(s.getId()));
                }
            } catch(Exception e){
                log.warn("Something went wrong during SIP->ID conversion", e);
            }
            
            // now extract all defined subscribers from contact list, if they are in it
            throw new java.lang.UnsupportedOperationException("Not yet implemented");            
        } else {
            //
            // Alias is empty -> get all contact list records
            //
            log.info("ClistGetRequest: Alias is empty, requestor: " + ownerSip);
            String getContactListQuery;
            
            // standard query to CL, for given user, now only internal user
            getContactListQuery = "SELECT s, cl FROM contactlist cl "
                    + "LEFT OUTER JOIN cl.obj.intern_user s "
                    + "WHERE cl.objType=:objtype AND cl.owner=:owner "
                    + "ORDER BY s.domain, s.username";
            TypedQuery<Object[]> query = em.createQuery(getContactListQuery, Object[].class);
            query.setParameter("objtype", ContactlistObjType.INTERNAL_USER);
            query.setParameter("owner", owner);
            
            List<Object[]> resultList = query.getResultList();
            for(Object[] o : resultList){
                final Subscriber t = (Subscriber) o[0];
                final Contactlist cl = (Contactlist) o[1];
                
                subs.add(t);
                clistEntries.put(t.getId(), cl);
            }
        }
                
        // now wrapping container 
        ContactlistGetResponse response = new ContactlistGetResponse();
        
        /**
         * Whitelist is now embedded into contact list
         * This code is kept here in order to have in in case whitelist will
         * turn to blacklist
         * 
         * <OLD_WHITELIST_CODE>
        // extract whitelist information for subscribers - adding extra info
        // for each contact. 
        Map<String, Whitelist> wlist = this.dataService.getWhitelistForUsers(owner, subs, null);
         * </OLD_WHITELIST_CODE>
         */
        
        for(Subscriber o : subs){
            String userSIP = o.getUsername() + "@" + o.getDomain();
            
            ContactListElement elem = new ContactListElement();
            elem.setAlias(o.getUsername());
            elem.setContactlistStatus(EnabledDisabled.ENABLED);
            elem.setPresenceStatus(UserPresenceStatus.ONLINE);
            elem.setUserid(o.getId());
            elem.setUsersip(userSIP);
            elem.setWhitelistStatus(UserWhitelistStatus.NOCLUE);
            if (clistEntries.containsKey(o.getId())){
                Contactlist cl = clistEntries.get(o.getId());
                elem.setWhitelistStatus(cl.isInWhitelist() ? UserWhitelistStatus.IN : UserWhitelistStatus.NOTIN);
                elem.setDisplayName(cl.getDisplayName());
            }
            
            /**
             * <OLD_WHITELIST_CODE>
            // whitelist 
            if (wlist.containsKey(userSIP)){
                Whitelist wl = wlist.get(userSIP);
                log.info("User whitelist: " + wl);
                
                elem.setWhitelistStatus(wl.getAction()==WhitelistStatus.ENABLED ? UserWhitelistStatus.IN : UserWhitelistStatus.NOTIN);
            }
             * </OLD_WHITELIST_CODE>
             */ 
            
            response.getContactlistEntry().add(elem);
            logAction(ownerSip, "contactlistGetRequest", null);
        }
        
        return response;
    }
    
    /**
     * Contact list change request
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "contactlistChangeRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public ContactlistChangeResponse contactlistChangeRequest(@RequestPayload ContactlistChangeRequest request, MessageContext context) throws CertificateException {
        final Subscriber owner = this.authUserFromCert(context, this.request);
        final String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (contactlistChangeRequest) : " + ownerSip);
        
        // construct response, then add results iteratively
        final ContactlistChangeResponse response = new ContactlistChangeResponse();
        
        // analyze request
        final List<ContactlistChangeRequestElement> elems = request.getContactlistChangeRequestElement();
        if (elems==null || elems.isEmpty()){
            log.info("elems is empty");
            ContactlistReturn ret = new ContactlistReturn();
            ret.setResultCode(CLIST_CHANGE_ERROR_EMPTY_REQUEST_LIST);
            
            response.getReturn().add(ret);
            return response;
        }

        // Implicit pairing?
        final boolean doPairing = jiveGlobals.getBooleanProperty(PhoenixDataService.PROP_CLIST_CHANGE_V1_IMPLICIT_PAIRING, true);

        // iterating over request, algorithm:
        // 1. select targeted subscriber
        // 2. create/modify/delete contact list accordingly
        log.info("contactlistChangeRequest: elems is not null; size: " + elems.size() + ", elements: " + elems.toString());
        try {
            // Store users whose contact list was modified. For them will be later 
            // regenerated XML policy file for presence.
            Map<String, Subscriber> changedUsers = new HashMap<String, Subscriber>();
            
            // Iterate over all change requets element in request. Every can contain 
            // request for different subscriber.
            for(ContactlistChangeRequestElement elem : elems){
                if (elem==null) continue;
                log.info("contactlistChangeRequest: elem2string: " + elem.toString()
                        + ", user: " + elem.getUser()
                        + ", action: " + elem.getAction()
                        + ", ActionRequest: on [" + elem.getUser() + "] do: [" + elem.getAction().value() + "]");

                // At first obtain user object we are talking about.
                // Now assume only local user, we will have procedure for extern and groups also
                // in future (I hope so:)).
                Subscriber s = null;
                final String sip = elem.getUser().getUserSIP();
                final Long userID = elem.getUser().getUserID();
                if (sip!=null && !sip.isEmpty()){
                    s = dataService.getLocalUser(sip);
                } else if (userID!=null && userID>0){
                    s = dataService.getLocalUser(userID);
                } else {
                    throw new RuntimeException("Both user identifiers are null");
                }
                
                final ContactlistReturn ret = new ContactlistReturn();
                ret.setResultCode(CLIST_CHANGE_ERROR_GENERIC);
                ret.setTargetUser(elem.getTargetUser());
                ret.setUser(PhoenixDataService.getSIP(s));
                
                // null subscriber is not implemented yet
                if (s==null){
                    log.info("Subscriber defined is null, not implemented yet.");
                    response.getReturn().add(ret);
                    continue;
                }
                
                // changing contact list for somebody else
                // NOT IMPLEMENTED YET
                // TODO: implement this + policy checks
                String targetUser = elem.getTargetUser();
                if (targetUser==null || targetUser.isEmpty()){
                    targetUser=ownerSip;
                }
                
                Subscriber targetOwner = owner;
                if (!ownerSip.equals(targetUser)){
                    log.warn("Changing contactlist for somebody else is not permitted");
                    response.getReturn().add(ret);
                    
                    // TODO: load target owner
                    continue;
                }
                
                // users whose contact list was changed - updating presence rules afterwards
                if (!changedUsers.containsKey(targetUser)){
                    changedUsers.put(targetUser, targetOwner);
                }

                // is there already some contact list item?
                Contactlist cl = this.dataService.getContactlistForSubscriber(targetOwner, s);
                ContactlistAction action = elem.getAction();
                try {
                    if (cl!=null){
                        // contact list entry is empty -> record does not exist
                        if (action == ContactlistAction.REMOVE){
                            this.dataService.remove(cl, true);
                            ret.setResultCode(1);
                            response.getReturn().add(ret);

                            if (doPairing) {
                                pairingMgr.onUserXRemovedYFromContactlist(targetOwner, s);
                            }
                            continue;
                        }

                        // enable/disable?
                        if (action==ContactlistAction.DISABLE || action==ContactlistAction.ENABLE){
                            cl.setEntryState(action == ContactlistAction.DISABLE ? ContactlistStatus.DISABLED : ContactlistStatus.ENABLED);
                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            response.getReturn().add(ret);
                        }
                        
                        // add action
                        if (action==ContactlistAction.ADD){
                            // makes no sense, already in
                            ret.setResultCode(CLIST_CHANGE_ERROR_ALREADY_ADDED);
                            response.getReturn().add(ret);
                            log.info("Wanted to add already existing user");
                            continue;
                        }
                        
                        // update - displayName
                        if (action==ContactlistAction.UPDATE && elem.getDisplayName()!=null){
                            String newDispName = elem.getDisplayName();
                            if (!StringUtils.isEmpty(newDispName)){
                                newDispName = StringUtils.takeMaxN(newDispName, 128);
                            }

                            cl.setDateLastEdit(new Date());
                            cl.setDisplayName(newDispName);
                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            response.getReturn().add(ret);
                        }
                        
                        // whitelist action
                        WhitelistAction waction = elem.getWhitelistAction();
                        if (waction!=null && waction!=WhitelistAction.NOTHING){
                            // operate on whitelist here
                            boolean normalReq = false;
                            if (waction==WhitelistAction.DISABLE || waction==WhitelistAction.REMOVE){
                                // disabling from whitelist
                                cl.setInWhitelist(false);
                                normalReq = true;
                            } else if (waction==WhitelistAction.ENABLE || waction==WhitelistAction.ADD) {
                                // enabling in whitelist
                                cl.setInWhitelist(true);
                                normalReq = true;
                            }
                            
                            // request makes sense
                            if (normalReq){
                                this.dataService.persist(cl, true);
                                ret.setResultCode(1);
                                response.getReturn().add(ret);
                                continue;
                            }
                        }
                    } else {                        
                        // contact list entry is empty -> record does not exist
                        if (action == ContactlistAction.REMOVE){
                            ret.setResultCode(CLIST_CHANGE_ERROR_NO_USER);
                            response.getReturn().add(ret);
                            log.info("Wanted to delete non-existing whitelist record");
                            continue;
                        }
                        
                        // add action
                        if (action == ContactlistAction.ADD){
                            final WhitelistAction waction = elem.getWhitelistAction();
                            String newDispName = elem.getDisplayName();
                            if (newDispName != null && !newDispName.isEmpty()){
                                newDispName = StringUtils.takeMaxN(newDispName, 128);
                            }

                            cl = new Contactlist();
                            cl.setDateCreated(new Date());
                            cl.setDateLastEdit(new Date());
                            cl.setOwner(owner);
                            cl.setObjType(ContactlistObjType.INTERNAL_USER);
                            cl.setObj(new ContactlistDstObj(s));
                            cl.setEntryState(ContactlistStatus.ENABLED);
                            cl.setDisplayName(newDispName);
                            cl.setInWhitelist(waction == WhitelistAction.ENABLE || waction == WhitelistAction.ADD);
                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            response.getReturn().add(ret);

                            if (doPairing) {
                                pairingMgr.onUserXAddedYToContactlist(targetOwner, s);
                            }
                        } else {
                            ret.setResultCode(CLIST_CHANGE_ERROR_NO_USER);
                            response.getReturn().add(ret);
                        }
                    }
                } catch(Exception e){
                    log.info("Manipulation with contactlist failed", e);
                    ret.setResultCode(CLIST_CHANGE_ERROR_GENERIC);
                    response.getReturn().add(ret);
                }
            }
            
            //
            // Now is time to re-generate presence view policies and trigger server update
            // and roster synchronization.
            //
            for(Entry<String, Subscriber> entry : changedUsers.entrySet()){
                // regenerating policy for given contact
                try {
                    Subscriber tuser = entry.getValue();
                    this.dataService.resyncRoster(tuser);
                } catch(Exception ex){
                    log.error("Exception during presence rules generation for: " + entry.getValue(), ex);
                }
            }

            logAction(ownerSip, "contactlistChangeRequest", null);
            
        } catch(Exception e){
            log.info("Exception occurred", e);
            return null;
        }
       
        return response;
    }

    /**
     * Contactlist get request V2 - returns contact list.
     * If request contains some particular users, only subset of this users from
     * contactlist is returned. Otherwise whole contact list is returned.
     *
     * V2 is a simplified version for contact list fetch.
     *
     * @param request
     * @param context
     * @return
     * @throws java.security.cert.CertificateException
     */
    @PayloadRoot(localPart = "contactlistGetV2Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    public ClistGetV2Response contactlistGetV2Request(@RequestPayload ClistGetV2Request request, MessageContext context) throws CertificateException {
        final Subscriber owner = this.authUserFromCert(context, this.request);
        final String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (contactlistGetV2Request): " + ownerSip);

        final ClistGetV2Response response = new ClistGetV2Response();
        final ClistElementListV2 elist = new ClistElementListV2();
        response.setContactList(elist);

        // subscriber list
        String targetUser = request.getTargetUser();
        if (StringUtils.isEmpty(targetUser)){
            targetUser=ownerSip;
        }

        // Contactlist for someone else is not supported.
        if (!ownerSip.equals(targetUser)){
            log.warn("Obtaining contact list for different person is not allowed");
            throw new RuntimeException("Not implemented yet");
        }

        // Analyze request - extract user identifiers to load from contact list get request..
        // For now the list with specified users to load is not supported. Whole contactlist is returned.
        log.info("ClistGetV2Request: Alias is empty, requestor: " + ownerSip);
        String getContactListQuery;

        // standard query to CL, for given user, now only internal user
        getContactListQuery = "SELECT s, cl FROM contactlist cl "
                + "LEFT OUTER JOIN cl.obj.intern_user s "
                + "WHERE cl.objType=:objtype AND cl.owner=:owner "
                + "ORDER BY s.domain, s.username";
        TypedQuery<Object[]> query = em.createQuery(getContactListQuery, Object[].class);
        query.setParameter("objtype", ContactlistObjType.INTERNAL_USER);
        query.setParameter("owner", owner);

        List<Object[]> resultList = query.getResultList();
        for(Object[] o : resultList){
            final Subscriber t = (Subscriber) o[0];
            final Contactlist cl = (Contactlist) o[1];
            final String userSIP = PhoenixDataService.getSIP(t);

            ClistElementV2 elem = new ClistElementV2();
            elem.setAlias(t.getUsername());
            elem.setContactlistStatus(EnabledDisabled.ENABLED);
            elem.setPresenceStatus(UserPresenceStatus.ONLINE);
            elem.setUserid(t.getId());
            elem.setUsersip(userSIP);
            elem.setWhitelistStatus(UserWhitelistStatus.NOCLUE);
            elem.setAuxData(StringUtils.isEmpty(cl.getAuxData()) ? null : cl.getAuxData());
            if (cl != null){
                elem.setWhitelistStatus(cl.isInWhitelist() ? UserWhitelistStatus.IN : UserWhitelistStatus.NOTIN);
                elem.setDisplayName(cl.getDisplayName());
            }

            if (cl.getDateLastEdit() != null){
                try {
                    elem.setDateLastChange(getXMLDate(cl.getDateLastEdit()));
                } catch(Exception e){
                    log.error("Exception in data conversion", e);
                }
            }

            elist.getElements().add(elem);
        }

        logAction(ownerSip, "clistGetV2", null);
        response.setErrCode(0);
        return response;
    }

    /**
     * Contact list change request V2. Extended version, with pairing request integration.
     * @param request
     * @param context
     * @return
     * @throws java.security.cert.CertificateException
     */
    @PayloadRoot(localPart = "clistChangeV2Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public ClistChangeV2Response clistChangeV2Request(@RequestPayload ClistChangeV2Request request, MessageContext context) throws CertificateException {
        final Subscriber owner = this.authUserFromCert(context, this.request);
        final String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (contactlistChangeV2Request) : " + ownerSip);

        // construct response, then add results iteratively
        final ClistChangeV2Response response = new ClistChangeV2Response();
        final ClistChangeResultListV2 resList = new ClistChangeResultListV2();
        response.setResultList(resList);

        // analyze request
        if (request.getChanges() == null || MiscUtils.collectionIsEmpty(request.getChanges().getChanges())){
            response.setErrCode(CLIST_CHANGE_ERROR_EMPTY_REQUEST_LIST);
            return response;
        }

        final List<ClistChangeRequestElementV2> elems = request.getChanges().getChanges();

        // iterating over request, algorithm:
        // 1. select targeted subscriber
        // 2. create/modify/delete contact list accordingly
        log.info("contactlistChangeV2Request: Elems is not null; size: " + elems.size() + ", elems2string: " + elems.toString());
        try {
            // Store users whose contact list was modified. For them will be later
            // regenerated XML policy file for presence.
            final Map<String, Subscriber> changedUsers = new HashMap<String, Subscriber>();

            // Iterate over all change request elements in the request. Every can contain
            // request for different subscriber.
            for(ClistChangeRequestElementV2 elem : elems){
                if (elem==null) continue;
                log.info("contactlistChangeV2Request: elem2string: " + elem.toString()
                        + ", user: " + elem.getUser()
                        + ", action: " + elem.getAction()
                        + ", ActionRequest: on [" + elem.getUser() + "] do: [" + elem.getAction().value() + "]");

                // At first obtain user object we are talking about.
                // Now assume only local user, we will have procedure for extern and groups also
                // in future (I hope so:)).
                Subscriber s = null;
                final String sip = elem.getUser().getUserSIP();
                final Long userID = elem.getUser().getUserID();
                if (sip!=null && !sip.isEmpty()){
                    s = dataService.getLocalUser(sip);
                } else if (userID!=null && userID>0){
                    s = dataService.getLocalUser(userID);
                } else {
                    throw new RuntimeException("Both user identifiers are null");
                }

                ClistChangeResultV2 ret = new ClistChangeResultV2();
                ret.setResultCode(CLIST_CHANGE_ERROR_GENERIC);
                ret.setUser(PhoenixDataService.getSIP(s));

                // Null subscriber is not implemented yet. Remote contacts are not supported yet.
                if (s==null){
                    resList.getResults().add(ret);
                    continue;
                }

                // pairing request condition
                final boolean doPairing = request.isManagePairingRequests() && (elem.isManagePairingRequests() == null || elem.isManagePairingRequests());

                // Changing contact list for somebody else is not implemented.
                final String targetUser = StringUtils.isEmpty(elem.getTargetUser()) ? ownerSip : elem.getTargetUser();
                final Subscriber targetOwner = owner;
                if (!ownerSip.equals(targetUser)){
                    log.warn("Changing contactlist for somebody else is not permitted");
                    resList.getResults().add(ret);
                    continue;
                }

                // users whose contact list was changed - updating presence rules afterwards
                if (!changedUsers.containsKey(targetUser)){
                    changedUsers.put(targetUser, targetOwner);
                }

                // is there already some contact list item?
                Contactlist cl = this.dataService.getContactlistForSubscriber(targetOwner, s);
                final ContactlistAction action = elem.getAction();
                try {
                    if (cl!=null){
                        // contact list entry is empty -> record does not exist
                        if (action == ContactlistAction.REMOVE){
                            this.dataService.remove(cl, true);
                            ret.setResultCode(1);
                            resList.getResults().add(ret);

                            // Pairing fix, if desired.
                            if (doPairing) {
                                pairingMgr.onUserXRemovedYFromContactlist(targetOwner, s);
                            }

                            continue;
                        }

                        // enable/disable?
                        if (action==ContactlistAction.DISABLE || action==ContactlistAction.ENABLE){
                            cl.setEntryState(action == ContactlistAction.DISABLE ? ContactlistStatus.DISABLED : ContactlistStatus.ENABLED);
                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            resList.getResults().add(ret);
                        }

                        // add action
                        if (action==ContactlistAction.ADD){
                            // makes no sense, already in
                            ret.setResultCode(CLIST_CHANGE_ERROR_ALREADY_ADDED);
                            resList.getResults().add(ret);
                            log.info("Wanted to add already existing user");
                            continue;
                        }

                        // update - displayName
                        if (action==ContactlistAction.UPDATE){
                            // Display name update, may be null, not updating maybe.
                            if (!StringUtils.isEmpty(elem.getDisplayName())){
                                final String newDispName = StringUtils.takeMaxN(elem.getDisplayName(), 128);
                                cl.setDisplayName(newDispName);
                            }

                            // Aux data.
                            if (!StringUtils.isEmpty(elem.getAuxData())){
                                final String newAuxData = StringUtils.takeMaxN(elem.getDisplayName(), 4096);
                                cl.setAuxData(newAuxData);
                            }

                            // Primary group
                            if (elem.getPrimaryGroup() != null){
                                final ContactGroup primaryGroup = dataService.getContactGroup(elem.getPrimaryGroup());
                                cl.setPrimaryGroup(primaryGroup);
                            }

                            // TODO: multiple groups?
                            // ...

                            cl.setDateLastEdit(new Date());
                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            resList.getResults().add(ret);
                        }

                        // whitelist action
                        WhitelistAction waction = elem.getWhitelistAction();
                        if (waction!=null && waction!=WhitelistAction.NOTHING){
                            // operate on whitelist here
                            boolean normalReq = false;
                            if (waction==WhitelistAction.DISABLE || waction==WhitelistAction.REMOVE){
                                // disabling from whitelist
                                cl.setInWhitelist(false);
                                normalReq = true;
                            } else if (waction==WhitelistAction.ENABLE || waction==WhitelistAction.ADD) {
                                // enabling in whitelist
                                cl.setInWhitelist(true);
                                normalReq = true;
                            }

                            // request makes sense
                            if (normalReq){
                                this.dataService.persist(cl, true);
                                ret.setResultCode(1);
                                resList.getResults().add(ret);
                                continue;
                            }
                        }
                    } else {
                        // contact list entry is empty -> record does not exist
                        if (action == ContactlistAction.REMOVE){
                            ret.setResultCode(CLIST_CHANGE_ERROR_NO_USER);
                            resList.getResults().add(ret);
                            log.info("Wanted to delete non-existing whitelist record");
                            continue;
                        }

                        // add action
                        if (action == ContactlistAction.ADD){
                            String newDispName = elem.getDisplayName();
                            if (!StringUtils.isEmpty(newDispName)){
                                newDispName = StringUtils.takeMaxN(newDispName, 128);
                            }

                            final WhitelistAction waction = elem.getWhitelistAction();
                            cl = new Contactlist();
                            cl.setDateCreated(new Date());
                            cl.setDateLastEdit(new Date());
                            cl.setOwner(owner);
                            cl.setObjType(ContactlistObjType.INTERNAL_USER);
                            cl.setObj(new ContactlistDstObj(s));
                            cl.setEntryState(ContactlistStatus.ENABLED);
                            cl.setDisplayName(newDispName);
                            cl.setInBlacklist(false);
                            cl.setAuxData(elem.getAuxData());
                            cl.setInWhitelist(waction == WhitelistAction.ENABLE || waction == WhitelistAction.ADD);

                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            resList.getResults().add(ret);

                            // Primary group.
                            if (elem.getPrimaryGroup() != null){
                                final ContactGroup primaryGroup = dataService.getContactGroup(elem.getPrimaryGroup());
                                cl.setPrimaryGroup(primaryGroup);
                            }

                            // Pairing fix, if desired.
                            if (doPairing) {
                                pairingMgr.onUserXAddedYToContactlist(targetOwner, s);
                            }

                        } else {
                            ret.setResultCode(CLIST_CHANGE_ERROR_NO_USER);
                            resList.getResults().add(ret);
                        }
                    }
                } catch(Exception e){
                    log.info("Manipulation with contactlist failed", e);
                    ret.setResultCode(CLIST_CHANGE_ERROR_GENERIC);
                    resList.getResults().add(ret);
                }
            }

            //
            // Now is time to re-generate presence view policies and trigger server update
            // and roster synchronization.
            //
            for(Entry<String, Subscriber> entry : changedUsers.entrySet()){
                // regenerating policy for given contact
                try {
                    Subscriber tuser = entry.getValue();
                    this.dataService.resyncRoster(tuser);
                } catch(Exception ex){
                    log.error("Exception during presence rules generation for: " + entry.getValue(), ex);
                }
            }

            // TODO: in multi device setting consider broadcasting newClist push event to another connected devices.

        } catch(Exception e){
            log.info("Exception ocurred", e);
            return null;
        }

        return response;
    }

    /**
     * Deprecated version of the call.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public GetCertificateResponse getCertificateOld(GetCertificateRequest request, MessageContext context) throws CertificateException {
        String owner = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (getCertificate): " + owner);

        // support also remote users :)
        // 1. try to load local user
        Subscriber sub = null;
        RemoteUser rem = null;

        // Monitor if user is asking for different certificates than his.
        // If yes, use this infromation to set first user add date if is empty.
        boolean askingForDifferentThanOurs = false;
        boolean askingForServer = false;
        final Date curDate = new Date();

        // for whitelist searching (ommiting groups right now)
        // searching as destination in someones whitelist
        WhitelistDstObj dstObj = new WhitelistDstObj();

        // at first try local
        sub = this.dataService.getLocalUser(owner);
        // now try remote
        if (sub==null){
            rem = this.dataService.getRemoteUser(owner);
            // set remote
            dstObj.setExtern_user(rem);
        } else {
            // sub is not null, set intern
            dstObj.setIntern_user(sub);
        }

        // still null?
        if (sub==null && rem==null){
            throw new IllegalArgumentException("User you are claiming you are does not exist!");
        }

        // define answer, will be iteratively filled in
        GetCertificateResponse response = new GetCertificateResponse();
        final List<CertificateWrapper> crtRet = response.getReturn();

        // maximum length?
        if (request.getElement() == null || request.getElement().isEmpty() || request.getElement().size() > 4096){
            throw new IllegalArgumentException(String.format("Invalid size of request: %d", MiscUtils.collectionSize(request.getElement())));
        }

        // Iterate over certificate requests and process it one-by-one.
        for(CertificateRequestElement el : request.getElement()){
            // At first obtain user object we are talking about.
            // Now assume only local user, we will have procedure for extern and groups also
            // in future (I hope so:)).
            Subscriber s = null;
            String sip = el.getUser();
            if (sip!=null && !sip.isEmpty()){
                s = dataService.getLocalUser(sip);
            } else {
                throw new RuntimeException("Both user identifiers are null");
            }

            askingForDifferentThanOurs |= !owner.equalsIgnoreCase(sip);

            // current certificate answer
            CertificateWrapper wr = new CertificateWrapper();

            // special case - server certificate
            // TODO: refactor this, unclean method
            if (sip!=null && "server".equalsIgnoreCase(sip)){
                log.info("Obtaining server certificate ["+owner+"]");
                // now just add certificate to response
                wr.setStatus(CertificateStatus.OK);
                wr.setUser(sip);
                wr.setCertificate(this.trustManager.getServerCA().getEncoded());
                crtRet.add(wr);
                continue;
            }

            // null subscriber - fail, user has to exist in database
            if (s==null){
                log.info("User is not found in database, " + sip + "; requestor: " + owner);
                wr.setStatus(CertificateStatus.NOUSER);
                crtRet.add(wr);
                continue;
            } else {
                log.info("User to obtain certificate for: " + s.getUsername() + "; requestor: " + owner);
            }

            // init return structure
            wr.setStatus(CertificateStatus.MISSING);
            wr.setUser(PhoenixDataService.getSIP(s));
            wr.setCertificate(null);

            // If user provided certificate hash, first check whether it is valid
            // and real certificate for user. If yes, then just confirm this to save
            // bandwidth. No certificate is really sent to user, no real certificate
            // checks are done (expiration, signature, etc...)
            String certHash = el.getCertificateHash();
            if (!StringUtils.isEmpty(certHash)){
                boolean valid = false;
                try {
                    valid = this.dataService.isProvidedHashValid(s, certHash);
                } catch(Exception ex){
                    log.warn("Exception during testing existing certificate hash", ex);
                }

                // it is valid -> no need to continue
                if (valid==true){
                    wr.setProvidedCertStatus(CertificateStatus.OK);
                    wr.setStatus(CertificateStatus.OK);
                    wr.setCertificate(null);
                    crtRet.add(wr);
                    continue;
                } else {
                    wr.setProvidedCertStatus(CertificateStatus.INVALID);
                }
            }

            // obtain certificate for particular subscriber
            CAcertsSigned cert = this.dataService.getCertificateForUser(s);
            if (cert==null || cert.getCert()==null){
                log.info("Certificate for user ["+s.getUsername()+"] is null");
                log.info("cert: " + cert);
                if (cert!=null){
                    log.info("Cert not null: " + cert.getCert());
                    log.info("Cert not null len: " + cert.getRawCert().length);
                }

                wr.setStatus(CertificateStatus.MISSING);
                crtRet.add(wr);
                continue;
            }

            // certificate
            X509Certificate cert509 = cert.getCert();
            log.info("cert is not null!; DBserial=[" + cert.getSerial() + "].");
            log.debug("cert is not null!; DBserial=[" + cert.getSerial() + "]. Real ceritificate: " + cert509);

            // time validity
            try{
                // time-date validity
                cert509.checkValidity();

                // is certificate valid?
                this.trustManager.checkTrusted(cert509);

                // is revoked?
                Boolean certificateRevoked = this.signer.isCertificateRevoked(cert509);
                if (certificateRevoked!=null && certificateRevoked.booleanValue()==true){
                    log.info("Certificate for user "+(s.getUsername())+" is revoked: " + cert509.getSerialNumber().longValue());
                    wr.setStatus(CertificateStatus.REVOKED);
                    crtRet.add(wr);
                    continue;
                }
            } catch(Exception e){
                // certificate is invalid
                log.info("Certificate for user "+(s.getUsername())+" is invalid", e);
                wr.setStatus(CertificateStatus.INVALID);
                crtRet.add(wr);
                continue;
            }

            // now just add certificate to response
            wr.setStatus(CertificateStatus.OK);
            wr.setCertificate(cert509.getEncoded());
            crtRet.add(wr);
        }

        // Logic for setting first user added field.
        updateStatsForCertGet(sub, askingForDifferentThanOurs);
        logAction(owner, "getCert", null);
        return response;
    }

    /**
     * Method for obtaining user certificate. 
     * User calls this if wants to communicate with remote user and in never communicated
     * with it before.
     * 
     * This method can be called by remote users also (more servers setup) thus
     * it does not necessarily have subscriber record here. 
     * 
     * If user provides hash of existing certificate then is only lightweight check
     * performed - test if given certificate with given hash is valid for given user.
     * No actual certificate test is performed (time validity & so on). This is left
     * up to the user.
     * 
     * For authentication do the following:
     *  - check certificate time validity & signature of CA
     *  - extract SIP from certificate (must be valid and present)
     *  - check if target user has this current user in whitelist 
     *      (user can try to obtain its own certificate)
     * 
     * If every this condition passed, then is certificate returned.
     * 
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "getCertificateRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public GetCertificateResponse getCertificate(@RequestPayload GetCertificateRequest request, MessageContext context) throws CertificateException {
        try {
            String owner = this.authRemoteUserFromCert(context, this.request);
            log.info("Remote user connected (getCertificate): " + owner);

            // Support also remote users.
            RemoteUser rem = null;

            // Monitor if user is asking for different certificates than his.
            // If yes, use this information to set first user add date if is empty.
            boolean askingForDifferentThanOurs = false;
            boolean askingForServer = false;
            final Date curDate = new Date();

            // at first try local
            final Subscriber sub = this.dataService.getLocalUser(owner);
            // now try remote
            if (sub == null) {
                rem = this.dataService.getRemoteUser(owner);
            }

            // still null?
            if (sub == null && rem == null) {
                throw new IllegalArgumentException("User you are claiming you are does not exist!");
            }

            // define answer, will be iteratively filled in
            GetCertificateResponse response = new GetCertificateResponse();
            final List<CertificateWrapper> crtRet = response.getReturn();

            // maximum length?
            if (request.getElement() == null || request.getElement().isEmpty() || request.getElement().size() > 4096) {
                throw new IllegalArgumentException(String.format("Invalid size of request: %d", MiscUtils.collectionSize(request.getElement())));
            }

            // SQL for loading certificates based on the hash.
            final String certHashQuery = "SELECT cs FROM CAcertsSigned cs WHERE certHash IN :hashes";

            // Load subscriber in bulks from the database.
            DBObjectLoader<Subscriber, String> subscriberLoader = new DBObjectLoader<Subscriber, String>(Subscriber.class, dataService);
            subscriberLoader.setSqlStatement("SELECT u FROM Subscriber u WHERE CONCAT(u.username, '@', u.domain) IN :sip");
            subscriberLoader.setWhereInColumn("sip");
            subscriberLoader.setOperationThreshold(250);

            // Statistics for debugging.
            final int requestSize = MiscUtils.collectionSize(request.getElement());
            int loadedSubscribers = 0;
            int certHashesProvided = 0;
            int certLoadedByHash = 0;
            int certValidByHash = 0;
            int certMissingByHash = 0;
            int certLoadedTotal = 0;
            int certNotFound = 0;
            int noUserCount = 0;

            // Extract SIP user names for bulk load & certificate hashes for easy check.
            final Set<String> sipUsers = new HashSet<String>();
            // Sip -> Certificate hash record.
            final Map<String, String> certHashes = new HashMap<String, String>();
            for (CertificateRequestElement el : request.getElement()) {
                final String sip = SipUri.getCanonicalSipContact(el.getUser(), false);
                final String certHash = el.getCertificateHash();

                sipUsers.add(sip);
                subscriberLoader.add(sip);
                askingForDifferentThanOurs |= !owner.equalsIgnoreCase(sip);
                askingForServer |= "server".equalsIgnoreCase(sip);

                if (!StringUtils.isEmpty(certHash)) {
                    certHashes.put(sip, certHash);
                }
            }

            // Set of found users in the database, from this users not found in DB are computed.
            final Set<String> foundUsersInDb = new HashSet<String>();
            // Do the mass query for Subscribers, load in bulks (250). Load certificates for those bulks.
            while (subscriberLoader.loadNewData()) {
                // Load all subscribers in this bulk.
                final List<Subscriber> subs = subscriberLoader.getLoadedData();
                // All certificate hashes to be loaded in a bulk in this stage.
                final Map<String, String> curCertHashes = new HashMap<String, String>();
                // Set of all cert hashes found in the database.
                final Set<String> usersWithFoundCertHashes = new HashSet<String>();
                // List of subscribers that need to load new certificates for.
                final List<Subscriber> subToLoadCert = new ArrayList<Subscriber>(subs.size());
                // Set of all users (SIP) with satisfied certificate request. Users not here should return MISSING status as no certificate was found for them.
                final Set<String> sipWithCertDone = new HashSet<String>();
                // Certificate status for certificates with provided cert hash.
                final Map<String, CertificateStatus> certStatus = new HashMap<String, CertificateStatus>();
                // Sip -> Sub map.
                final Map<String, Subscriber> subMap = new HashMap<String, Subscriber>();

                // Process this bulk and prepare state structures, cert hash grouping.
                for (Subscriber s : subs) {
                    final String curSip = PhoenixDataService.getSIP(s);
                    foundUsersInDb.add(curSip);
                    subMap.put(curSip, s);
                    loadedSubscribers += 1;

                    // Load certificate hashes in this bulk.
                    if (certHashes.containsKey(curSip)) {
                        curCertHashes.put(curSip, certHashes.get(curSip));
                        certHashesProvided += 1;
                    } else {
                        subToLoadCert.add(s);
                    }
                }

                // Load all certificates with hashes provided by users.
                List<CAcertsSigned> resultList = curCertHashes.isEmpty() ?
                        new ArrayList<CAcertsSigned>() :
                        em.createQuery(certHashQuery, CAcertsSigned.class)
                                .setParameter("hashes", curCertHashes.values())
                                .getResultList();

                for (CAcertsSigned cert : resultList) {
                    final String certSip = cert.getSubscriberName();
                    final boolean isValid = !cert.getIsRevoked()
                            && cert.getNotValidAfter() != null
                            && cert.getNotValidAfter().after(curDate);
                    certLoadedByHash += 1;
                    certValidByHash += isValid ? 1 : 0;

                    // Mark that for this user certificate was found based on the cert hash.
                    usersWithFoundCertHashes.add(certSip);

                    if (!isValid) {
                        // Certificate is not valid, load a new one.
                        subToLoadCert.add(subMap.get(certSip));
                    } else {
                        // Certificate is done, mark as finished.
                        sipWithCertDone.add(certSip);
                        // Certificate request finished -> add response to the list.
                        CertificateWrapper w = new CertificateWrapper();
                        w.setUser(certSip);
                        w.setProvidedCertStatus(CertificateStatus.OK);
                        w.setStatus(CertificateStatus.OK);
                        w.setCertificate(null);
                        crtRet.add(w);
                    }

                    certStatus.put(certSip, isValid ? CertificateStatus.OK : CertificateStatus.INVALID);
                }

                // Handle users with provided cert hash but no certificate was found for given cert hash.
                for(String userSipWithProvidedCertHash : curCertHashes.keySet()){
                    if (usersWithFoundCertHashes.contains(userSipWithProvidedCertHash)){
                        continue;
                    }

                    certMissingByHash += 1;
                    certStatus.put(userSipWithProvidedCertHash, CertificateStatus.MISSING);
                }

                // Load all certificates without hashes, new certificates whose hash does not match, total bulk.
                // subToLoadCert might got extended when provided certificate referenced with cert hash is invalid.
                // Certificates with CertificateStatus.OK in certStatus are not here, not needed to load.
                // Still need to handle missing certificates.
                final Map<String, CAcertsSigned> loadedCertList = dataService.getCertificatesForUsers(subToLoadCert);
                for (CAcertsSigned curCert : loadedCertList.values()) {
                    final String curSip = curCert.getSubscriberName();
                    sipWithCertDone.add(curSip);

                    // Certificate request finished -> add response to the list.
                    CertificateWrapper w = new CertificateWrapper();
                    w.setUser(curSip);
                    w.setStatus(CertificateStatus.OK);
                    w.setCertificate(curCert.getRawCert());
                    // Here situation when provided cert is invalid, but we are returning a new valid one handled.
                    if (certStatus.containsKey(curSip)) {
                        w.setProvidedCertStatus(certStatus.get(curSip));
                    }

                    crtRet.add(w);
                    certLoadedTotal += 1;
                }

                // Here handle response generation for users without any valid certificate.
                for (Subscriber curSub : subs) {
                    final String curSip = PhoenixDataService.getSIP(curSub);
                    if (sipWithCertDone.contains(curSip)){
                        continue;
                    }

                    // Certificate request finished -> add response to the list.
                    CertificateWrapper w = new CertificateWrapper();
                    w.setUser(curSip);
                    w.setStatus(CertificateStatus.MISSING);
                    w.setCertificate(null);
                    // If provided certificate hash pointed to invalid certificate.
                    if (certStatus.containsKey(curSip)) {
                        w.setProvidedCertStatus(certStatus.get(curSip));
                    }

                    crtRet.add(w);

                    // Add to request-handled-set.
                    sipWithCertDone.add(curSip);
                    certNotFound += 1;
                }
            }

            // Finish request - add those not found in the user database.
            for(String curSip : sipUsers){
                if (foundUsersInDb.contains(curSip)){
                    continue;
                }

                // Certificate request finished -> add response to the list.
                CertificateWrapper w = new CertificateWrapper();
                w.setUser(curSip);
                w.setStatus(CertificateStatus.NOUSER);
                w.setCertificate(null);
                crtRet.add(w);
                noUserCount += 1;
            }

            // Special case - asking for server certificate
            if (askingForServer){
                log.info("Obtaining server certificate ["+owner+"]");
                CertificateWrapper wr = new CertificateWrapper();
                wr.setStatus(CertificateStatus.OK);
                wr.setUser("server");
                wr.setCertificate(this.trustManager.getServerCA().getEncoded());
                crtRet.add(wr);
            }

            // Logic for setting first user added field.
            updateStatsForCertGet(sub, askingForDifferentThanOurs);
            logAction(owner, "getCert", null);

            // Statistics for debugging.
            log.info(String.format("GetCertificate: %s, requestSize: %d, loadedUsers: %d, certHashesProvided: %d, " +
                    "certLoadedByHash: %d, certValidByHash: %d, certMissingByHash: %d, certLoadedRaw: %d, " +
                    "certNotFound: %d, noUserCount: %d", owner, requestSize, loadedSubscribers, certHashesProvided,
                    certLoadedByHash, certValidByHash, certMissingByHash, certLoadedTotal, certNotFound, noUserCount));

            return response;

        } catch(Exception ex){
            log.error("Exception in new getCertificate(), fallback to old getCertificate() method", ex);
            return getCertificateOld(request, context);
        }
    }

    /**
     * Updates date statistics for the user.
     * @param sub
     * @param askingForDifferentThanOurs
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    private void updateStatsForCertGet(Subscriber sub, boolean askingForDifferentThanOurs){
        if (sub == null){
            return;
        }

        try {
            final String owner = PhoenixDataService.getSIP(sub);

            // If user does not have first login date filled in, add this one.
            Calendar fUserAdded = sub.getDateFirstUserAdded();
            if (askingForDifferentThanOurs && (fUserAdded == null || fUserAdded.before(get1971()))) {
                sub.setDateFirstUserAdded(Calendar.getInstance());
                log.info(String.format("First user added date set to: %s, for %s", sub.getDateFirstUserAdded().getTime(), owner));
            }

            // First login if not set.
            Calendar fLogin = sub.getDateFirstLogin();
            if (fLogin == null || fLogin.before(get1971())) {
                sub.setDateFirstLogin(Calendar.getInstance());
                log.info(String.format("First login set to: %s, for %s", sub.getDateFirstLogin().getTime(), owner));
            }

            // Update last activity date.
            sub.setDateLastActivity(Calendar.getInstance());
            sub.setLastActionIp(auth.getIp(this.request));
            em.persist(sub);
            log.info(String.format("Last activity set to: %s, for %s", sub.getDateLastActivity().getTime(), owner));

        } catch(Throwable t){
            log.error("Exception in writing statistics for the user", t);
        }
    }
    
    /**
     * Changing user password. 
     * 
     * With this call user can change password even for different user (in its group).
     * Passwords for both HA1 and HA1B has to be provided.
     * 
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "passwordChangeV2Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public PasswordChangeV2Response passwordChangeV2(@RequestPayload PasswordChangeV2Request request, MessageContext context) throws CertificateException {
        // protect user identity and avoid MITM, require SSL
        this.checkOneSideSSL(context, this.request);
        
        // user is provided in request thus try to load it from database
        String sip = request.getUser();
        log.info("User [" + sip + "] is asking to change password");

        // user matches, load subscriber data for him
        Subscriber owner = this.dataService.getLocalUser(sip);
        if (owner == null){
            log.warn("Local user was not found in database for: " + sip);
            throw new IllegalArgumentException("Not authorized");
        }
        if (owner.isDeleted()){
            log.warn("Local user is deleted/disabled. SIP: " + sip);
            throw new IllegalArgumentException("Not authorized");
        }
        log.info("Remote user connected (passwordChange): " + sip);
        
        // construct response wrapper
        PasswordChangeV2Response response = new PasswordChangeV2Response();
        response.setResult(-1);
        
        // obtain new password encrypted by my AES
        byte[] newHA1 = request.getNewHA1();
        byte[] newHA1B = request.getNewHA1B();
        
        if (newHA1==null || newHA1B==null){
            log.warn("One (or both) of HA1, HA1B passwords is empty; ha1: " + newHA1 + "; ha2: " + newHA1B);
            log.debug("Request object: " + request.toString());
            throw new IllegalArgumentException("Null password");
        }
                
        try {   
            // Integer version by default set to 2. 
            // Changes window of the login validity.
            int reqVersion = 2;
            Integer reqVersionInt = request.getVersion();
            if (reqVersionInt != null){
                reqVersion = reqVersionInt.intValue();
            }
            
            // Adjust millisecond window size for auth hash validity.
            // This was increased in v3 so users are not bullied for not 
            // minute-precise time setup.
            long milliSecondWindowSize = 1000L * 60L;
            if (reqVersion >= 2){
                milliSecondWindowSize = 1000L * 60L * 10L;
            } 
            
            // check token here!
            boolean ott_valid = this.dataService.isOneTimeTokenValid(sip, request.getUsrToken(), request.getServerToken(), "");
            if (ott_valid==false){
                log.warn("Invalid one time token");
                throw new RuntimeException("Not authorized");
            }
            
            // check user AUTH hash
            // generate 3 tokens, for 3 time slots
            String userHashes[] = new String[3];
            // store correct encryption key
            String encKeys[] = new String[3];
            for (int i=-1, c=0; i <=1 ; i++, c++){
                userHashes[c] = this.dataService.generateUserAuthToken(
                    sip, owner.getHa1(), 
                    request.getUsrToken(), request.getServerToken(), 
                    milliSecondWindowSize, i);
                encKeys[c] = this.dataService.generateUserEncToken(sip, owner.getHa1(), 
                    request.getUsrToken(), request.getServerToken(), 
                    milliSecondWindowSize, i);
            }
            
            // verify one of auth hashes
            boolean authHash_valid=false;
            String encKey = "";
            for (int c=0; c<=2; c++){
                String curAuthHash = userHashes[c];
                log.info("Verify auth hash["+request.getAuthHash()+"] vs. genhash["+curAuthHash+"] window: "+ milliSecondWindowSize);
                if (curAuthHash.equals(request.getAuthHash())){
                    encKey = encKeys[c];
                    authHash_valid=true;
                    break;
                }
            }
            
            if (authHash_valid==false){
                log.warn("Invalid auth hash");
                throw new RuntimeException("Not authorized");
            }
            
            // here we can decrypt passwords
            String newHA1Dec=null;
            String newHA1BDec=null;
            try {
                newHA1Dec = new String(AESCipher.decrypt2(newHA1, encKey.toCharArray()), "UTF-8");
                newHA1BDec = new String(AESCipher.decrypt2(newHA1B, encKey.toCharArray()), "UTF-8");
            } catch(Exception e){
                log.warn("Error during decrypting new passwords");
                throw new RuntimeException("Not authorized");
            }
            
            if (newHA1Dec==null || newHA1BDec==null){
                log.warn("Some of decrypted passwords (or both) is null");
                throw new RuntimeException("Not authorized");
            }
            
            // ok, now ignore target user option, for NOW
            // TODO: add support for groups and changing user 
            String targetUser = request.getTargetUser();
            if (targetUser==null || targetUser.isEmpty()){
                targetUser=sip;
            }
            
            if (sip.equals(targetUser)==false){
                log.warn("Not implemented feature: changing pasword for different user");
                throw new UnsupportedOperationException("This feature is not implemented yet.");
            }
            
            // so now we change user password for targetUser.
            Subscriber targetUserObj = this.dataService.getLocalUser(request.getTargetUser());
            // and just change password. this must be safe, in transaction, since 
            targetUserObj.setHa1(newHA1Dec);
            targetUserObj.setHa1b(newHA1BDec);
            targetUserObj.setForcePasswordChange(false);
            targetUserObj.setDateLastPasswordChange(Calendar.getInstance());
            em.persist(targetUserObj);
            
            response.setResult(1);
            response.setTargetUser(request.getTargetUser());
            response.setReason("Password changes successfully");
         } catch(Exception e){
             log.warn("Exception in password change procedure", e);
             throw new RuntimeException(e);
         }
         
        return response;
    }

    /**
     * Testing authentication.
     * 
     * User can provide its auth hash and test its authentication with password.
     * This service is available on both ports with user certificate required or not.
     * Thus user can provide certificate and in this case it will be tested as well.
     * 
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "authCheckV3Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public AuthCheckV3Response authCheckV3(@RequestPayload AuthCheckV3Request request, MessageContext context) throws CertificateException {
        // protect user identity and avoid MITM, require SSL
        this.checkOneSideSSL(context, this.request);
        
        AuthCheckV3Response resp = new AuthCheckV3Response();
        resp.setAuthHashValid(TrueFalse.FALSE);
        resp.setCertValid(TrueFalseNA.NA);
        resp.setCertStatus(CertificateStatus.MISSING);
        resp.setForcePasswordChange(TrueFalse.FALSE);
        resp.setAccountDisabled(true);
        resp.setAuxVersion(0);
        resp.setAuxJSON("");
        resp.setErrCode(404);
        JSONObject jsonAuxObj = new JSONObject();

        try {
            // Integer version by default set to 3. 
            // Changes window of the login validity.
            int reqVersion = 3;
            Integer reqVersionInt = request.getVersion();
            if (reqVersionInt != null){
                reqVersion = reqVersionInt.intValue();
            }
            
            // Adjust millisecond window size for auth hash validity.
            // This was increased in v3 so users are not bullied for not 
            // minute-precise time setup.
            long milliSecondWindowSize = 1000L * 60L;
            if (reqVersion >= 3){
                milliSecondWindowSize = 1000L * 60L * 10L;
            }
            
            // Date conversion can throw exception
            resp.setAccountExpires(getXMLDate(new Date()));
            resp.setServerTime(getXMLDate(new Date()));
            resp.setAccountIssued(getXMLDate(new Date()));
            
            // user is provided in request thus try to load it from database
            final String sip = request.getTargetUser();
            log.info(String.format("User [%s] is asking to verify credentials, reqVer: %d", sip, reqVersion));
            
            // user matches, load subscriber data for him
            Subscriber localUser = this.dataService.getLocalUser(sip);
            if (localUser == null){
                log.warn("Local user was not found in database for: " + sip);
                return resp;
            }

            // check user AUTH hash
            // generate 3 tokens, for 3 time slots
            boolean authHash_valid=false;
            for (int i=-1, c=0; i <=1 ; i++, c++){
                String userHash = this.dataService.generateUserAuthToken(
                    sip, localUser.getHa1(), 
                    "", "", 
                    milliSecondWindowSize, i);
                
                log.info("Verify auth hash["+request.getAuthHash()+"] vs. genhash["+userHash+"]");
                if (userHash.equals(request.getAuthHash())){
                    resp.setAuthHashValid(TrueFalse.TRUE);
                    authHash_valid=true;
                    break;
                }
            }

            // Invalid login here. You shall not pass!
            if (!authHash_valid){
                logAction(sip, "authCheck3.fail", null);
                return resp;
            }
            
            // User valid ?
            resp.setAccountDisabled(localUser.isDeleted());
            resp.setErrCode(200);
            
            // Time can be null, account expire time.
            Calendar cal = localUser.getExpires();
            resp.setAccountExpires(cal == null ? null : getXMLDate(cal.getTime()));
            
            // Account issued time.
            Calendar calIssued = localUser.getIssued();
            resp.setAccountIssued(calIssued == null ? null : getXMLDate(calIssued.getTime()));
            
            Calendar firstUserAddTime = localUser.getDateFirstUserAdded();
            resp.setFirstUserAddDate(firstUserAddTime == null ? null : getXMLDate(firstUserAddTime.getTime()));
            
            Calendar firstLogin = localUser.getDateFirstLogin();
            resp.setFirstLoginDate(firstLogin == null ? null : getXMLDate(firstLogin.getTime()));
            
            Calendar lastActivity = localUser.getDateLastActivity();
            resp.setAccountLastActivity(lastActivity == null ? null : getXMLDate(lastActivity.getTime()));
            
            Calendar lastPasswdChange = localUser.getDateLastPasswordChange();
            resp.setAccountLastPassChange(lastPasswdChange == null ? null : getXMLDate(lastPasswdChange.getTime()));
            
            Calendar firstAuthCheck = localUser.getDateFirstAuthCheck();
            resp.setFirstAuthCheckDate(firstAuthCheck == null ? null : getXMLDate(firstAuthCheck.getTime()));
            
            Calendar lastAuthCheck = localUser.getDateLastAuthCheck();
            resp.setLastAuthCheckDate(lastAuthCheck == null ? null : getXMLDate(lastAuthCheck.getTime()));
            
            // License type.
            String licType = localUser.getLicenseType();
            resp.setLicenseType(licType == null ? "-1" : licType);
            
            // Number of stored files for given user.
            resp.setStoredFilesNum(Integer.valueOf((int)fmanager.getStoredFilesNum(localUser)));

            // If user was deleted, login was not successful.
            if (localUser.isDeleted()){
                resp.setErrCode(405);
                logAction(sip, "authCheck3.deleted", null);
                return resp;
            }
            
            // Force password change?
            Boolean passwdChange = localUser.getForcePasswordChange();
            if (passwdChange!=null && passwdChange==true){
                resp.setForcePasswordChange(TrueFalse.TRUE);
            }

            // Type of the testing account. Alpha / beta.
            if (!StringUtils.isEmpty(localUser.getTestingSettings())){
                try {
                    final String testingSettingsStr = localUser.getTestingSettings();
                    final JSONObject testingSettings = new JSONObject(testingSettingsStr);

                    jsonAuxObj.put("testingSettings", testingSettings);
                    resp.setAuxJSON(jsonAuxObj.toString());
                } catch(Exception e){
                    log.error("Testing settings could not be parsed", e);
                }
            }

            // AuxData
            if (!StringUtils.isEmpty(localUser.getAuxData())){
                try {
                    final String auxDataStr = localUser.getAuxData();
                    final JSONObject auxData = new JSONObject(auxDataStr);

                    jsonAuxObj.put("auxData", auxData);
                    resp.setAuxJSON(jsonAuxObj.toString());
                } catch(Exception e){
                    log.error("Aux data could not be parsed", e);
                }
            }

            // AUXJson - trial event logs.
            if (localUser.getExpires() != null && localUser.getExpires().before(Calendar.getInstance())){
                final List<TrialEventLog> logs = dataService.getTrialEventLogs(localUser, null);
                final JSONObject jsonObj = dataService.eventLogToJson(logs, localUser);
                jsonAuxObj.put("evtlog", jsonObj);
                resp.setAuxJSON(jsonAuxObj.toString());
            }

            // Support contacts.
            dataService.setSupportContacts(localUser, jsonAuxObj);
            resp.setAuxJSON(jsonAuxObj.toString());

            // Turn password.
            try {
                final String turnPasswd = localUser.getTurnPasswd();
                if (turnPasswd == null || turnPasswd.length() == 0) {
                    final String turnPasswdGen = PasswordGenerator.genPassword(24, true);
                    localUser.setTurnPasswd(turnPasswdGen);
                    localUser.setTurnPasswdHa1b(MiscUtils.getHA1(PhoenixDataService.getSIP(localUser), localUser.getDomain(), turnPasswdGen));
                }

                // Fix turn ha1b password if missing.
                final String turnHa1b = localUser.getTurnPasswdHa1b();
                if (turnHa1b == null || turnHa1b.length() == 0){
                    localUser.setTurnPasswdHa1b(MiscUtils.getHA1(PhoenixDataService.getSIP(localUser), localUser.getDomain(), turnPasswd));
                }

                // Base field - action/method of this message.
                jsonAuxObj.put(AUTH_TURN_PASSWD_KEY, localUser.getTurnPasswd());
                resp.setAuxJSON(jsonAuxObj.toString());

                // TODO: send AMQP message to the TURN server so it updates auth credentials.
            } catch(Throwable th){
                log.error("Exception in authcheck, turn password set.", th);
            }

            // Store app version provided by the user so we have statistics of update and for debugging.
            String appVersion = StringUtils.takeMaxN(request.getAppVersion(), 1024);
            if (appVersion != null){
                localUser.setAppVersion(appVersion);
            }

            // Update last activity date.
            localUser.setDateLastAuthCheck(Calendar.getInstance());
            localUser.setLastAuthCheckIp(auth.getIp(this.request));

            // First login if not set.
            Calendar fAuthCheck = localUser.getDateFirstAuthCheck();
            if (fAuthCheck == null || fAuthCheck.before(get1971())){
                localUser.setDateFirstAuthCheck(Calendar.getInstance());
                log.info(String.format("First auth check set to: %s", localUser.getDateFirstAuthCheck()));
            }
            
            // if we have some certificate, we can continue with checks
            X509Certificate[] chain = auth.getCertificateChainFromConnection(context, this.request);
            if (chain==null){
                em.persist(localUser);
                logAction(sip, "authCheck3", null);
                return resp;
            }
            
            // now try to test certificate if any provided
            try {
                Subscriber owner = this.authUserFromCert(context, this.request);
                String certSip = auth.getSIPFromCertificate(context, this.request);
                log.info("User provided also certificate: " + owner);
                if (owner == null || certSip == null){
                    return resp;
                }
                
                // check user validity
                if (!certSip.equals(sip)){
                    resp.setCertValid(TrueFalseNA.FALSE);
                    return resp;
                }
                
                // Get certificate chain from cert parameter. Obtain user certificate
                // that was used in connection
                X509Certificate certChain[] = auth.getCertChain(context, this.request);
                // client certificate SHOULD be stored first here, so assume it
                X509Certificate cert509 = certChain[0];
                // time-date validity
                cert509.checkValidity();
                // is signed by server CA?
                //cert509.verify(this.trustManager.getServerCA().getPublicKey());
                trustManager.checkTrusted(cert509);
                
                // is revoked?
                Boolean certificateRevoked = this.signer.isCertificateRevoked(cert509);
                if (certificateRevoked!=null && certificateRevoked.booleanValue()==true){
                    log.info("Certificate for user is revoked: " + cert509.getSerialNumber().longValue());
                    resp.setCertValid(TrueFalseNA.FALSE);
                    resp.setCertStatus(CertificateStatus.REVOKED);
                } else {
                    resp.setCertValid(TrueFalseNA.TRUE);
                    resp.setCertStatus(CertificateStatus.OK);
                }

                em.persist(localUser);
                logAction(certSip, "authCheck3", null);
            } catch (Throwable e){
                // no certificate, just return response, exception is 
                // actually really expected :)
                log.info("Certificate was not valid: ", e);
                resp.setCertValid(TrueFalseNA.FALSE);
                resp.setCertStatus(CertificateStatus.INVALID);
                logAction(sip, "authCheck3.crtfail", null);
                return resp;
            }
            
            // Unregister if auth is OK?
            if (request.getUnregisterIfOK() == TrueFalse.TRUE && passwdChange!=true){
                log.info("Unregistering user, auth was OK so far");
                
                /*ServerMICommand cmd = new ServerMICommand("ul_rm");
                cmd.addParameter("location").addParameter(sip);
                cmd.setPriority(1);
                executor.addToHiPriorityQueue(cmd);*/
            }

            resp.setAuxJSON(jsonAuxObj.toString());

         } catch(Throwable e){
             log.warn("Exception in password change procedure", e);
             throw new RuntimeException(e);
         }

        resp.setErrCode(0);
        return resp;
    }
    
    /**
     * Obtain basic information about current account.
     * 
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "accountInfoV1Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public AccountInfoV1Response accountInfoV1(@RequestPayload AccountInfoV1Request request, MessageContext context) throws CertificateException {
        String owner = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (accountInfoV1) : " + owner);
        
        AccountInfoV1Response resp = new AccountInfoV1Response();
        resp.setForcePasswordChange(TrueFalse.FALSE);
        resp.setAccountDisabled(true);
        resp.setAuxVersion(0);
        resp.setAuxJSON("");
        resp.setErrCode(404);

        final JSONObject jsonAuxObj = new JSONObject();
        try {
            // Integer version by default set to 3. 
            // Changes window of the login validity.
            int reqVersion = 3;
            Integer reqVersionInt = request.getVersion();
            if (reqVersionInt != null){
                reqVersion = reqVersionInt.intValue();
            }

            final String auxJsonReq = request.getAuxJSON();
            
            // Date conversion can throw exception
            resp.setAccountExpires(getXMLDate(new Date()));
            resp.setServerTime(getXMLDate(new Date()));
            resp.setAccountIssued(getXMLDate(new Date()));
            
            // user is provided in request thus try to load it from database
            String sip = request.getTargetUser();
            log.info(String.format("User [%s] is asking for details, reqVer: %d", sip, reqVersion));
            if (!StringUtils.isEmpty(sip) && !owner.equalsIgnoreCase(sip)){
                log.error(String.format("Requesting a different user than in certificate. cert=%s, req=%s", owner, sip));
                resp.setErrCode(-10);
                return resp;
            }
            sip = owner;

            // user matches, load subscriber data for him
            Subscriber localUser = this.dataService.getLocalUser(sip);
            if (localUser == null){
                log.warn("Local user was not found in database for: " + sip);
                return resp;
            }
            
            // User valid ?
            resp.setAccountDisabled(localUser.isDeleted());
            
            // Time can be null, account expire time.
            Calendar cal = localUser.getExpires();
            resp.setAccountExpires(cal == null ? null : getXMLDate(cal.getTime()));
            
            // Account issued time.
            Calendar calIssued = localUser.getIssued();
            resp.setAccountIssued(calIssued == null ? null : getXMLDate(calIssued.getTime()));
            
            Calendar firstUserAddTime = localUser.getDateFirstUserAdded();
            resp.setFirstUserAddDate(firstUserAddTime == null ? null : getXMLDate(firstUserAddTime.getTime()));
            
            Calendar firstLogin = localUser.getDateFirstLogin();
            resp.setFirstLoginDate(firstLogin == null ? null : getXMLDate(firstLogin.getTime()));
            
            Calendar lastActivity = localUser.getDateLastActivity();
            resp.setAccountLastActivity(lastActivity == null ? null : getXMLDate(lastActivity.getTime()));
            
            Calendar lastPasswdChange = localUser.getDateLastPasswordChange();
            resp.setAccountLastPassChange(lastPasswdChange == null ? null : getXMLDate(lastPasswdChange.getTime()));
            
            Calendar firstAuthCheck = localUser.getDateFirstAuthCheck();
            resp.setFirstAuthCheckDate(firstAuthCheck == null ? null : getXMLDate(firstAuthCheck.getTime()));
            
            Calendar lastAuthCheck = localUser.getDateLastAuthCheck();
            resp.setLastAuthCheckDate(lastAuthCheck == null ? null : getXMLDate(lastAuthCheck.getTime()));
            
            // License type.
            String licType = localUser.getLicenseType();
            resp.setLicenseType(licType == null ? "-1" : licType);
            
            // Number of stored files for given user.
            resp.setStoredFilesNum(Integer.valueOf((int)fmanager.getStoredFilesNum(localUser)));
       
            // If user was deleted, login was not successful.
            if (localUser.isDeleted()){
                resp.setErrCode(405);
                return resp;
            }
            
            // Force password change?
            Boolean passwdChange = localUser.getForcePasswordChange();
            if (passwdChange!=null && passwdChange){
                resp.setForcePasswordChange(TrueFalse.TRUE);
            }

            // Type of the testing account. Alpha / beta.
            if (!StringUtils.isEmpty(localUser.getTestingSettings())){
                try {
                    final String testingSettingsStr = localUser.getTestingSettings();
                    final JSONObject testingSettings = new JSONObject(testingSettingsStr);

                    jsonAuxObj.put("testingSettings", testingSettings);
                    resp.setAuxJSON(jsonAuxObj.toString());
                } catch(Exception e){
                    log.error("Testing settings could not be parsed", e);
                }
            }

            // AuxData
            if (!StringUtils.isEmpty(localUser.getAuxData())){
                try {
                    final String auxDataStr = localUser.getAuxData();
                    final JSONObject auxData = new JSONObject(auxDataStr);

                    jsonAuxObj.put("auxData", auxData);
                    resp.setAuxJSON(jsonAuxObj.toString());
                } catch(Exception e){
                    log.error("Aux data could not be parsed", e);
                }
            }

            // AUXJson - trial event logs.
            if (localUser.getExpires() != null && localUser.getExpires().before(Calendar.getInstance())){
                final List<TrialEventLog> logs = dataService.getTrialEventLogs(localUser, null);
                final JSONObject jsonObj = dataService.eventLogToJson(logs, localUser);
                jsonAuxObj.put("evtlog", jsonObj);
            }

            // Update app_version?
            if (!StringUtils.isEmpty(auxJsonReq)){
                try {
                    JSONObject auxReq = new JSONObject(auxJsonReq);
                    if (auxReq.has("app_version")){
                        final JSONObject appVersion = auxReq.getJSONObject("app_version");
                        final String appVersionString = appVersion.toString();
                        localUser.setAppVersion(appVersionString);
                    }
                } catch(Exception e){
                    log.warn("Could not parse auxJson, app_version", e);
                }
            }

            logAction(sip, "accountInfoV1", null);
            localUser.setDateLastActivity(Calendar.getInstance());
            localUser.setLastActionIp(auth.getIp(this.request));
            em.persist(localUser);

            // Support contacts.
            dataService.setSupportContacts(localUser, jsonAuxObj);
            resp.setAuxJSON(jsonAuxObj.toString());

        } catch(Exception e){
            log.warn("Exception in password change procedure", e);
            throw new RuntimeException(e);
        }

        resp.setErrCode(0);
        return resp;
    }
    
    /**
     * Signing certificates in Certificate Signing Request that come from remote 
     * party - mobile devices.
     * 
     * Be very careful during this method, it is used ONLY when new user is added
     * to the system. Audit everything and be very strict with signing something.
     * 
     * Restrictions:
     *  1. user must be in database -> local user
     *  2. user has to have enabled flag allowing signing new certificate
     *      this flag is immediately reseted after signing. Only manual intervention
     *      can revert value of the flag to enable signing again.
     *  3. user has to provide AUTH token to prove identity
     *      sha1(challenge, time_block, user, hashed_password_to_sip_server) 
     *  4. connection has to use SSL to avoid MITM.
     *
     * @param req1
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "signCertificateRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public SignCertificateResponse signCertificate(@RequestPayload SignCertificateRequest req1, MessageContext context) throws CertificateException {
        // Construct v2 request.
        SignCertificateV2Request req2 = new SignCertificateV2Request();
        req2.setAuthHash(req1.getAuthHash());
        req2.setCSR(req1.getCSR());
        req2.setServerToken(req1.getServerToken());
        req2.setUser(req1.getUser());
        req2.setUsrToken(req1.getUsrToken());
        req2.setVersion(1);
        
        // Do the job.
        SignCertificateV2Response resp2 = this.signCertificateV2(req2, context);
        
        // Convert back to version 1.
        SignCertificateResponse resp1 = new SignCertificateResponse();
        resp1.setCertificate(resp2.getCertificate());
        return resp1;
    }
    
    /**
     * Signing certificates in Certificate Signing Request that come from remote 
     * party - mobile devices.
     * 
     * Be very careful during this method, it is used ONLY when new user is added
     * to the system. Audit everything and be very strict with signing something.
     * 
     * Restrictions:
     *  1. user must be in database -> local user
     *  2. user has to have enabled flag allowing signing new certificate
     *      this flag is immediately reseted after signing. Only manual intervention
     *      can revert value of the flag to enable signing again.
     *  3. user has to provide AUTH token to prove identity
     *      sha1(challenge, time_block, user, hashed_password_to_sip_server) 
     *  4. connection has to use SSL to avoid MITM.
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "signCertificateV2Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public SignCertificateV2Response signCertificateV2(@RequestPayload SignCertificateV2Request request, MessageContext context) throws CertificateException {
        // protect user identity and avoid MITM, require SSL
        this.checkOneSideSSL(context, this.request);
        
        // construct response wrapper
        SignCertificateV2Response response = new SignCertificateV2Response();
        CertificateWrapper certificate = new CertificateWrapper();
        certificate.setStatus(CertificateStatus.INVALID);
        response.setCertificate(certificate);
        
        // signer init
        this.signer.initCA();
        
        // obtain signing request
        byte[] csr = request.getCSR();
        if (csr==null || csr.length==0){
            log.warn("CSR is null/empty");
            throw new IllegalArgumentException("Emty CSR");
        }
        
        try {
            // request data here - username from signCertificate request
            String reqUser = request.getUser();
            log.info("User [" + reqUser + "] is asking for signing a certificate");
            
            // user matches, load subscriber data for him
            Subscriber localUser = this.dataService.getLocalUser(reqUser);
            if (localUser == null){
                log.warn("Local user was not found in database for: " + reqUser);
                throw new IllegalArgumentException("Not authorized");
            }
            if (localUser.isDeleted()){
                log.warn("Local user was deleted/disabled. SIP: " + reqUser);
                throw new IllegalArgumentException("Not authorized");
            }
            
            // Integer version by default set to 3. 
            // Changes window of the login validity.
            int reqVersion = 2;
            Integer reqVersionInt = request.getVersion();
            if (reqVersionInt != null){
                reqVersion = reqVersionInt.intValue();
            }
            
            // Adjust millisecond window size for auth hash validity.
            // This was increased in v3 so users are not bullied for not 
            // minute-precise time setup.
            long milliSecondWindowSize = 1000L * 60L;
            if (reqVersion >= 2){
                milliSecondWindowSize = 1000L * 60L * 10L;
            } 
            
            // check token here!
            boolean ott_valid = this.dataService.isOneTimeTokenValid(reqUser, request.getUsrToken(), request.getServerToken(), "");
            if (ott_valid==false){
                log.warn("Invalid one time token");
                throw new RuntimeException("Not authorized");
            }
            
            // check user AUTH hash
            // generate 3 tokens, for 3 time slots
            String userHashes[] = new String[3];
            // store correct encryption key
            String encKeys[] = new String[3];
            for (int i=-1, c=0; i <=1 ; i++, c++){
                userHashes[c] = this.dataService.generateUserAuthToken(
                    reqUser, localUser.getHa1(), 
                    request.getUsrToken(), request.getServerToken(), 
                    milliSecondWindowSize, i);
                encKeys[c] = this.dataService.generateUserEncToken(reqUser, localUser.getHa1(), 
                    request.getUsrToken(), request.getServerToken(), 
                    milliSecondWindowSize, i);
            }
            
            // verify one of auth hashes
            boolean authHash_valid=false;
            String encKey = "";
            for (int c=0; c<=2; c++){
                String curAuthHash = userHashes[c];
                log.info("Verify auth hash["+request.getAuthHash()+"] vs. genhash["+curAuthHash+"]");
                if (curAuthHash.equals(request.getAuthHash())){
                    encKey = encKeys[c];
                    authHash_valid=true;
                    break;
                }
            }
            
            if (authHash_valid==false){
                log.warn("Invalid auth hash");
                throw new RuntimeException("Not authorized");
            }
            
            // check if user is allowed to sign certificate - subscriber table contains flag
            // telling that user is new and can sign new certificate
            if (CLIENT_DEBUG==false && localUser.isCanSignNewCert()==false){
                log.warn("User cannot sign certificates");
                throw new IllegalArgumentException("Not authorized");
            }
            
            // decrypt CSR - important step, prevents attacker to sign his certificate
            csr = AESCipher.decrypt2(csr, encKey.toCharArray());
            
            // extract CSR from request - decrypt 
            log.info("Going to extract request");
            PKCS10CertificationRequest csrr = this.signer.getReuest(csr, false);
            log.debug("Request extracted: " + csrr);
            log.debug("Request extracted: " + csrr.getSubject().toString());
            
            // check request validity - CN format, match to username in request
            String certCN = this.auth.getCNfromX500Name(csrr.getSubject());
            log.info("CN from certificate: " + certCN);
            if (certCN==null || certCN.isEmpty()){
                throw new IllegalArgumentException("CN in certificate is null");
            } else if (certCN.equals(reqUser)==false){
                throw new IllegalArgumentException("CN in certificate does not match user in request");
            }
            
            // check if user already has valid certificate (not revoked, date valid)
            CAcertsSigned userCert = null;
            
            try {
                userCert = this.dataService.getCertificateForUser(localUser);
            } catch(Exception ex){
                // no entity found and another exceptions - not interested in
                log.debug("No entity returned when asking for CAsigned DB certificate", ex);
            }
            
            if (userCert!=null){
                // check if current certificate is about to expire
                // in interval 14 days. If not, user is not allowed to sign new
                // certificates.
                log.info("User has some valid certificate in DB: " + userCert);
                
                Date expireLimit = new Date(System.currentTimeMillis() + 14L * 24L * 60L * 60L * 1000L);
                if (CLIENT_DEBUG==false && userCert.getNotValidAfter().after(expireLimit)){
                    log.warn("User wants to create a new certificate even though "
                            + "he has on valid certificate, with more than 14 days validity");
                    throw new IllegalArgumentException("Your certificate is valid enough, wait for expiration.");
                }
                
                // check if this certificate has same public key as proposed in request.
                // if yes, then ask for new certificate
                X509Certificate dbCert = userCert.getCert();
                if (Arrays.equals(
                        csrr.getSubjectPublicKeyInfo().getPublicKeyData().getEncoded(), 
                        dbCert.getPublicKey().getEncoded())){
                    log.warn("User wants to sign certificate with same public key as previous valid certificate");
                    throw new IllegalArgumentException("Your certificate is not secure - PK is same as previous");
                }
                
                // if here, revoke existing certificate
                log.info("Revoking previous user certificate");
                userCert.setIsRevoked(Boolean.TRUE);
                userCert.setDateRevoked(new Date());
                userCert.setRevokedReason("New certificate");
                em.persist(userCert);
            }
            // insert new certificate record to table and use its serial, generated
            // by low level engine for signing. This should be run in transaction
            // thus if something will fail during signing, transaction with
            // revocation.
            Date notBefore = new Date(System.currentTimeMillis());
            Date notAfter  = new Date(System.currentTimeMillis() + (2L * 365L * 24L * 60L * 60L * 1000L));
            CAcertsSigned cacertsSigned = new CAcertsSigned();
            cacertsSigned.setCN(certCN);
            cacertsSigned.setIsRevoked(false);
            cacertsSigned.setDN(csrr.getSubject().toString());
            cacertsSigned.setDateSigned(new Date());
            cacertsSigned.setSubscriber(localUser);
            cacertsSigned.setSubscriberName(PhoenixDataService.getSIP(localUser));
            cacertsSigned.setRawCert(new byte[0]);
            cacertsSigned.setCertHash("");
            cacertsSigned.setNotValidBefore(notBefore);
            cacertsSigned.setNotValidAfter(notAfter);
            em.persist(cacertsSigned);
            
            // here should be generated new serial
            Query query = em.createNativeQuery("SELECT LAST_INSERT_ID()");
            Long newSerial = ((BigInteger) query.getSingleResult()).longValue();

            // prepare certificate basic attributes - serial number - unique
            BigInteger serial = new BigInteger(newSerial.toString());
            
            // sign certificate by server CA, setting DN from CSR, issuer from CA data,
            // adding appropriate X509v3 extensions - CA:false
            X509Certificate sign = this.signer.sign(csrr, serial, notBefore, notAfter);
            log.debug("Certificate signed: " + sign);
            
            // update cacert in CA database - missing certificate values (binary, digest)
            final String crtDigest = this.signer.getCertificateDigest(sign);
            cacertsSigned.setRawCert(sign.getEncoded());
            cacertsSigned.setCertHash(crtDigest);
            em.persist(cacertsSigned);
            
            response.getCertificate().setStatus(CertificateStatus.OK);
            response.getCertificate().setCertificate(sign.getEncoded());
            response.getCertificate().setUser(reqUser);

            // If user does not have first login date filled in, add this one.
            Calendar fLogin = localUser.getDateFirstLogin();
            if (fLogin == null || fLogin.before(get1971())){
                localUser.setDateFirstLogin(Calendar.getInstance());
                log.info(String.format("First login set to: %s", localUser.getDateFirstLogin().getTime()));
                em.persist(localUser);
            }

            // flush transaction
            log.info(String.format("New certificate signed for user %s, certSerial: %s", reqUser, serial));
            em.flush();

            // New login was successful, broadcast push notification informing the signed user about new device certificate.
            try {
                amqpListener.pushNewCertificate(reqUser, sign.getNotBefore().getTime(), crtDigest.substring(0, 10));
            } catch(Exception ex){
                log.error("Error in pushing new cert event", ex);
            }

            // Broadcast push notification for all contact who have this one in its contact list.
            try {
                dataService.notifyNewCertificateToRoster(localUser, sign.getNotBefore().getTime(), crtDigest);
            } catch(Exception ex){
                log.error("Error in pushing contact cert update event", ex);
            }

            logAction(reqUser, "signCert", null);
        } catch (InvalidKeyException ex) {
            log.warn("Problem with signing - invalid key", ex);
            
            throw new CertificateException(ex);
        } catch (NoSuchAlgorithmException ex) {
            log.warn("Problem with signing - no such alg", ex);
            
            throw new CertificateException(ex);
        } catch (NoSuchProviderException ex) {
            log.warn("Problem with signing - no such provider", ex);
            
            throw new CertificateException(ex);
        } catch (SignatureException ex) {
            log.warn("Problem with signing - signature problem", ex);
            
            throw new CertificateException(ex);
        } catch (OperatorCreationException ex) {
            log.warn("Problem with signing - operator problem", ex);
            
            throw new CertificateException(ex);
        } catch (IOException ex) {
            log.warn("Problem with signing - IO exception", ex);
            
            throw new CertificateException(ex);
        } catch (Throwable ex){
            log.warn("General exception during signing", ex);
            
            throw new CertificateException(ex);
        }
        
        return response;
    }
    
    /**
     * Obtains one time token. 
     * Required for some special further actions.
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     * @throws java.security.NoSuchAlgorithmException 
     */
    @PayloadRoot(localPart = "getOneTimeTokenRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public GetOneTimeTokenResponse getOneTimeToken(@RequestPayload GetOneTimeTokenRequest request, MessageContext context) throws CertificateException, NoSuchAlgorithmException {
        // To protect this channel from eavesdropers and to protect user identity
        // we are using another SSL channel - without client certificate required.
        //
        this.checkOneSideSSL(context, this.request);
        
        // generate new one time token - 2 minutes validity
        String ott = this.dataService.generateOneTimeToken(request.getUser(), request.getUserToken(), Long.valueOf(1000L * 60L * 2L), "");
        
        GetOneTimeTokenResponse response = new GetOneTimeTokenResponse();
        response.setUser(request.getUser());
        response.setUserToken(request.getUserToken());
        response.setServerToken(ott);
        return response;
    }
    
    /**
     * Logic for adding new Diffie-Hellman offline keys to the server.
     * 
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "ftAddDHKeysRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public FtAddDHKeysResponse ftAddDHKeys(@RequestPayload FtAddDHKeysRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (ftAddDHKeys): " + ownerSip);
        
        // construct response, then add results iteratively
        FtAddDHKeysResponse response = new FtAddDHKeysResponse();
        response.setResult(new FtAddDHKeysReturnList());
        
        // analyze request
        List<FtDHKey> dhkeys = request.getDhkeys();
        if (dhkeys==null || dhkeys.isEmpty()){
            log.info("dhkeysr list is empty");
            response.setErrCode(-1);
            return response;
        }
        
        // Iterating over the request.
        log.info("ftAddDHKeys; size: " + dhkeys.size() + "; requestor: " + ownerSip);
        int errCode=-1;
        
        try {
            for(FtDHKey key : dhkeys){
                Integer result=-1;
                
                try {
                    DHKeys entity = new DHKeys();

                    entity.setExpired(false);
                    entity.setUsed(false);

                    entity.setCreated(new Date());
                    entity.setExpires(getDate(key.getExpires()));
                    entity.setForUser(key.getUser());
                    entity.setNonce1(key.getNonce1());
                    entity.setNonce2(key.getNonce2());
                    entity.setOwner(owner);
                    entity.setSig1(key.getSig1());
                    entity.setSig2(key.getSig2());
                    entity.setaAncBlock(key.getAEncBlock());
                    entity.setsAncBlock(key.getSEncBlock());
                    
                    this.dataService.persist(entity);
                    result=1;
                } catch(Throwable e){
                    log.warn("Exception when adding DH key to the database", e);
                } finally {
                    response.getResult().getCode().add(result);
                }
            }
            
            errCode=1;
            logAction(ownerSip, "ftAddDHKeys", null);
            
        } catch(Exception e){
            log.error("Exception in AddDHKeys", e);
            errCode=-2;
        }
        
        response.setErrCode(errCode);
        return response;
    }
    
    /**
     * Logic for removing all Diffie-Hellman offline keys on the server.
     * 
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "ftRemoveDHKeysRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public FtRemoveDHKeysResponse ftRemoveDHKeys(@RequestPayload FtRemoveDHKeysRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (ftRemoveDHKeys): " + ownerSip);
        
        // construct response, then add results iteratively
        Integer result = -1;
        FtRemoveDHKeysResponse response = new FtRemoveDHKeysResponse();
        
        try {
            // If the reuest is to delete all DHkeys, do it.
            if (request.isDeleteAll()){
                // Delete all DH keys. 
                Query delQuery = this.em.createQuery("DELETE FROM DHKeys d "
                    + " WHERE d.owner=:owner");
                delQuery.setParameter("owner", owner);
                delQuery.executeUpdate();
                result = 1;
                
                response.setErrCode(result);
                return response;
            }
            
            // Request to delete keys is more specific. Maybe deleting all keys
            // from particular sender.
            SipList sipList = request.getUsers();
            if (sipList!=null && sipList.getUser()!=null && sipList.getUser().isEmpty()==false){
                final List<String> usrList = sipList.getUser();
                for(String usr : usrList){
                    // One query to delete per user. 
                    // At the moment it is directly here, if it gets more complicated
                    // or functionality will be repeated, move it to the separate manager.
                    Query delQuery = this.em.createQuery("DELETE FROM DHKeys d "
                        + " WHERE d.owner=:owner AND forUser=:foruser");
                    delQuery.setParameter("owner", owner);
                    delQuery.setParameter("foruser", usr);
                    delQuery.executeUpdate();
                }
                result = 1;
            }
            
            // Maybe request to delete all files with given nonce?
            FtNonceList nonceList = request.getNonceList();
            if (nonceList!=null && nonceList.getNonce()!=null && nonceList.getNonce().isEmpty()==false){
                final List<String> nonce = nonceList.getNonce();
                for(String curNonce : nonce){
                    // One query to delete per nonce.
                    // Same as foruser field, at the moment code is here.
                    // If more complicated or replicated -> move to separate manager.
                    Query delQuery = this.em.createQuery("DELETE FROM DHKeys d "
                        + " WHERE d.owner=:owner AND nonce2=:nonce2");
                    delQuery.setParameter("owner", owner);
                    delQuery.setParameter("nonce2", curNonce);
                    delQuery.executeUpdate();
                }
                result = 1;
            }
            
            // Maybe request to delete all files older than some date?
            XMLGregorianCalendar deleteOlderThan = request.getDeleteOlderThan();
            if (deleteOlderThan != null && deleteOlderThan.isValid()){
                Date dt = getDate(deleteOlderThan);
                
                final String olderThanQueryString = "DELETE FROM DHKeys d"
                        + " WHERE d.owner=:o AND (d.created < :dc OR d.expires < :de)";
                Query delQuery = em.createQuery(olderThanQueryString);
                delQuery.setParameter("o", owner)
                        .setParameter("dc", dt)
                        .setParameter("de", dt);
                delQuery.executeUpdate();
                result = 1;
            }
            
            // If there is list of pairs (sip, remove older than) than process it.
            // It allows to remove older keys than corresponding certificates are.
            SipDatePairList userDateList = request.getUserDateList();
            if (userDateList!=null && userDateList.getSipdate()!=null && userDateList.getSipdate().isEmpty()==false){
                List<SipDatePair> sipdate = userDateList.getSipdate();
                for(SipDatePair p : sipdate){
                    Date dt = getDate(p.getDt());
                    
                    // TODO: optimize this by bulk remove.
                    final String olderThanQueryString = "DELETE FROM DHKeys d"
                        + " WHERE d.owner=:o AND (forUser=:foruser AND d.created <= :dt)";
                    Query delQuery = em.createQuery(olderThanQueryString);
                    delQuery.setParameter("o", owner)
                            .setParameter("foruser", p.getSip())
                            .setParameter("dt", dt);
                    delQuery.executeUpdate();
                }
                
                result = 1;
            }

            logAction(ownerSip, "ftRemoveDHKeys", null);
            response.setErrCode(result);
            return response;
            
        } catch(Exception e){
            response.setErrCode(result);
            log.error("Exception in deleting all DH keys", e);
        }
        
        return response;
    }
    
    /**
     * Logic for obtaining information about stored DH keys.
     * 
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "ftGetStoredDHKeysInfoRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public FtGetStoredDHKeysInfoResponse ftGetStoredDHKeysInfo(@RequestPayload FtGetStoredDHKeysInfoRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (ftGetStoredDHKeysInfo): " + ownerSip);
        
        // construct response, then add results iteratively
        FtGetStoredDHKeysInfoResponse response = new FtGetStoredDHKeysInfoResponse();
        int result = -1;

        try {
            if (request.isDetailed()){
                // Want detailed DH keys information, for all key separately.
                FtDHKeyUserInfoArr infoArr = new FtDHKeyUserInfoArr();
                List<FtDHKeyUserInfo> dhkeys = infoArr.getKeyinfo();
                
                // Query to fetch only neccessary information from DB.
                String queryStats = "SELECT NEW com.phoenix.db.DHKeys(dh.id, dh.owner, dh.forUser, dh.nonce2, "
                        + " dh.created, dh.expires, dh.used, dh.uploaded) "
                        + " FROM DHKeys dh "
                        + " WHERE dh.owner=:s ORDER BY dh.forUser";
                TypedQuery<DHKeys> query = em.createQuery(queryStats, DHKeys.class);
                query.setParameter("s", owner);
                
                List<DHKeys> resultList = query.getResultList();
                for(DHKeys keyInfo : resultList){
                    FtDHKeyUserInfo i = new FtDHKeyUserInfo();
                    i.setUser(keyInfo.getForUser());
                    i.setNonce2(keyInfo.getNonce2());
                    i.setExpires(getXMLDate(keyInfo.getExpires()));
                    i.setCreated(getXMLDate(keyInfo.getCreated()));
                    
                    // TODO: implement
                    i.setCreatorCertInfo("");
                    i.setUserCertInfo("");
                    if (keyInfo.isUploaded()){
                        i.setStatus(FtDHkeyState.UPLOADED);
                    } else if (keyInfo.isUsed()){
                        i.setStatus(FtDHkeyState.USED);
                    } else if (keyInfo.getExpires().before(new Date())){
                        i.setStatus(FtDHkeyState.EXPIRED);
                    } else {
                        i.setStatus(FtDHkeyState.READY);
                    }
                    
                    dhkeys.add(i);
                }
                
                response.setInfo(infoArr);
                result = 1;
                
            } else {
                // Want only statistical information about keys for each user.
                class tmpStats {
                    public int ready=0;
                    public int used=0;
                    public int expired=0;
                    public int uploaded=0;
                }
                
                FtDHKeyUserStatsArr statsArr = new FtDHKeyUserStatsArr();
                List<FtDHKeyUserStats> dhkeys = statsArr.getKeystats();
                Map<String, tmpStats> stats = new HashMap<String, tmpStats>(); // Mapping user -> key statistics
                
                // Query to fetch only neccessary information from DB.
                String queryStats = "SELECT NEW com.phoenix.db.DHKeys(dh.id, dh.owner, dh.forUser, dh.expires, dh.used, dh.uploaded) FROM DHKeys dh "
                        + " WHERE dh.owner=:s";
                TypedQuery<DHKeys> query = em.createQuery(queryStats, DHKeys.class);
                query.setParameter("s", owner);
                
                List<DHKeys> resultList = query.getResultList();
                for(DHKeys keyInfo : resultList){
                    // Ensure user entry exists in the hash map
                    final String user = keyInfo.getForUser();
                    if (!stats.containsKey(user)){
                        stats.put(user, new tmpStats());
                    }
                    
                    tmpStats s = stats.get(user);
                    if (keyInfo.isUploaded()){
                        s.uploaded += 1;
                    } else if (keyInfo.isUsed()){
                        s.used += 1;
                    } else if (keyInfo.getExpires().before(new Date())){
                        s.expired += 1;
                    } else {
                        s.ready += 1;
                    }
                    
                    stats.put(user, s);
                }
                
                // Harvest processed entries in HashMap and produce response.
                for(Entry<String, tmpStats> e : stats.entrySet()){
                    FtDHKeyUserStats s = new FtDHKeyUserStats();
                    s.setUser(e.getKey());
                    s.setUsedCount(e.getValue().used);
                    s.setExpiredCount(e.getValue().expired);
                    s.setReadyCount(e.getValue().ready);
                    
                    dhkeys.add(s);
                }
                
                response.setStats(statsArr);
            }

            logAction(ownerSip, "ftGetStoredDHKeysInfo", null);
            result = 1;
        } catch(Exception e){
            log.error("Exception in obtaining DH keys info", e);
        } finally {
            response.setErrCode(result);
        }
        
        return response;
    }
    
    /**
     * First message of getDH key protocol.
     * 
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "ftGetDHKeyRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public FtGetDHKeyResponse ftGetDHKey(@RequestPayload FtGetDHKeyRequest request, MessageContext context) throws CertificateException {
        String caller = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (ftGetDHKeyRequest): " + caller);
        
        // construct response, then add results iteratively
        FtGetDHKeyResponse response = new FtGetDHKeyResponse();
        response.setErrCode(-1);

        try {
            // Has to obtain targer local user at first - needed for later query,
            // verification the user exists and so on.
            Subscriber owner = this.dataService.getLocalUser(request.getUser());
            if (owner==null){
                log.debug("Unable to find target user '" + request.getUser() + "'");
                return response;
            }
            
            // Query to fetch DH key from database
            String queryStats = "SELECT dh FROM DHKeys dh "
                    + " WHERE dh.owner=:s AND dh.forUser=:c AND dh.used=:u AND dh.expired=:e AND dh.uploaded=:up AND dh.expires>:n"
                    + " ORDER BY dh.expires ASC";
            TypedQuery<DHKeys> query = em.createQuery(queryStats, DHKeys.class);
            query.setParameter("s", owner)
                    .setParameter("c", caller)
                    .setParameter("u", Boolean.FALSE)
                    .setParameter("up", Boolean.FALSE)
                    .setParameter("e", Boolean.FALSE)
                    .setParameter("n", new Date())
                    .setMaxResults(1);
            
            List<DHKeys> resultList = query.getResultList();
            if (resultList==null || resultList.isEmpty()){
                response.setErrCode(-2);
                return response;
            }
            
            final DHKeys e = resultList.get(0);
            response.setCreated(getXMLDate(e.getCreated()));
            response.setExpires(getXMLDate(e.getExpires()));
            response.setUser(request.getUser());
            response.setAEncBlock(e.getaAncBlock());
            response.setSEncBlock(e.getsAncBlock());
            response.setSig1(e.getSig1());
            response.setErrCode(1);

            logAction(caller, "ftGetDHKey", null);
            return response;
        } catch(Exception e){
            log.error("Exception in obtaining DH keys info", e);
        }
        
        return response;
    }
    
    /**
     * Second message of getDH key protocol
     * 
     * @param request
     * @param context
     * @return 
     * @throws java.security.cert.CertificateException 
     */
    @PayloadRoot(localPart = "ftGetDHKeyPart2Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public FtGetDHKeyPart2Response ftGetDHKeyPart2(@RequestPayload FtGetDHKeyPart2Request request, MessageContext context) throws CertificateException {
        String caller = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (ftGetDHKeyPart2): " + caller);
        
        // construct response, then add results iteratively
        FtGetDHKeyPart2Response response = new FtGetDHKeyPart2Response();
        response.setErrCode(-1);
        
        try {
            // Has to obtain targer local user at first - needed for later query,
            // verification the user exists and so on.
            Subscriber owner = this.dataService.getLocalUser(request.getUser());
            if (owner==null){
                log.debug("Unable to find target user '" + request.getUser() + "'");
                return response;
            }
            
            // Query to fetch DH key from database
            String queryStats = "SELECT dh FROM DHKeys dh "
                    + " WHERE dh.owner=:s AND dh.forUser=:c AND dh.nonce1=:h AND dh.used=:u AND dh.expired=:e AND dh.expires>:n"
                    + " ORDER BY dh.expires ASC";
            TypedQuery<DHKeys> query = em.createQuery(queryStats, DHKeys.class);
            query.setParameter("s", owner)
                    .setParameter("c", caller)
                    .setParameter("h", request.getNonce1())
                    .setParameter("u", Boolean.FALSE)
                    .setParameter("e", Boolean.FALSE)
                    .setParameter("n", new Date())
                    .setMaxResults(1);
            
            DHKeys e = query.getSingleResult();
            if (e==null){
                response.setErrCode(-2);
                return response;
            }
            
            response.setNonce2(e.getNonce2());
            response.setSig2(e.getSig2());
            response.setUser(request.getUser());
            response.setErrCode(1);
            
            // Update DH key in database, mark as used and store when was this 
            // key marked as used.
            e.setUsed(Boolean.TRUE);
            e.setWhenUsed(new Date());
            this.dataService.persist(e, true);

            // Broadcast push notification about new used key.
            try {
                amqpListener.pushDHKeyUsed(PhoenixDataService.getSIP(owner));
            } catch(Exception ex){
                log.error("Error in pushing dh key used event", ex);
            }

            logAction(caller, "ftGetDHKeyPart2", null);
            return response;
        } catch(Exception e){
            log.error("Exception in obtaining DH keys info", e);
        }
        
        return response;
    }
    
    /**
     * Request from the user to delete his uploaded files.
     * 
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "ftDeleteFilesRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public FtDeleteFilesResponse ftDeleteFiles(@RequestPayload FtDeleteFilesRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (ftDeleteFiles): " + ownerSip);
        
        // construct response, then add results iteratively
        FtDeleteFilesResponse response = new FtDeleteFilesResponse();
        response.setErrCode(0);
        
        try {
            // If the reuest is to delete all files, do it.
            if (request.isDeleteAll()){
                List<StoredFiles> sfList = fmanager.getStoredFiles(owner);
                if (sfList == null || sfList.isEmpty()){
                    response.setErrCode(0);
                    return response;
                }
                
                fmanager.deleteFilesList(sfList);
                
                // Get all new nonces - notify user about new files
                List<String> nc = fmanager.getStoredFilesNonces(owner);
                pmanager.notifyNewFiles(ownerSip, nc);
                
                response.setErrCode(0);
                return response;
            }
            
            // Request to delete files is more specific. Maybe deleting all files
            // from particular sender.
            SipList sipList = request.getUsers();
            if (sipList!=null && sipList.getUser()!=null && sipList.getUser().isEmpty()==false){
                final List<String> usrList = sipList.getUser();
                for(String usr : usrList){
                    List<StoredFiles> sfList = fmanager.getStoredFilesFromUser(owner, usr);
                    if (sfList == null || sfList.isEmpty()){
                        continue;
                    }
                    
                    fmanager.deleteFilesList(sfList);
                }
            }
            
            // Maybe request to delete all files with given nonce?
            FtNonceList nonceList = request.getNonceList();
            if (nonceList!=null && nonceList.getNonce()!=null && nonceList.getNonce().isEmpty()==false){
                final List<String> nonce = nonceList.getNonce();
                for(String curNonce : nonce){
                    // Obtain file reference to a given nonce, delete only if
                    // user has privileges for this file.
                    StoredFiles sf = fmanager.getStoredFile(owner, curNonce);
                    if (sf!=null){
                        fmanager.deleteFiles(sf);
                    }
                }
            }
            
            // Maybe request to delete all files older than some date?
            XMLGregorianCalendar deleteOlderThan = request.getDeleteOlderThan();
            if (deleteOlderThan != null && deleteOlderThan.isValid()){
                Date dt = getDate(deleteOlderThan);
                
                final String olderThanQueryString = "SELECT sf FROM StoredFiles sf"
                        + " WHERE sf.owner=:o AND (sf.created < :dc OR sf.expires < :de)";
                TypedQuery<StoredFiles> sfQuery = em.createQuery(olderThanQueryString, StoredFiles.class);
                sfQuery.setParameter("o", owner)
                        .setParameter("dc", dt)
                        .setParameter("de", dt);
                List<StoredFiles> sfList = sfQuery.getResultList();
                if (sfList!=null && sfList.isEmpty()==false){
                    fmanager.deleteFilesList(sfList);
                }
            }
            
            // Get all new nonces - notify user about new files
            List<String> nc = fmanager.getStoredFilesNonces(owner);
            pmanager.notifyNewFiles(ownerSip, nc);

            logAction(ownerSip, "ftDeleteFiles", null);
            response.setErrCode(0);
            return response;
            
        } catch(Exception e){
            response.setErrCode(-1);
            log.error("Exception deleting files", e);
        }
        
        return response;
    }
    
    /**
     * Request from the user to delete his uploaded files.
     * 
     * @param request
     * @param context
     * @return
     * @throws CertificateException 
     */
    @PayloadRoot(localPart = "ftGetStoredFilesRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public FtGetStoredFilesResponse ftGetStoredFiles(@RequestPayload FtGetStoredFilesRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (ftGetStoredFiles): " + ownerSip);
        
        // Construct response
        FtGetStoredFilesResponse response = new FtGetStoredFilesResponse();
        response.setErrCode(0);
        
        try {
            final Map<String, StoredFiles> sfToSend = new HashMap<String, StoredFiles>();
            
            // If the request is to obtain all stored files, do it
            if (request.isGetAll()){
                List<StoredFiles> sfList = fmanager.getStoredFiles(owner);
                if (sfList!=null && sfList.isEmpty()==false){
                    for(StoredFiles sf : sfList){
                        sfToSend.put(sf.getNonce2(), sf);
                    }
                }
            } else {
                // Maybe only files for particular user are interesting.
                // 
                SipList sipList = request.getUsers();
                if (sipList!=null && sipList.getUser()!=null && sipList.getUser().isEmpty()==false){
                    final List<String> usrList = sipList.getUser();
                    for(String usr : usrList){
                        List<StoredFiles> sfList = fmanager.getStoredFilesFromUser(owner, usr);
                        if (sfList == null || sfList.isEmpty()){
                            continue;
                        }

                        for(StoredFiles sf : sfList){
                            sfToSend.put(sf.getNonce2(), sf);
                        }
                    }
                }
                
                // Maybe request to delete all files with given nonce?
                //
                FtNonceList nonceList = request.getNonceList();
                if (nonceList!=null && nonceList.getNonce()!=null && nonceList.getNonce().isEmpty()==false){
                    final List<String> nonce = nonceList.getNonce();
                    for(String curNonce : nonce){
                        // Obtain file reference to a given nonce, delete only if
                        // user has privileges for this file.
                        StoredFiles sf = fmanager.getStoredFile(owner, curNonce);
                        if (sf!=null){
                            sfToSend.put(curNonce, sf);
                        }
                    }
                }
            }
            
            response.setStoredFile(new FtStoredFileList());
            List<FtStoredFile> storedFiles = response.getStoredFile().getFile();
            
            // Now process StoredFiles to send
            for(Entry<String, StoredFiles> e : sfToSend.entrySet()){
                final StoredFiles sf = e.getValue();
                
                FtStoredFile sf2send = new FtStoredFile();
                sf2send.setProtocolVersion(sf.getProtocolVersion());
                sf2send.setNonce2(sf.getNonce2());
                sf2send.setSender(sf.getSender());
                sf2send.setSentDate(getXMLDate(sf.getCreated()));
                sf2send.setSizeMeta(sf.getSizeMeta());
                sf2send.setSizePack(sf.getSizePack());
                sf2send.setHashMeta(sf.getHashMeta());
                sf2send.setHashPack(sf.getHashPack());
                sf2send.setKey(sf.getDhpublic());
                storedFiles.add(sf2send);
            }

            logAction(ownerSip, "ftGetStoredFiles", null);
            return response;
        } catch(Exception e){
            response.setErrCode(-1);
            log.error("Exception ftGetStoredFiles()", e);
        }
        
        return response;
    }

    /**
     * Request from the user to store this event related to the trial account.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "trialEventSaveRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public TrialEventSaveResponse trialEventSave(@RequestPayload TrialEventSaveRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (trialEventSave): " + ownerSip);

        // Construct response
        TrialEventSaveResponse response = new TrialEventSaveResponse();
        response.setErrCode(0);

        try {
            // Check for the number of events stored for this user.
            long numEvents = dataService.getCountOfTrialEvents(owner);
            if (numEvents >= 2000){
                throw new RuntimeException("Too many records for the user " + ownerSip);
            }

            TrialEventLog te = new TrialEventLog();
            te.setDateCreated(new Date());
            te.setOwner(owner);
            te.setEtype(request.getEtype());
            em.persist(te);

            logAction(ownerSip, "trialEventSave", null);
            return response;
        } catch(Exception e){
            response.setErrCode(-2);
            log.error("Exception trialEventSave()", e);
        }

        return response;
    }

    /**
     * Request from the user's trial event log.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "trialEventGetRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public TrialEventGetResponse trialEventGet(@RequestPayload TrialEventGetRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (trialEventGet): " + ownerSip);

        // Construct response
        TrialEventGetResponse response = new TrialEventGetResponse();
        response.setErrCode(0);

        try {
            final Integer etype = request.getEtype();
            final List<TrialEventLog> logs = dataService.getTrialEventLogs(owner, etype == null || etype == -1 ? null : etype);
            final JSONObject jsonObj = dataService.eventLogToJson(logs, owner);
            response.setRespJSON(jsonObj.toString());

            logAction(ownerSip, "trialEventGet", null);
            return response;
        } catch(Exception e){
            response.setErrCode(-2);
            log.error("Exception trialEventSave()", e);
        }

        return response;
    }

    /**
     * Request to fetch pairing requests from the database according to the specified criteria.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "pairingRequestFetchRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public PairingRequestFetchResponse pairingRequestFetch(@RequestPayload PairingRequestFetchRequest request, MessageContext context) throws CertificateException {
        final Subscriber caller = this.authUserFromCert(context, this.request);
        final String callerSip = PhoenixDataService.getSIP(caller);
        log.info("Remote user connected (pairingRequestFetch): " + callerSip);

        // Construct response
        final PairingRequestFetchResponse response = new PairingRequestFetchResponse();
        response.setErrCode(0);

        final PairingRequestList reqList = new PairingRequestList();
        response.setRequestList(reqList);

        try {
            //
            // Fetch record according to the search criteria.
            //
            final String reqFrom = request.getFrom();
            final Long reqTstamp = request.getTstamp();
            final boolean fetchMy = request.isFetchMyRequests();
            final StringBuilder query = new StringBuilder();
            final Map<String, Object> params = new HashMap<String, Object>();
            TypedQuery<PairingRequest> dbQuery;

            if (fetchMy) {
                // Load my pairing requests to someone.
                query.append("SELECT pr FROM pairingRequest pr WHERE pr.fromUser=:ownerName");
                params.put("ownerName", callerSip);

                if (reqTstamp != null) {
                    query.append(" AND pr.tstamp > :tstamp");
                    params.put("tstamp", new Date(reqTstamp));
                }

                dbQuery = em.createQuery(query.toString(), PairingRequest.class);
                dataService.setQueryParameters(dbQuery, params);

            } else {
                // Load pairing request for me.
                query.append("SELECT pr FROM pairingRequest pr WHERE pr.toUser=:owner");
                params.put("owner", callerSip);

                if (reqTstamp != null) {
                    query.append(" AND pr.tstamp > :tstamp");
                    params.put("tstamp", new Date(reqTstamp));
                }
                if (!StringUtils.isEmpty(reqFrom)) {
                    query.append(" AND pr.fromUser=:fromUser");
                    params.put("fromUser", reqFrom);
                }

                dbQuery = em.createQuery(query.toString(), PairingRequest.class);
                dataService.setQueryParameters(dbQuery, params);
            }

            // Process DB response.
            List<PairingRequest> resultList = dbQuery.getResultList();
            for (PairingRequest pr : resultList) {
                final PairingRequestElement elem = ConversionUtils.pairingRequestDbToElement(pr);

                // Elem post processing for security info?
                // For now, even fetching my requests to others will show resolution, resolution timestamp & so on.
                reqList.getElements().add(elem);
            }

            response.setErrCode(0);
            logAction(callerSip, "pairingRequestFetch", null);

        } catch(Exception e){
            log.error("Exception in fetching pairing requests", e);
            response.setErrCode(-2);
        }

        return response;
    }

    /**
     * Request to store a new pairing requests to the database.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "pairingRequestInsertRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public PairingRequestInsertResponse pairingRequestInsert(@RequestPayload PairingRequestInsertRequest request, MessageContext context) throws CertificateException {
        final String callerSip = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (pairingRequestInsert): " + callerSip);

        // Construct response
        final PairingRequestInsertResponse response = new PairingRequestInsertResponse();
        response.setErrCode(0);

        try {
            // Check sanity of the request.
            final String toUser = request.getTo();
            if (StringUtils.isEmpty(request.getFromResource()) || StringUtils.isEmpty(toUser)) {
                response.setErrCode(-2);
                return response;
            }

            // Get subscriber record for toUser. If it is not a local user, caller is calling the wrong server.
            final Subscriber toUserSubs = this.dataService.getLocalUser(toUser);
            if (toUserSubs == null){
                response.setErrCode(-3);
                return response;
            }

            // Insert a new request via pairing manager.
            PairingRequest newPr = new PairingRequest();
            newPr.setFromUserResource(request.getFromResource());
            newPr.setFromUserAux(StringUtils.isEmpty(request.getFromAux()) ? null : request.getFromAux());
            newPr.setRequestAux(StringUtils.isEmpty(request.getRequestAux()) ? null : request.getRequestAux());
            newPr.setRequestMessage(StringUtils.isEmpty(request.getRequestMessage()) ? null : request.getRequestMessage());
            int insertRes = pairingMgr.insertPairingRequest(toUserSubs, callerSip, true, newPr);

            response.setErrCode(insertRes);
            logAction(callerSip, "pairingRequestInsert", null);

        } catch(Exception e){
            log.error("Exception in inserting pairing request", e);
            response.setErrCode(-1);
        }

        return response;
    }

    /**
     * Request to store a new pairing requests to the database.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "pairingRequestUpdateRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public PairingRequestUpdateResponse pairingRequestUpdate(@RequestPayload PairingRequestUpdateRequest request, MessageContext context) throws CertificateException {
        final String callerSip = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (pairingRequestUpdate): " + callerSip);

        // Construct response
        final PairingRequestUpdateResponse response = new PairingRequestUpdateResponse();
        final PairingRequestUpdateResultList resList = new PairingRequestUpdateResultList();
        response.setResultList(resList);
        response.setErrCode(0);

        try {
            if (request.getUpdateList() == null || MiscUtils.collectionIsEmpty(request.getUpdateList().getUpdates())){
                return response;
            }

            // Is caller local user?
            final Subscriber caller = this.dataService.getLocalUser(callerSip);
            final boolean isCallerLocal = caller != null;

            final List<PairingRequestUpdateElement> updates = request.getUpdateList().getUpdates();
            for(PairingRequestUpdateElement elem : updates){
                ArrayList<String> criteria = new ArrayList<String>();
                HashMap<String, Object> params = new HashMap<String, Object>();
                boolean isCallerOwner = true;

                // Security check, if both fromUser != null and owner != null, at least one of those have to equal callerSip
                // So caller cannot modify foreign pairing requests.
                if (!StringUtils.isEmpty(elem.getFromUser()) && !callerSip.equals(elem.getFromUser())
                        && !StringUtils.isEmpty(elem.getOwner()) && !callerSip.equals(elem.getOwner()))
                {
                    log.warn("pairingRequestUpdate: Security violation["+callerSip+"]: " + elem);
                    resList.getErrCodes().add(-2); // Security violation.
                    continue;
                }

                // If id is null, fromUser is null and owner is null, not asking to delete older than, criteria is not sufficient.
                if (elem.getId() == null
                        && StringUtils.isEmpty(elem.getFromUser())
                        && StringUtils.isEmpty(elem.getOwner())
                        && elem.getDeleteOlderThan() == null)
                {
                    log.warn("pairingRequestUpdate: Criteria is not sufficient["+callerSip+"]: " + elem);
                    resList.getErrCodes().add(-3); // Criteria is not sufficient.
                    continue;
                }

                // Security measure, if both to / from is null, add criteria so caller manages his own list.
                if (StringUtils.isEmpty(elem.getFromUser()) && StringUtils.isEmpty(elem.getOwner())){
                    criteria.add("pr.toUser=:toUser");
                    params.put("toUser", callerSip);

                } else if (!StringUtils.isEmpty(elem.getOwner()) && StringUtils.isEmpty(elem.getFromUser())){
                    // Deleting my request - only in case resolution is none.
                    criteria.add("pr.toUser=:toUser");
                    criteria.add("pr.fromUser=:fromUser");
                    criteria.add("(pr.resolution=:resolution1 OR pr.resolution:=resolution2)");
                    params.put("toUser", elem.getOwner());
                    params.put("fromUser", callerSip);
                    params.put("resolution1", PairingRequestResolution.NONE);
                    params.put("resolution2", PairingRequestResolution.REVERTED);
                    isCallerOwner = false;

                } else if (!StringUtils.isEmpty(elem.getFromUser())) {
                    criteria.add("pr.toUser=:toUser");
                    criteria.add("pr.fromUser=:fromUser");
                    params.put("toUser", callerSip);
                    params.put("fromUser", elem.getFromUser());
                }

                // If request for deletion by time, do it right now, no more specification is needed.
                if (elem.getDeleteOlderThan() != null){
                    criteria.add("pr.tstamp <= :tstamp");
                    params.put("tstamp", elem.getDeleteOlderThan());

                    Query delQuery = this.em.createQuery(dataService.buildQueryString("DELETE FROM pairingRequest pr WHERE ", criteria, ""));
                    dataService.setQueryParameters(delQuery, params);
                    int updRes = delQuery.executeUpdate();
                    resList.getErrCodes().add(updRes);
                    continue;
                }

                // May be identified by it's ID.
                if (elem.getId() != null){
                    criteria.add("pr.id=:id");
                    params.put("id", elem.getId());
                }

                // If deletion request, wait no more.
                if (elem.isDeleteRecord()){
                    Query delQuery = this.em.createQuery(dataService.buildQueryString("DELETE FROM pairingRequest pr WHERE ", criteria, ""));
                    dataService.setQueryParameters(delQuery, params);
                    int updRes = delQuery.executeUpdate();
                    resList.getErrCodes().add(updRes);
                    continue;
                }

                // If caller is not local, here ends his possibilities.
                if (!isCallerLocal || !isCallerOwner){
                    log.warn("pairingRequestUpdate: Operation not allowed["+callerSip+"]: " + elem);
                    resList.getErrCodes().add(-4); // Operation not allowed.
                    continue;
                }

                // If here, we want to update the record, caller is owner of such record.
                // Update is done in the following way: load entity according to criteria, make changes, persist.
                final String requestQuerySql = "SELECT pr FROM pairingRequest pr WHERE ";
                final String reqQuery = dataService.buildQueryString(requestQuerySql, criteria, " ORDER BY tstamp DESC ");
                TypedQuery<PairingRequest> requestQuery = em.createQuery(reqQuery, PairingRequest.class);
                dataService.setQueryParameters(requestQuery, params);
                requestQuery.setMaxResults(1);

                PairingRequest pr = dataService.tryGetSingleResult(requestQuery);
                if (pr == null){
                    resList.getErrCodes().add(-5); // Not found.
                    continue;
                }

                // Make changes and persist.
                if (!StringUtils.isEmpty(elem.getFromUserAux())){
                    pr.setFromUserAux(elem.getFromUserAux());
                }
                if (!StringUtils.isEmpty(elem.getRequestMessage())){
                    pr.setRequestMessage(elem.getRequestMessage());
                }
                if (!StringUtils.isEmpty(elem.getRequestAux())){
                    pr.setRequestAux(elem.getRequestAux());
                }
                if (elem.getResolution() != null){
                    pr.setResolution(ConversionUtils.getDbResolutionFromRequest(elem.getResolution()));
                    if (elem.getResolutionTstamp() == null){
                        pr.setResolutionTstamp(new Date());
                    }
                }
                if (!StringUtils.isEmpty(elem.getResolutionResource())){
                    pr.setResolutionResource(elem.getResolutionResource());
                }
                if (elem.getResolutionTstamp() != null){
                    pr.setResolutionTstamp(new Date(elem.getResolutionTstamp()));
                }
                if (!StringUtils.isEmpty(elem.getResolutionMessage())){
                    pr.setResolutionMessage(elem.getResolutionMessage());
                }
                if (!StringUtils.isEmpty(elem.getResolutionAux())){
                    pr.setResolutionAux(elem.getResolutionAux());
                }

                em.persist(pr);
                resList.getErrCodes().add(0);
            }

            // No push notification happens here. If we update our list, we know about it.
            // Deletion is not pushed.
            response.setErrCode(0);
            logAction(callerSip, "pairingRequestUpdate", null);

        } catch(Exception e) {
            log.error("Exception in updating pairing requests", e);
            response.setErrCode(-1);
        }

        return response;
    }

    /**
     * Request to fetch all groups for given user + specified by ID.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "cgroupGetRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public CgroupGetResponse cgroupFetch(@RequestPayload CgroupGetRequest request, MessageContext context) throws CertificateException {
        final Subscriber caller = this.authUserFromCert(context, this.request);
        final String callerSip = PhoenixDataService.getSIP(caller);
        log.info("Remote user connected (cgroupFetch): " + callerSip);

        // Construct response
        final CgroupGetResponse response = new CgroupGetResponse();
        final CgroupList list = new CgroupList();
        response.setGroupList(list);
        response.setErrCode(0);

        try {
            //
            // Fetch record according to the search criteria.
            //
            TypedQuery<ContactGroup> dbQuery;
            final StringBuilder query = new StringBuilder();
            final Map<String, Object> params = new HashMap<String, Object>();

            query.append("SELECT cg FROM contactGroup cg WHERE cg.owner=:ownerName");
            params.put("ownerName", caller);

            // User can specify list of group IDs to be fetched - but only where he is the owner.
            // Here condition allows to fetch system wide groups with null owner.
            if (request.getIdList() != null && !MiscUtils.collectionIsEmpty(request.getIdList().getIds())){
                final List<Long> ids = request.getIdList().getIds();
                query.append(" OR (cg.id IN :ids AND ( cg.owner=:ownerName OR cg.owner IS NULL ) ) ");
                params.put("ids", new ArrayList<Long>(ids));
            }

            dbQuery = em.createQuery(query.toString(), ContactGroup.class);
            dataService.setQueryParameters(dbQuery, params);

            // Process DB response.
            List<ContactGroup> resultList = dbQuery.getResultList();
            for (ContactGroup cg : resultList) {
                list.getGroups().add(ConversionUtils.dbCgroupToResponse(cg));
            }

            response.setErrCode(0);
            logAction(callerSip, "cgroupFetch", null);

        } catch(Exception e){
            log.error("Exception in fetching pairing contact groups", e);
            response.setErrCode(-2);
        }

        return response;
    }

    /**
     * Request to store a new pairing requests to the database.
     *
     * @param request
     * @param context
     * @return
     * @throws CertificateException
     */
    @PayloadRoot(localPart = "cgroupChangeRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public CgroupUpdateResponse cgroupUpdate(@RequestPayload CgroupUpdateRequest request, MessageContext context) throws CertificateException {
        final Subscriber owner = this.authUserFromCert(context, this.request);
        final String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (cgroupUpdate): " + ownerSip);

        // Construct response
        final CgroupUpdateResponse response = new CgroupUpdateResponse();
        final CgroupUpdateResultList resultList = new CgroupUpdateResultList();
        response.setResultList(resultList);
        response.setErrCode(0);

        try {
            if (request.getUpdatesList() == null) {
                return response;
            }

            final List<CgroupUpdateRequestElement> updList = request.getUpdatesList().getUpdates();
            if (MiscUtils.collectionIsEmpty(updList)) {
                return response;
            }

            for (CgroupUpdateRequestElement elem : updList) {
                final Map<String, Object> params = new HashMap<String, Object>();
                final ArrayList<String> criteria = new ArrayList<String>();
                final CgroupAction action = elem.getAction();
                final CgroupUpdateResult updRes = new CgroupUpdateResult();

                // Add
                if (action == CgroupAction.ADD) {
                    ContactGroup cg = new ContactGroup();
                    cg.setOwner(owner);
                    cg.setDateCreated(new Date());
                    cg.setDateLastEdit(new Date());
                    cg.setGroupKey(elem.getGroupKey() == null ? "" : elem.getGroupKey());
                    cg.setGroupType(elem.getGroupType() == null ? "" : elem.getGroupType());
                    cg.setGroupName(elem.getGroupName() == null ? "" : elem.getGroupName());
                    cg.setAuxData(elem.getAuxData());
                    em.persist(cg);

                    updRes.setResultCode(0);
                    resultList.getResults().add(updRes);
                    continue;
                }

                // Criteria for removal / update -> only owners groups.
                criteria.add("cg.owner=:owner");
                params.put("owner", owner);

                // ID if specified, user can update only groups he owns, no system wide.
                if (elem.getId() != null) {
                    criteria.add("cg.id=:id");
                    params.put("id", elem.getId());
                }

                // Deletion.
                if (action == CgroupAction.REMOVE) {
                    final Query delQuery = em.createQuery(dataService.buildQueryString("DELETE FROM contactGroup cg WHERE ", criteria, ""));
                    dataService.setQueryParameters(delQuery, params);
                    final int updateCode = delQuery.executeUpdate();

                    updRes.setResultCode(updateCode);
                    resultList.getResults().add(updRes);
                    continue;
                }

                // Update left.
                if (action != CgroupAction.UPDATE) {
                    updRes.setResultCode(-3);
                    resultList.getResults().add(updRes);
                    continue;
                }

                // Can update only if ID was specified.
                // No other addresing criteria is allowed at the moment.
                if (elem.getId() == null) {
                    updRes.setResultCode(-4);
                    resultList.getResults().add(updRes);
                    continue;
                }

                // If here, we want to update the record, caller is owner of such record.
                // Update is done in the following way: load entity according to criteria, make changes, persist.
                final String requestQuerySql = "SELECT cg FROM contactGroup cg WHERE";
                TypedQuery<ContactGroup> requestQuery = em.createQuery(
                        dataService.buildQueryString(requestQuerySql, criteria, ""), ContactGroup.class);
                dataService.setQueryParameters(requestQuery, params);
                requestQuery.setMaxResults(1);

                ContactGroup cg = dataService.tryGetSingleResult(requestQuery);
                if (cg == null) {
                    updRes.setResultCode(-5); // Not found
                    resultList.getResults().add(updRes);
                    continue;
                }

                // Make changes and persist.
                cg.setDateLastEdit(new Date());

                // TODO: fix when Android parser is working properly. Null field should indicate "do not update this field".
                // TODO:   In this setting we cannot set given field to empty value. 
                if (!StringUtils.isEmpty(elem.getGroupType())) {
                    cg.setGroupType(elem.getGroupType());
                }

                if (!StringUtils.isEmpty(elem.getGroupKey())) {
                    cg.setGroupKey(elem.getGroupKey());
                }

                if (!StringUtils.isEmpty(elem.getGroupName())) {
                    cg.setGroupName(elem.getGroupName());
                }

                if (!StringUtils.isEmpty(elem.getAuxData())) {
                    cg.setAuxData(elem.getAuxData());
                }

                em.persist(cg);

                updRes.setResultCode(0);
                resultList.getResults().add(updRes);
            }

            // No push notification happens here. If we update our list, we know about it.
            // Deletion is not pushed.
            response.setErrCode(0);
            logAction(ownerSip, "cgroupUpdate", null);

        } catch(Exception e){
            log.error("Exception in updating contact group", e);
            response.setErrCode(-1);
        }

        return response;
    }

    /**
     * Stores information about some action to the usage logs.
     * @param user
     * @param action
     * @param ip 
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public void logAction(String user, String action, String ip){
        UsageLogs l = new UsageLogs();
        l.setLaction(action);
        l.setLuser(user);
        l.setLip(ip);
        l.setLwhen(new Date());
        try {
            em.persist(l);
        } catch(Exception e){
            log.error("Cannot write log entry to the databse", e);
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

    public EntityManager getEm() {
        return em;
    }

    public void setEm(EntityManager em) {
        this.em = em;
    }

    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }

    public String getOwner_sip() {
        return owner_sip;
    }

    public void setOwner_sip(String owner_sip) {
        this.owner_sip = owner_sip;
    }

    public FileManager getFmanager() {
        return fmanager;
    }

    public void setFmanager(FileManager fmanager) {
        this.fmanager = fmanager;
    }

    public PresenceManager getPmanager() {
        return pmanager;
    }

    public void setPmanager(PresenceManager pmanager) {
        this.pmanager = pmanager;
    }

    public ServerCommandExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(ServerCommandExecutor executor) {
        this.executor = executor;
    }

    public AMQPListener getAmqpListener() {
        return amqpListener;
    }

    public void setAmqpListener(AMQPListener amqpListener) {
        this.amqpListener = amqpListener;
    }

    public JiveGlobals getJiveGlobals() {
        return jiveGlobals;
    }

    public void setJiveGlobals(JiveGlobals jiveGlobals) {
        this.jiveGlobals = jiveGlobals;
    }

    public UserPairingManager getPairingMgr() {
        return pairingMgr;
    }

    public void setPairingMgr(UserPairingManager pairingMgr) {
        this.pairingMgr = pairingMgr;
    }
}
