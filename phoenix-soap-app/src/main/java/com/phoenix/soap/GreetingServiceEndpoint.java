package com.phoenix.soap;

import java.security.cert.X509Certificate;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.core.Context;

import org.springframework.stereotype.Service;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;


//@Component
@Service("greetingServiceEndpoint")
@WebService(serviceName = "GreetingService")
public class GreetingServiceEndpoint extends SpringBeanAutowiringSupport {

    //@Autowired
    @Resource(name="greetingService")
    private GreetingService greetingService;
    
    @Context
    javax.servlet.http.HttpServletRequest request;
    
    private int initialized=0;

    @PostConstruct
    public void init() {
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
        initialized=1;
    }

    @WebMethod
    public String sayHello(String name) {
        StringBuilder sb = new StringBuilder();
        
        try {
            sb.append("Start; ");
            sb.append("initialized: ").append(initialized).append("\n");
            sb.append("; GreetingSvc: ").append(greetingService==null?"NULL":"not null").append("\n");
            sb.append("; request:").append(request==null?"NULL":"not null").append("\n");
            sb.append(greetingService.sayHello()).append("\n");
            
            //sb.append("; wsCtxt: ").append(wsCtxt==null?"NULL":"Not null");
            
            sb.append("; Method: ").append(request.getMethod()).append("\n");
            sb.append("; URI: ").append(request.getRequestURI()).append("\n");
            
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
