/*
 * Copyright 2005-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phoenix.soap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;

import com.phoenix.service.HumanResourceService;
import com.phoenix.soap.beans.HolidayRequest;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.net.ssl.X509TrustManager;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.hibernate.ejb.HibernateEntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Document;
import org.w3c.dom.Text;

/**
 * This endpoint handles holiday requests. It uses a combination of JDOM and XPath to extract interesting pieces of XML
 * from the incoming message, and invoked the injected {@link HumanResourceService} with those.
 *
 * @author Arjen Poutsma
 */
@Endpoint
public class HolidayEndpoint {
    private static final Logger log = LoggerFactory.getLogger(HolidayEndpoint.class);
    
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
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required=true)
    private HttpServletRequest request;
    
    @Autowired(required=true)
    private X509TrustManager trustManager;
    
    @Autowired
    public HolidayEndpoint(HumanResourceService humanResourceService) throws JDOMException {
        this.humanResourceService = humanResourceService;
        Namespace namespace = Namespace.getNamespace("hr", NAMESPACE_URI);
        XPathFactory xPathFactory = XPathFactory.instance();
        startDateExpression = xPathFactory.compile("//hr:StartDate", Filters.element(), null, namespace);
        endDateExpression = xPathFactory.compile("//hr:EndDate", Filters.element(), null, namespace);
        firstNameExpression = xPathFactory.compile("//hr:FirstName", Filters.element(), null, namespace);
        lastNameExpression = xPathFactory.compile("//hr:LastName", Filters.element(), null, namespace);
    }
    
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "HolidayRequest")
    @ResponsePayload 
    public org.w3c.dom.Element handleHolidayRequest(@RequestPayload HolidayRequest holidayRequest, MessageContext context) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Shit happened? ").append(NL);
        
        try {
            sb.append("Trust manager: ").append(trustManager==null?"NULL":"not null").append(NL);
            sb.append("MessageContext: ").append(context == null ? "NULL" : "not null").append(";").append(NL);
            sb.append("Ctxt: ").append(context.toString()).append(";").append(NL);
            String[] propertyNames = context.getPropertyNames();
            for (String prop : propertyNames) {
                sb.append("\tp: ").append(prop).append(NL);
            }

            sb.append("ServletRequest: ").append(this.request == null ? "NULL" : "not null").append(";").append(NL);
            sb.append("EntityManager: ").append(this.em==null ? "NULL" : "not null").append(NL);
            sb.append("SessionFactory: ").append(this.sessionFactory==null ? "NULL" : "not null").append(NL);
            if (sessionFactory!=null){
                Session sess = sessionFactory.openSession();
                sb.append("HibernateSession: ").append(sess==null ? "NULL" : "not null").append(NL);
                if (sess!=null){
                    sb.append("isConnected: ").append(sess.isConnected() ? "connected" : "NOT connected").append(NL);
                    sb.append("isOpen: ").append(sess.isOpen() ? "Yes" : "NO").append(NL);
                }
            }
            
            // new way how to get hibernate session
            HibernateEntityManager hem = em.unwrap(HibernateEntityManager.class);
            Session hibsess = hem.getSession();
            sb.append("HibSession: ").append(hibsess==null ? "NULL" : "not null").append(NL);
            if (hibsess!=null){
                sb.append("isConnected: ").append(hibsess.isConnected() ? "connected" : "NOT connected").append(NL);
                sb.append("isOpen: ").append(hibsess.isOpen() ? "Yes" : "NO").append(NL);
            }
            
            sb.append("req1: ").append(request.toString()).append(";").append(NL);
            sb.append("Method: ").append(request.getMethod()).append(";").append(NL);
            sb.append("RequestURI: ").append(request.getRequestURI()).append(";").append(NL);
            sb.append("isSecure: ").append(request.isSecure()).append(NL);
            sb.append("SessionID: ").append(request.getSession().getId()).append(NL);
            
            try {
                Enumeration headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String helem = (String) headerNames.nextElement();
                    sb.append("\tHEl: ").append(helem).append("; ").append(NL);
                }

                String cipherSuite = (String) request.getAttribute("javax.servlet.request.cipher_suite");
                if (cipherSuite != null) {
                    sb.append("CryptoSuite is not null;").append(NL);
                    X509Certificate certChain[] = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
                    if (certChain != null) {
                        sb.append("Certificates in chain: ").append(certChain.length).append(NL);
                        for (int i = 0; i < certChain.length; i++) {
                            //sb.append("Client Certificate [").append(i).append("] = ").append(certChain[i].toString()).append(";").append(NL);
                             sb.append("CERT: ").append(i).append(NL);
                             sb.append("Cert_IssuerDN: ").append(certChain[i].getIssuerDN()).append(NL);
                             sb.append("Cert_Serial: ").append(certChain[i].getSerialNumber()).append(NL);
                             sb.append("Cert_SubjDN: ").append(certChain[i].getSubjectDN()).append(NL);
                        }
                        
                        sb.append("Cert chain verification: ").append(NL);
                        try {
                            this.trustManager.checkClientTrusted(certChain, "auth");
                            sb.append("certificate check passed...").append(NL);
                        } catch(Exception ex){
                            sb.append("certificate check failed!!!!").append(NL);
                            sb.append(ex.getMessage());
                            ex.printStackTrace();
                        }
                        
                    } else {
                        sb.append("CertChain is null").append(NL);
                    }
                } else {
                    sb.append("CipherSuite is null; \r\n");
                }
            } catch (Exception e) {
                sb.append("Exc: ").append(e.getMessage());
            }

            HttpServletRequest request2 = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            sb.append("req2: ").append(request2 == null ? "NULL" : "not null");
            sb.append("req2: ").append(request2.toString()).append("; ");
            sb.append("Session2ID: ").append(request2.getSession().getId()).append(NL);
            sb.append("Method: ").append(request2.getMethod()).append(";");
            
            
            //TransportContext ctx = TransportContextHolder.getTransportContext();
            //ctx.getConnection().
            //HttpServletRequest req = ((HttpServletConnection ) ctx.getConnection()).getHttpServletRequest();

            // Do something request
//            Date startDate = parseDate(startDateExpression, holidayRequest);
//            Date endDate = parseDate(endDateExpression, holidayRequest);
//            String name = firstNameExpression.evaluateFirst(holidayRequest).getText() + " " + lastNameExpression.evaluateFirst(holidayRequest).getText();
//            humanResourceService.bookHoliday(startDate, endDate, name);


        } catch (Exception e) {
            sb.append("rootExc: ").append(e.getMessage());
        }

        //return sb.toString();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();
        org.w3c.dom.Element responseElement = document.createElementNS(NAMESPACE_URI, "fuck");
        Text responseText = document.createTextNode(sb.toString());
        responseElement.appendChild(responseText);
        return responseElement;
    }

    private Date parseDate(XPathExpression<Element> expression, Element element) throws ParseException {
        Element result = expression.evaluateFirst(element);
        if (result != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(result.getText());
        } else {
            throw new IllegalArgumentException("Could not evaluate [" + expression + "] on [" + element + "]");
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
