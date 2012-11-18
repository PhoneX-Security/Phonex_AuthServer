/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.soap;

/**
 *
 * @author ph4r05
 */

import org.springframework.stereotype.Service;

@Service("greetingService")
public class GreetingService {

 public String sayHello() {
  return "Hello from Greeting Service";
 }
 
}
