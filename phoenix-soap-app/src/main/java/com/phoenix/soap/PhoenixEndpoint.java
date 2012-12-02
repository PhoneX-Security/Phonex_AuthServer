/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import com.phoenix.service.EndpointAuth;
import com.phoenix.soap.beans.WhitelistRequest;
import com.phoenix.soap.beans.WhitelistRequestElementType;
import com.phoenix.soap.beans.WhitelistResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;

import com.phoenix.service.HumanResourceService;
import com.phoenix.soap.beans.ContactListElementType;
import com.phoenix.soap.beans.ContactlistChangeRequest;
import com.phoenix.soap.beans.ContactlistChangeRequestElementType;
import com.phoenix.soap.beans.ContactlistChangeResponse;
import com.phoenix.soap.beans.ContactlistGetRequest;
import com.phoenix.soap.beans.ContactlistGetResponse;
import com.phoenix.soap.beans.EnabledDisabledType;
import com.phoenix.soap.beans.UserPresenceStatusType;
import com.phoenix.soap.beans.UserWhitelistStatusType;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.logging.Level;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.xpath.XPathExpression;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.xml.parsers.DocumentBuilderFactory;
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
    private XPathExpression<Element> startDateExpression;
    private XPathExpression<Element> endDateExpression;
    private XPathExpression<Element> firstNameExpression;
    private XPathExpression<Element> lastNameExpression;
    private HumanResourceService humanResourceService;
    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private static final String NL = "\r\n";
    
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

    @Autowired
    public PhoenixEndpoint(HumanResourceService humanResourceService) throws JDOMException {
        this.humanResourceService = humanResourceService;
    }

    /**
     * Whitelist manipulation request
     * @param request
     * @param context
     * @return 
     */
    @PayloadRoot(localPart = "whitelistRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public WhitelistResponse whitelistRequest(@RequestPayload WhitelistRequest request, MessageContext context) throws CertificateException {
        try {
            auth.check(context, this.request);
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
        
        List<WhitelistRequestElementType> whitelistrequestElement =
                request.getWhitelistrequestElement();

        for (WhitelistRequestElementType element : whitelistrequestElement) {
            log.info("WHreq on: [" + element.getAlias() + "] do [" + element.getAction().value() + "]");
            System.out.println(element.getAction().value());
        }

        System.out.println("Elements: " + whitelistrequestElement.size());
        WhitelistResponse response = new WhitelistResponse();
        response.getReturn().add(BigInteger.valueOf(whitelistrequestElement.size()));
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
        try {
            auth.check(context, this.request);
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
        
        // analyze request
        List<String> alias = request.getAlias();
        if (alias!=null){
            log.info("Alias is not null; size: " + alias.size());
            for(String al:alias){
                log.info("AliasFromList: " + al);
            }
        } else {
            log.info("Alias is empty");
        }
        
        // build basic element response
        ContactListElementType elem = new ContactListElementType();
        elem.setAlias("john");
        elem.setContactlistStatus(EnabledDisabledType.ENABLED);
        elem.setPresenceStatus(UserPresenceStatusType.DND);
        elem.setUserid(BigInteger.ZERO);
        elem.setUsersip("john@voip.com");
        elem.setWhitelistStatus(UserWhitelistStatusType.IN);
                
        // now wrapping container 
        ContactlistGetResponse response = new ContactlistGetResponse();
        response.getContactlistEntry().add(elem);
        
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
        try {
            auth.check(context, this.request);
        } catch (CertificateException ex) {
            log.info("User check failed", ex);
            throw new CertificateException("You are not authorized, go away!");
        }
        
        // analyze request
        List<ContactlistChangeRequestElementType> elems = request.getContactlistChangeRequestElement();
        if (elems!=null){
            log.info("elems is not null; size: " + elems.size());
            for(ContactlistChangeRequestElementType elem : elems){
                if (elem==null) continue;
                log.info("ActionRequest: on [" + elem.getAlias() + "] do: [" + elem.getAction().value() + "]");
            }
        } else {
            log.info("elems is empty");
        }
        
        ContactlistChangeResponse response = new ContactlistChangeResponse();
        response.getReturn().add(BigInteger.ONE);
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
}
