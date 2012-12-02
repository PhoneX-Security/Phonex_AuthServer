/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap.old;

import java.security.cert.X509Certificate;

/**
 *
 * @author ph4r05
 */
//@WebService(name="HelloWsImpl")
public class HelloWsImpl { // extends SpringBeanAutowiringSupport  {
    //@Context
    javax.servlet.http.HttpServletRequest request;
    /*@Resource
    WebServiceContext wsCtxt;*/

    //@WebMethod(operationName = "sayHello")
    public String sayHello(String name) { //, @Context HttpServletRequest inRequest) {
        StringBuilder sb = new StringBuilder();
        try {
            //      MessageContext mc = this.wsContext.getMessageContext();
            //        HttpServletRequest req = (HttpServletRequest)mc.get("javax.xml.ws.servlet.request");
            //        return req.getRemoteAddr();
//            try{
//                MessageContext context = MessageContext.getCurrentContext();
//                HttpServletRequest req = (HttpServletRequest) context.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
//                sb.append("AXIS request: ").append(req.toString());
//            } catch(Exception ex){
//                sb.append("; Shit happened: ").append(ex.getMessage());
//            }
//            
            
            sb.append("; request:").append(request==null?"NULL":"not null");
            //sb.append("; wsCtxt: ").append(wsCtxt==null?"NULL":"Not null");
            
            sb.append("; Method: ").append(request.getMethod());
            sb.append("; URI: ").append(request.getRequestURI());
            
            String ipAddress  = request.getHeader("X-FORWARDED-FOR");    
            if (ipAddress!=null){
                sb.append("; X-Forw: ").append(ipAddress);
            } 

            X509Certificate certs[] = (X509Certificate[]) request.getServletContext().getAttribute("javax.servlet.request.X509Certificate");
            for (X509Certificate cert : certs) {
                sb.append("Cert: ").append(cert.getIssuerDN());
            }

            if (name != null) {
                sb.append("; Input: ").append(name);
            }

            return sb.toString();
        } catch (Exception ex) {
            sb.append("Shit happened; ").append(ex.getMessage());
            return sb.toString();
        }
    }
}
