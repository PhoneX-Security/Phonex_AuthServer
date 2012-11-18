package com.javacodegeeks.jaxwsspring.endpoints;

import javax.jws.WebMethod;
import javax.jws.WebService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.phoenix.soap.GreetingService;
import javax.annotation.PostConstruct;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

@Service("greetingServiceEndpoint")
@WebService(serviceName = "GreetingService")
public class GreetingServiceEndpoint {

    @Autowired
    private GreetingService greetingService;

    @PostConstruct
    public void init() {
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    }

    @WebMethod
    public String sayHello() {
        return greetingService.sayHello();
    }
}
