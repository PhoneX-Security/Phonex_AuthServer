/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import com.phoenix.db.CAcertsSigned;
import com.phoenix.db.Contactlist;
import com.phoenix.db.ContactlistDstObj;
import com.phoenix.db.DHKeys;
import com.phoenix.db.RemoteUser;
import com.phoenix.db.StoredFiles;
import com.phoenix.db.UsageLogs;
import com.phoenix.db.Whitelist;
import com.phoenix.db.WhitelistDstObj;
import com.phoenix.db.WhitelistSrcObj;
import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.extra.ContactlistStatus;
import com.phoenix.db.extra.WhitelistObjType;
import com.phoenix.db.extra.WhitelistStatus;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.db.opensips.Xcap;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.files.FileManager;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.PhoenixServerCASigner;
import com.phoenix.service.pres.PresenceManager;
import com.phoenix.service.ServerCommandExecutor;
import com.phoenix.service.TrustVerifier;
import com.phoenix.soap.beans.AuthCheckRequestV2;
import com.phoenix.soap.beans.AuthCheckResponseV2;
import com.phoenix.soap.beans.TrueFalse;
import com.phoenix.soap.beans.TrueFalseNA;
import com.phoenix.soap.beans.CertificateRequestElement;
import com.phoenix.soap.beans.CertificateStatus;
import com.phoenix.soap.beans.CertificateWrapper;
import com.phoenix.soap.beans.WhitelistRequest;
import com.phoenix.soap.beans.WhitelistGetRequest;
import com.phoenix.soap.beans.WhitelistRequestElement;
import com.phoenix.soap.beans.WhitelistResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;

