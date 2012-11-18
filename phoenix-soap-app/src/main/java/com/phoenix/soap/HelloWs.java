/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;



/**
 *
 * @author ph4r05
 */
//@WebService(name="helloWs")
//@SOAPBinding(style = Style.RPC)
public class HelloWs {
    /**
     * Web service operation
     */
    //@WebMethod(operationName = "test")
    public String test(@WebParam(name = "test") String test) {
        //TODO write your implementation code here:
        
        
        return test;
    }
}