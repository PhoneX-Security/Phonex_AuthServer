/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import com.phoenix.db.Contactlist;
import com.phoenix.db.ContactlistDstObj;
import com.phoenix.db.Whitelist;
import com.phoenix.db.WhitelistDstObj;
import com.phoenix.db.WhitelistSrcObj;
import com.phoenix.db.extra.ContactlistObjType;
import com.phoenix.db.extra.ContactlistStatus;
import com.phoenix.db.extra.WhitelistObjType;
import com.phoenix.db.extra.WhitelistStatus;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.EndpointAuth;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.soap.beans.WhitelistRequest;
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
import com.phoenix.soap.beans.EnabledDisabled;
import com.phoenix.soap.beans.UserIdentifier;
import com.phoenix.soap.beans.UserPresenceStatus;
import com.phoenix.soap.beans.UserWhitelistStatus;
import com.phoenix.soap.beans.WhitelistAction;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.ejb.HibernateEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic phoenix service endpoint for whitelist and contactlist manipulation
 * @author ph4r05
 */
@Endpoint
public class PhoenixEndpoint {
    private static final Logger log = LoggerFactory.getLogger(PhoenixEndpoint.class);
    private static final String NAMESPACE_URI = "http://phoenix.com/hr/schemas";
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @Autowired(required = true)
    private EndpointAuth auth;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required = true)
    private HttpServletRequest request;
    
    @Autowired(required = true)
    private X509TrustManager trustManager;
    
    @Autowired(required = true)
    private PhoenixDataService dataService;
    
    // owner SIP obtained from certificate
    private String owner_sip;

    public PhoenixEndpoint() {
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
                return null;
            }
            
            log.info("Request came from user: [" + sip + "]");
            this.owner_sip = sip;
            Subscriber subs = this.dataService.getLocalUser(sip);
            if (subs==null){
                throw new CertificateException("You are not authorized, go away!");
            }
            
            return subs;
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
    }
    
    /**
     * Whitelist manipulation request - with this is user changing its white list
     * @param request
     * @param context
     * @return 
     */
    @PayloadRoot(localPart = "whitelistRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public WhitelistResponse whitelistRequest(@RequestPayload WhitelistRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        log.info("User connected: " + owner);
        
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
     * Contactlist get request
     * @param request
     * @param context
     * @return 
     */
    @PayloadRoot(localPart = "contactlistGetRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public ContactlistGetResponse contactlistGetRequest(@RequestPayload ContactlistGetRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        log.info("User connected: " + owner);
        
        // subscriber list
        List<Subscriber> subs = new LinkedList<Subscriber>();
        
        // analyze request - extract user identifiers to load from contact list.
        // At first extract all users with SIP name from local user table and convert
        // to user ID
        List<Long> userIDS = null;
        List<UserIdentifier> alias = request.getUser();
        if (alias!=null && !alias.isEmpty()){
            // OK now load only part of contactlist we are interested in...
            //
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
            getContactListQuery = "SELECT s FROM contactlist cl "
                    + "LEFT OUTER JOIN cl.obj.intern_user s "
                    + "WHERE cl.objType=:objtype AND cl.owner=:owner "
                    + "ORDER BY s.domain, s.username";
            TypedQuery<Subscriber> query = em.createQuery(getContactListQuery, Subscriber.class);
            query.setParameter("objtype", ContactlistObjType.INTERNAL_USER);
            query.setParameter("owner", owner);
            List<Subscriber> resultList = query.getResultList();
            for(Subscriber o : resultList){
                subs.add(o);
            }
        }
                
        // extract whitelist information for subscribers - adding extra info
        // for each contact. 
        Map<String, Whitelist> wlist = this.dataService.getWhitelistForUsers(owner, subs, null);
        
        // now wrapping container 
        ContactlistGetResponse response = new ContactlistGetResponse();
        for(Subscriber o : subs){
            String userSIP = o.getUsername() + "@" + o.getDomain();
            
            ContactListElement elem = new ContactListElement();
            elem.setAlias(o.getUsername());
            elem.setContactlistStatus(EnabledDisabled.ENABLED);
            elem.setPresenceStatus(UserPresenceStatus.ONLINE);
            elem.setUserid(o.getId());
            elem.setUsersip(userSIP);
            elem.setWhitelistStatus(UserWhitelistStatus.NOCLUE);
            
            // whitelist 
            if (wlist.containsKey(userSIP)){
                Whitelist wl = wlist.get(userSIP);
                log.info("User whitelist: " + wl);
                
                elem.setWhitelistStatus(wl.getAction()==WhitelistStatus.ENABLED ? UserWhitelistStatus.IN : UserWhitelistStatus.NOTIN);
            }
            
            response.getContactlistEntry().add(elem);
        }
        
        return response;
    }
    
    /**
     * Contact list change request
     * @param request
     * @param context
     * @return 
     */
    @PayloadRoot(localPart = "contactlistChangeRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public ContactlistChangeResponse contactlistChangeRequest(@RequestPayload ContactlistChangeRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        log.info("User connected: " + owner);
        
        // construct response, then add results iteratively
        ContactlistChangeResponse response = new ContactlistChangeResponse();
        
        // analyze request
        List<ContactlistChangeRequestElement> elems = request.getContactlistChangeRequestElement();
        if (elems==null || elems.isEmpty()){
            log.info("elems is empty");
            response.getReturn().add(-1);
            return response;
        }
        
        // iterating over request, algorithm:
        // 1. select targeted subscriber
        // 2. create/modify/delete contact list accordingly
        log.info("elems is not null; size: " + elems.size());
        for(ContactlistChangeRequestElement elem : elems){
            if (elem==null) continue;
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
            
            // null subscriber is not implemented yet
            if (s==null){
                log.info("Subscriber defined is null, not implemented yet.");
                response.getReturn().add(-1);
                continue;
            }
            
            // is there already some contact list item?
            Contactlist cl = this.dataService.getContactlistForSubscriber(owner, s);
            ContactlistAction action = elem.getAction();
            try {
                if (cl!=null){
                    // contact list entry is empty -> record does not exist
                    if (action == ContactlistAction.REMOVE){
                        this.dataService.remove(cl, true);
                        response.getReturn().add(1);
                        continue;
                    }
                    
                    cl.setEntryState(action == ContactlistAction.DISABLE ? ContactlistStatus.DISABLED : ContactlistStatus.ENABLED);
                    this.dataService.persist(cl, true);
                    response.getReturn().add(1);
                    
                } else {
                    // contact list entry is empty -> record does not exist
                    if (action == ContactlistAction.REMOVE){
                        response.getReturn().add(-1);
                        log.info("Wanted to delete non-existing whitelist record");
                        continue;
                    }
                    
                    cl = new Contactlist();
                    cl.setDateCreated(new Date());
                    cl.setDateLastEdit(new Date());
                    cl.setOwner(owner);
                    cl.setObjType(ContactlistObjType.INTERNAL_USER);
                    cl.setObj(new ContactlistDstObj(s));
                    cl.setEntryState(action == ContactlistAction.DISABLE ? ContactlistStatus.DISABLED : ContactlistStatus.ENABLED);
                    this.dataService.persist(cl, true);
                    response.getReturn().add(1);
                }
            } catch(Exception e){
                log.info("Manipulation with contactlist failed", e);
                response.getReturn().add(-1);
            }
        }
       
        return response;
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
}
