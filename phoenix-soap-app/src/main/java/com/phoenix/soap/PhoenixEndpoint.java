/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import com.phoenix.db.CAcertsSigned;
import com.phoenix.db.Contactlist;
import com.phoenix.db.ContactlistDstObj;
import com.phoenix.db.RemoteUser;
import com.phoenix.db.SubscriberCertificate;
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
import com.phoenix.service.PhoenixServerCASigner;
import com.phoenix.service.TrustVerifier;
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
import com.phoenix.soap.beans.EnabledDisabled;
import com.phoenix.soap.beans.GetCertificateRequest;
import com.phoenix.soap.beans.GetCertificateResponse;
import com.phoenix.soap.beans.GetOneTimeTokenRequest;
import com.phoenix.soap.beans.GetOneTimeTokenResponse;
import com.phoenix.soap.beans.SignCertificateRequest;
import com.phoenix.soap.beans.SignCertificateResponse;
import com.phoenix.soap.beans.UserIdentifier;
import com.phoenix.soap.beans.UserPresenceStatus;
import com.phoenix.soap.beans.UserWhitelistStatus;
import com.phoenix.soap.beans.WhitelistAction;
import com.phoenix.soap.beans.WhitelistElement;
import com.phoenix.soap.beans.WhitelistGetResponse;
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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
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
                throw new CertificateException("You are not authorized, go away!");
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
     * Whitelist get request  - returns whole whitelist for requesting user
     * @param request
     * @param context
     * @return 
     */
    @PayloadRoot(localPart = "whitelistGetRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public WhitelistGetResponse whitelistGetRequest(@RequestPayload WhitelistGetRequest request, MessageContext context) throws CertificateException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        log.info("User connected: " + owner);
        
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
     * Method for obtaining user certificate. 
     * User calls this if wants to communicate with remote user and in never communicated
     * with it before.
     * 
     * This method can be called by remote users also (more servers setup) thus
     * it does not necessarily have subscriber record here. 
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
    public GetCertificateResponse getCertificate(@RequestPayload GetCertificateRequest request, MessageContext context) throws CertificateException {
        String owner = this.authRemoteUserFromCert(context, this.request);
        log.info("Remote user connected: " + owner);
       
        // support also remote users :)
        // 1. try to load local user
        Subscriber sub = null;
        RemoteUser rem = null;
        
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
        
        // still nul? 
        if (sub==null && rem==null){
            throw new IllegalArgumentException("User you are claiming you are does not exist!");
        }
        
        // define answer, will be iteratively filled in
        GetCertificateResponse response = new GetCertificateResponse();
        
        // maximum length?
        if (request.getUser()==null || request.getUser().isEmpty() || request.getUser().size()>30){
            throw new IllegalArgumentException("Invalid size of request");
        }
        
        // now we have prepared data for whitelist search, lets iterate over 
        for(UserIdentifier ui : request.getUser()){
            // At first obtain user object we are talking about.
            // Now assume only local user, we will have procedure for extern and groups also
            // in future (I hope so:)).
            Subscriber s = null;
            String sip = ui.getUserSIP();
            Long userID = ui.getUserID();
            if (sip!=null && !sip.isEmpty()){
                s = dataService.getLocalUser(sip);
            } else if (userID!=null && userID>0){
                s = dataService.getLocalUser(userID);
            } else {
                throw new RuntimeException("Both user identifiers are null");
            }
            
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
            }
            
            // check if user "s" has in whitelist "owner" = WhitelistDstObj
            Whitelist wl = this.dataService.getWhitelistForSubscriber(s, dstObj);
            if ((wl==null || wl.getAction()!=WhitelistStatus.ENABLED) && s.equals(dstObj.getIntern_user())==false){
                log.info("Not allowed: " + wl);
                wr.setStatus(CertificateStatus.FORBIDDEN);
                response.getReturn().add(wr);
                continue;
            }
            
            // obtain certificate for particular subscriber
            SubscriberCertificate cert = this.dataService.getCertificateForUser(s);
            if (cert==null || cert.getCert()==null){
                log.info("Certificate for user is null");
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
            log.info("cert is not null! " + cert509);
            
            // time validity
            try{
                cert509.checkValidity();
            } catch(Exception e){
                // certificate is invalid
                log.info("Certificate for user is invalid");
                wr.setStatus(CertificateStatus.INVALID);
                response.getReturn().add(wr);
                continue;
            }
            
            // now just add certificate to response
            wr.setStatus(CertificateStatus.OK);
            wr.setUser(PhoenixDataService.getSIP(s));
            wr.setCertificate(cert509.getEncoded());
            response.getReturn().add(wr);
        }
        
        return response;
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
     *      can revert value of flag to enable signing again.
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
    public SignCertificateResponse signCertificate(@RequestPayload SignCertificateRequest request, MessageContext context) throws CertificateException {
        //String owner = this.authRemoteUserFromCert(context, this.request);
        //log.info("Remote user connected: " + owner);
        
        // construct response wrapper
        SignCertificateResponse response = new SignCertificateResponse();
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
            log.info("Going to extract request");
            PKCS10CertificationRequest csrr = this.signer.getReuest(csr, false);
            log.info("Request extracted: " + csrr);
            log.info("Request extracted: " + csrr.getSubject().toString());
            
            // request data here - username from signCertificate request
            String reqUser = request.getUser();
            log.info("User [" + reqUser + "] is asking for signing a certificate");
            
            // check request validity - CN format, match to username in request
            String certCN = this.auth.getCNfromX500Name(csrr.getSubject());
            log.info("CN from certificate: " + certCN);
            if (certCN==null || certCN.isEmpty()){
                throw new IllegalArgumentException("CN in certificate is null");
            } else if (certCN.equals(reqUser)==false){
                throw new IllegalArgumentException("CN in certificate does not match user in request");
            }
            
            // user matches, load subscriber data for him
            Subscriber localUser = this.dataService.getLocalUser(reqUser);
            if (localUser == null){
                log.warn("Local user was not found in database for: " + reqUser);
                throw new IllegalArgumentException("Not authorized");
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
            for (int i=-1, c=0; i <=1 ; i++, c++){
                userHashes[c] = this.dataService.generateUserAuthToken(
                    reqUser, localUser.getHa1(), 
                    request.getUsrToken(), request.getServerToken(), 
                    1000 * 60, i);
            }
            
            // verify one of auth hashes
            boolean authHash_valid=false;
            for(String curAuthHash : userHashes){
                log.info("Verify auth hash["+request.getAuthHash()+"] vs. genhash["+curAuthHash+"]");
                if (curAuthHash.equals(request.getAuthHash())){
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
            if (localUser.isCanSignNewCert()==false){
                log.warn("User cannot sign certificates");
                throw new IllegalArgumentException("Not authorized");
            }
            
            // certifiacte for user form different table
            SubscriberCertificate certificateForUser=null;
            
            // check if user already has valid certificate (not revoked, date valid)
            CAcertsSigned userCert = null;
            
            try {
                    userCert = em.createQuery("select cs from CAcertsSigned cs "
                                    + " WHERE cs.subscriber=:s "
                                    + " AND cs.isRevoked=false"
                                    + " AND cs.notValidAfter>:n"    
                                    + " ORDER BY cs.notValidBefore DESC", CAcertsSigned.class)
                                    .setParameter("s", localUser)
                                    .setParameter("n", new Date())
                                    .getSingleResult();
            } catch(Exception ex){
                // no entity found and another exceptions - not interested in
                log.debug("No entity returned when asking for CAsigned DB certificate", ex);
            }
            
            if (userCert!=null){
                // check if current certificate is about to expire
                // in interval 14 days. If not, user is not allowed to sign new
                // certificates.
                log.info("User has some valid certificate in DB: " + userCert);
                
                Date expireLimit = new Date(System.currentTimeMillis() + 14 * 24 * 60 * 60 * 1000);
                if (userCert.getNotValidAfter().after(expireLimit)){
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
                
                // check if this certificate is also in subscriber certificate table
                certificateForUser = this.dataService.getCertificateForUser(localUser);
                if (certificateForUser==null){
                    log.warn("Certificate for user is null but there is some valid in CA db");
                } else {
                    log.info("There also exists certificate in subscriber database");
                    em.remove(certificateForUser);
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
            Date notAfter  = new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000);
            
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
            Long newSerial = ((BigInteger) query.getSingleResult()).longValue() +1;

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
            
            // create also subscriber certificate in SubscribeCertificate table
            // for another applications.
            SubscriberCertificate sc = new SubscriberCertificate();
            sc.setRawCert(sign.getEncoded());
            sc.setDateCreated(new Date());
            sc.setDateLastEdit(new Date());
            sc.setSubscriber(localUser);
            em.persist(sc);
            
            // flush transaction
            em.flush();
            
            response.getCertificate().setStatus(CertificateStatus.OK);
            response.getCertificate().setCertificate(sign.getEncoded());
            response.getCertificate().setUser(reqUser);
            
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
     * Contact list change request
     * @param request
     * @param context
     * @return 
     */
    @PayloadRoot(localPart = "getOneTimeTokenRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public GetOneTimeTokenResponse getOneTimeToken(@RequestPayload GetOneTimeTokenRequest request, MessageContext context) throws CertificateException, NoSuchAlgorithmException {
        Subscriber owner = this.authUserFromCert(context, this.request);
        log.info("User connected: " + owner);
        
        // generate new one time token - 2 minutes validity
        String ott = this.dataService.generateOneTimeToken(request.getUser(), request.getUserToken(), Long.valueOf(1000 * 60 * 2), "");
        
        GetOneTimeTokenResponse response = new GetOneTimeTokenResponse();
        response.setUser(request.getUser());
        response.setUserToken(request.getUserToken());
        response.setServerToken(ott);
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
