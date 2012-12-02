/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import com.phoenix.soap.beans.WhitelistRequest;
import com.phoenix.soap.beans.WhitelistRequestElementType;
import com.phoenix.soap.beans.WhitelistResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;

import com.phoenix.service.HumanResourceService;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.X509TrustManager;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Document;
import org.w3c.dom.Text;

/**
 *
 * @author ph4r05
 */
@Endpoint
public class PhoenixEndpoint {

    private static final String NAMESPACE_URI = "http://mycompany.com/hr/schemas";
    private XPathExpression<Element> startDateExpression;
    private XPathExpression<Element> endDateExpression;
    private XPathExpression<Element> firstNameExpression;
    private XPathExpression<Element> lastNameExpression;
    private HumanResourceService humanResourceService;
    private final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private static final String NL = "\r\n";
    
    @Autowired(required = true)
    private HttpServletRequest request;
    
    @Autowired(required = true)
    private X509TrustManager trustManager;

    @Autowired
    public PhoenixEndpoint(HumanResourceService humanResourceService) throws JDOMException {
        this.humanResourceService = humanResourceService;
    }

    @PayloadRoot(localPart = "whitelistRequest", namespace = NAMESPACE_URI)
    @ResponsePayload
    public WhitelistResponse doit(@RequestPayload WhitelistRequest request, MessageContext context) {
        List<WhitelistRequestElementType> whitelistrequestElement =
                request.getWhitelistrequestElement();

        for (WhitelistRequestElementType element : whitelistrequestElement) {
            System.out.println(element.getAction().value());
        }

        System.out.println("Elements: " + whitelistrequestElement.size());
        WhitelistResponse response = new WhitelistResponse();
        response.getReturn().add(BigInteger.valueOf(whitelistrequestElement.size()));
        return response;
    }

}