import com.phoenix.soap.beans.ContactListElement;
import com.phoenix.soap.beans.ContactlistAction;
import com.phoenix.soap.beans.ContactlistChangeRequest;
import com.phoenix.soap.beans.ContactlistChangeRequestElement;
import com.phoenix.soap.beans.ContactlistChangeResponse;
import com.phoenix.soap.beans.ContactlistGetRequest;
import com.phoenix.soap.beans.ContactlistGetResponse;
import com.phoenix.soap.beans.ContactlistReturn;
import com.phoenix.soap.beans.EnabledDisabled;
import com.phoenix.soap.beans.FtAddDHKeysRequest;
import com.phoenix.soap.beans.FtAddDHKeysResponse;
import com.phoenix.soap.beans.FtRemoveDHKeysRequest;
import com.phoenix.soap.beans.FtRemoveDHKeysResponse;
import com.phoenix.soap.beans.*;
import com.phoenix.soap.beans.FtDHKey;
import com.phoenix.soap.beans.GetCertificateRequest;
import com.phoenix.soap.beans.GetCertificateResponse;
import com.phoenix.soap.beans.GetOneTimeTokenRequest;
import com.phoenix.soap.beans.GetOneTimeTokenResponse;
import com.phoenix.soap.beans.PasswordChangeRequest;
import com.phoenix.soap.beans.PasswordChangeResponse;
import com.phoenix.soap.beans.SignCertificateRequest;
import com.phoenix.soap.beans.SignCertificateResponse;
import com.phoenix.soap.beans.UserIdentifier;
import com.phoenix.soap.beans.UserPresenceStatus;
import com.phoenix.soap.beans.UserWhitelistStatus;
import com.phoenix.soap.beans.WhitelistAction;
import com.phoenix.soap.beans.WhitelistElement;
import com.phoenix.soap.beans.WhitelistGetResponse;
import com.phoenix.utils.AESCipher;
import com.phoenix.utils.StringUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    
    // owner SIP obtained from certificate
    private String owner_sip;
    public static final String DISPLAY_NAME_REGEX="^[a-zA-Z0-9_\\-\\s\\./]+$";
    
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
            log.info("Alias is empty");
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
        Subscriber owner = this.authUserFromCert(context, this.request);
        String ownerSip = PhoenixDataService.getSIP(owner);
        log.info("Remote user connected (contactlistChangeRequest) : " + ownerSip);
        
        // construct response, then add results iteratively
        ContactlistChangeResponse response = new ContactlistChangeResponse();
        
        // analyze request
        List<ContactlistChangeRequestElement> elems = request.getContactlistChangeRequestElement();
        if (elems==null || elems.isEmpty()){
            log.info("elems is empty");
            ContactlistReturn ret = new ContactlistReturn();
            ret.setResultCode(CLIST_CHANGE_ERROR_EMPTY_REQUEST_LIST);
            
            response.getReturn().add(ret);
            return response;
        }
        
        // iterating over request, algorithm:
        // 1. select targeted subscriber
        // 2. create/modify/delete contact list accordingly
        log.info("elems is not null; size: " + elems.size());
        log.info("elems2string: " + elems.toString());
        try {
            // Store users whose contact list was modified. For them will be later 
            // regenerated XML policy file for presence.
            Map<String, Subscriber> changedUsers = new HashMap<String, Subscriber>();
            
            // Iterate over all change requets element in request. Every can contain 
            // request for different subscriber.
            for(ContactlistChangeRequestElement elem : elems){
                if (elem==null) continue;
                log.info("elem2string: " + elem.toString());
                log.info("user: " + elem.getUser());
                log.info("action: " + elem.getAction());
                log.info("ActionRequest: on [" + elem.getUser() + "] do: [" + elem.getAction().value() + "]");

                // At first obtain user object we are talking about.
                // Now assume only local user, we will have procedure for extern and groups also
                // in future (I hope so:)).
                Subscriber s = null;
                String sip = elem.getUser().getUserSIP();
                Long userID = elem.getUser().getUserID();
                if (sip!=null && !sip.isEmpty()){
                    s = dataService.getLocalUser(sip);
                } else if (userID!=null && userID>0){
                    s = dataService.getLocalUser(userID);
                } else {
                    throw new RuntimeException("Both user identifiers are null");
                }
                
                ContactlistReturn ret = new ContactlistReturn();
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
                if (ownerSip.equals(targetUser)==false){
                    log.warn("Changing contactlist for somebody else is not permitted");
                    response.getReturn().add(ret);
                    
                    // TODO: load target owner
                    continue;
                }
                
                // users whose contact list was changed - updating presence rules afterwards
                if (changedUsers.containsKey(targetUser)==false){
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
                            final String newDispName = elem.getDisplayName();
                            if (newDispName.isEmpty()==false && newDispName.matches(DISPLAY_NAME_REGEX)==false){
                                ret.setResultCode(CLIST_CHANGE_ERROR_INVALID_NAME);
                                response.getReturn().add(ret);
                                log.info("Display name regex fail: [" + newDispName + "]");
                                continue;
                            }
                            
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
                            cl = new Contactlist();
                            cl.setDateCreated(new Date());
                            cl.setDateLastEdit(new Date());
                            cl.setOwner(owner);
                            cl.setObjType(ContactlistObjType.INTERNAL_USER);
                            cl.setObj(new ContactlistDstObj(s));
                            cl.setEntryState(action == ContactlistAction.DISABLE ? ContactlistStatus.DISABLED : ContactlistStatus.ENABLED);
                            cl.setDisplayName(elem.getDisplayName());
                            
                            // whitelist state
                            WhitelistAction waction = elem.getWhitelistAction();
                            if (waction == WhitelistAction.ENABLE || waction == WhitelistAction.ENABLE){
                                cl.setInWhitelist(true);
                            } else {
                                cl.setInWhitelist(false);
                            }
                            
                            this.dataService.persist(cl, true);
                            ret.setResultCode(1);
                            response.getReturn().add(ret);
                        } else {
                            ret.setResultCode(CLIST_CHANGE_ERROR_GENERIC);
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
                    
                    List<Contactlist> contactlistForSubscriber = dataService.getContactlistForSubscriber(entry.getValue());
                    Map<Integer, Subscriber> internalUsersInContactlist = dataService.getInternalUsersInContactlist(contactlistForSubscriber);
                    
                    List<String> sips = new ArrayList(contactlistForSubscriber.size());
                    for(Entry<Integer, Subscriber> e : internalUsersInContactlist.entrySet()){
                        String clsip = PhoenixDataService.getSIP(e.getValue());
                        sips.add(clsip);
                    }
                    
                    Xcap xcapEntity = pmanager.updateXCAPPolicyFile(tuser.getUsername(), tuser.getDomain(), sips);
                    log.info("XcapEntity persisted");
                    this.em.flush();
                    
                    // Synchronize roster list.
                    dataService.syncRosterWithRetry(tuser, contactlistForSubscriber, 3);
                } catch(Exception ex){
                    log.error("Exception during presence rules generation for: " + entry.getValue(), ex);
                }
            }
            
        } catch(Exception e){
            log.info("Exception ocurred", e);
            return null;
        }
       
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
        String owner = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected (getCertificate): " + owner);
       
        // support also remote users :)
        // 1. try to load local user
        Subscriber sub = null;
        RemoteUser rem = null;
        
        // Monitor if user is asking for different certificates than his.
        // If yes, use this infromation to set first user add date if is empty.
        boolean askingForDifferentThanOurs = false;
        
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
        
        // maximum length?
        if (request.getElement()==null || request.getElement().isEmpty() || request.getElement().size()>256){
            throw new IllegalArgumentException("Invalid size of request");
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
                log.info("Obtaining server certificate");
                // now just add certificate to response
                wr.setStatus(CertificateStatus.OK);
                wr.setUser(sip);
                wr.setCertificate(this.trustManager.getServerCA().getEncoded());
                response.getReturn().add(wr);
                continue;
            }
            
            // null subscriber - fail, user has to exist in database
            if (s==null){
                log.info("User is not found in database");
                wr.setStatus(CertificateStatus.NOUSER);
                response.getReturn().add(wr);
                continue;
            } else {
                log.info("User to obtain certificate for: " + s.getUsername());
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
            if (certHash!=null && certHash.isEmpty()==false){
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
                    response.getReturn().add(wr);
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
                response.getReturn().add(wr);
                continue;
            }
            
            // certificate
            X509Certificate cert509 = cert.getCert();
            log.info("cert is not null!; DBserial=[" + cert.getSerial() + "] Real ceritificate: " + cert509);
            
            // time validity
            try{
                // time-date validity
                cert509.checkValidity();
                
                // is certificate valid?
                this.trustManager.checkTrusted(cert509);
                
                // is revoked?
                Boolean certificateRevoked = this.signer.isCertificateRevoked(cert509);
                if (certificateRevoked!=null && certificateRevoked.booleanValue()==true){
                    log.info("Certificate for user is revoked: " + cert509.getSerialNumber().longValue());
                    wr.setStatus(CertificateStatus.REVOKED);
                    response.getReturn().add(wr);
                    continue;
                }
            } catch(Exception e){
                // certificate is invalid
                log.info("Certificate for user is invalid", e);
                wr.setStatus(CertificateStatus.INVALID);
                response.getReturn().add(wr);
                continue;
            }
            
            // now just add certificate to response
            wr.setStatus(CertificateStatus.OK);
            wr.setCertificate(cert509.getEncoded());
            response.getReturn().add(wr);
        }
        
        // Logic for setting first user added field.
        if (sub != null){
            // If user does not have first login date filled in, add this one.
            Calendar fUserAdded = sub.getDateFirstUserAdded();
            if (askingForDifferentThanOurs && (fUserAdded == null || fUserAdded.before(get1971()))){
                sub.setDateFirstUserAdded(Calendar.getInstance());
                log.info(String.format("First user added date set to: %s", sub.getDateFirstUserAdded()));
            }   
            
            // First login if not set.
            Calendar fLogin = sub.getDateFirstLogin();
            if (fLogin == null || fLogin.before(get1971())){
                sub.setDateFirstLogin(Calendar.getInstance());
                log.info(String.format("First login set to: %s", sub.getDateFirstLogin()));
            }
            
            // Update last activity date.
            sub.setDateLastActivity(Calendar.getInstance());
            em.persist(sub);
            log.info(String.format("Last activity set to: %s", sub.getDateLastActivity()));
        }
        
        logAction(owner, "getCert", null);
        return response;
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
    @PayloadRoot(localPart = "passwordChangeRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public PasswordChangeResponse passwordChange(@RequestPayload PasswordChangeRequest request, MessageContext context) throws CertificateException {
        // Prepare request v2.
        PasswordChangeV2Request req2 = new PasswordChangeV2Request();
        req2.setAuthHash(request.getAuthHash());
        req2.setNewHA1(request.getNewHA1());
        req2.setNewHA1B(request.getNewHA1B());
        req2.setServerToken(request.getServerToken());
        req2.setTargetUser(request.getTargetUser());
        req2.setUser(request.getUser());
        req2.setUsrToken(request.getUsrToken());
        req2.setVersion(1);
        
        // Do the job.
        PasswordChangeV2Response resp2 = this.passwordChangeV2(req2, context);
        
        // Convert back to v1.
        PasswordChangeResponse resp1 = new PasswordChangeResponse();
        resp1.setReason(resp2.getReason());
        resp1.setResult(resp2.getResult());
        resp1.setTargetUser(resp2.getTargetUser());
        return resp1;
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
    @PayloadRoot(localPart = "authCheckRequestV2", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public AuthCheckResponseV2 authCheckV2(@RequestPayload AuthCheckRequestV2 request, MessageContext context) throws CertificateException {
        // 1. Construct v3 request and port data to it.
        AuthCheckV3Request req3 = new AuthCheckV3Request();
        req3.setAuthHash(request.getAuthHash());
        req3.setAuxJSON(request.getAuxJSON());
        req3.setAuxVersion(request.getAuxVersion());
        req3.setTargetUser(request.getTargetUser());
        req3.setUnregisterIfOK(request.getUnregisterIfOK());
        Integer reqVer = request.getVersion();
        if (reqVer == null || reqVer.compareTo(0) == 0){
            reqVer = 2;
        }
        req3.setVersion(reqVer);
        
        // 2. Do the auth check itself
        AuthCheckV3Response resp3 = this.authCheckV3(req3, context);

        // 3. Port response 3 to response 2.
        AuthCheckResponseV2 r2 = new AuthCheckResponseV2();
        r2.setAccountDisabled(resp3.isAccountDisabled());
        r2.setAccountExpires(resp3.getAccountExpires());
        r2.setAuthHashValid(resp3.getAuthHashValid());
        r2.setAuxJSON(resp3.getAuxJSON());
        r2.setAuxVersion(resp3.getAuxVersion());
        r2.setCertStatus(resp3.getCertStatus());
        r2.setCertValid(resp3.getCertValid());
        r2.setErrCode(resp3.getErrCode());
        r2.setForcePasswordChange(resp3.getForcePasswordChange());
        r2.setServerTime(resp3.getServerTime());
        return r2;
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
    @PayloadRoot(localPart = "authCheckV2Request", namespace = NAMESPACE_URI)
    @ResponsePayload
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public AuthCheckV2Response authCheckV2(@RequestPayload AuthCheckV2Request request, MessageContext context) throws CertificateException {
        // 1. Construct v3 request and port data to it.
        AuthCheckV3Request req3 = new AuthCheckV3Request();
        req3.setAuthHash(request.getAuthHash());
        req3.setAuxJSON(request.getAuxJSON());
        req3.setAuxVersion(request.getAuxVersion());
        req3.setTargetUser(request.getTargetUser());
        req3.setUnregisterIfOK(request.getUnregisterIfOK());
        Integer reqVer = request.getVersion();
        if (reqVer == null || reqVer.compareTo(0) == 0){
            reqVer = 2;
        }
        req3.setVersion(reqVer);
        
        // 2. Do the auth check itself
        AuthCheckV3Response resp3 = this.authCheckV3(req3, context);
        AuthCheckV2Response r2 = new AuthCheckV2Response();
        r2.setAccountDisabled(resp3.isAccountDisabled());
        r2.setAccountExpires(resp3.getAccountExpires());
        r2.setAuthHashValid(resp3.getAuthHashValid());
        r2.setAuxJSON(resp3.getAuxJSON());
        r2.setAuxVersion(resp3.getAuxVersion());
        r2.setCertStatus(resp3.getCertStatus());
        r2.setCertValid(resp3.getCertValid());
        r2.setErrCode(resp3.getErrCode());
        r2.setForcePasswordChange(resp3.getForcePasswordChange());
        r2.setServerTime(resp3.getServerTime());
        return r2;   
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
            String sip = request.getTargetUser();
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
            
            if (authHash_valid==false){
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
            if (passwdChange!=null && passwdChange==true){
                resp.setForcePasswordChange(TrueFalse.TRUE);
            }
            
            // if we have some certificate, we can continue with checks
            X509Certificate[] chain = auth.getCertificateChainFromConnection(context, this.request);
            if (chain==null){
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
                if (certSip.equals(sip)==false){
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
                
                // First login if not set.
                Calendar fAuthCheck = localUser.getDateFirstAuthCheck();
                if (fAuthCheck == null || fAuthCheck.before(get1971())){
                    localUser.setDateFirstAuthCheck(Calendar.getInstance());
                    log.info(String.format("First autch check set to: %s", localUser.getDateFirstAuthCheck()));
                }
                
                // Store app version provided by the user so we have statistics of update and for debugging.
                String appVersion = StringUtils.takeMaxN(request.getAppVersion(), 128);
                if (appVersion != null){
                    localUser.setAppVersion(appVersion);
                }

                // Update last activity date.
                localUser.setDateLastAuthCheck(Calendar.getInstance());
                em.persist(localUser);
                
                logAction(certSip, "authCheck3", null);
            } catch (Exception e){
                // no certificate, just return response, exception is 
                // actually really expected :)
                log.debug("Certificate was not valid: ", e);
                resp.setCertValid(TrueFalseNA.FALSE);
                resp.setCertStatus(CertificateStatus.INVALID);
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
         } catch(Exception e){
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
        resp.setCertValid(TrueFalseNA.NA);
        resp.setCertStatus(CertificateStatus.MISSING);
        resp.setForcePasswordChange(TrueFalse.FALSE);
        resp.setAccountDisabled(true);
        resp.setAuxVersion(0);
        resp.setAuxJSON("");
        resp.setErrCode(404);
        try {
            // Integer version by default set to 3. 
            // Changes window of the login validity.
            int reqVersion = 3;
            Integer reqVersionInt = request.getVersion();
            if (reqVersionInt != null){
                reqVersion = reqVersionInt.intValue();
            }
            
            // Date conversion can throw exception
            resp.setAccountExpires(getXMLDate(new Date()));
            resp.setServerTime(getXMLDate(new Date()));
            resp.setAccountIssued(getXMLDate(new Date()));
            
            // user is provided in request thus try to load it from database
            String sip = request.getTargetUser();
            log.info(String.format("User [%s] is asking for details, reqVer: %d", sip, reqVersion));
            
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
            if (passwdChange!=null && passwdChange==true){
                resp.setForcePasswordChange(TrueFalse.TRUE);
            }
            
            // now try to test certificate if any provided
            try {
                // check user validity
                if (owner.equals(sip)==false){
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
                
                logAction(owner, "accountInfo1", null);
            } catch (Exception e){
                // no certificate, just return response, exception is 
                // actually really expected :)
                log.debug("Certificate was not valid: ", e);
                resp.setCertValid(TrueFalseNA.FALSE);
                resp.setCertStatus(CertificateStatus.INVALID);
                return resp;
            }
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
     * @param request
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
            log.info("Request extracted: " + csrr);
            log.info("Request extracted: " + csrr.getSubject().toString());
            
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
            log.info("Certificate signed: " + sign);
            
            // update cacert in CA database - missing certificate values (binary, digest)
            cacertsSigned.setRawCert(sign.getEncoded());
            cacertsSigned.setCertHash(this.signer.getCertificateDigest(sign));
            em.persist(cacertsSigned);
            
            // flush transaction
            em.flush();
            
            response.getCertificate().setStatus(CertificateStatus.OK);
            response.getCertificate().setCertificate(sign.getEncoded());
            response.getCertificate().setUser(reqUser);
            
            // If user does not have first login date filled in, add this one.
            Calendar fLogin = localUser.getDateFirstLogin();
            if (fLogin == null || fLogin.before(get1971())){
                localUser.setDateFirstLogin(Calendar.getInstance());
                log.info(String.format("First login set to: %s", localUser.getDateFirstLogin()));
                em.persist(localUser);
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
        } catch (Exception ex){
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
        log.info("dhkeys is not null; size: " + dhkeys.size());
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
                } catch(Exception e){
                    log.warn("Exception when adding DH key to the database", e);
                } finally {
                    response.getResult().getCode().add(result);
                }
            }
            
            errCode=1;
            
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
            
            return response;
        } catch(Exception e){
            response.setErrCode(-1);
            log.error("Exception ftGetStoredFiles()", e);
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
}
