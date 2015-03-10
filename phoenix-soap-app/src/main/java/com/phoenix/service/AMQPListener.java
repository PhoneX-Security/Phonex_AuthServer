/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.utils.JiveGlobals;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AMPQ listener.
 * @author dusanklinec
 */
@Service
@Scope(value = "singleton")
public class AMQPListener extends BackgroundThreadService {
   private static final Logger log = LoggerFactory.getLogger(AMQPListener.class);
   private static final String QUEUE_SERVER     = "phone-x.net";
   private static final String QUEUE_VHOST      = "/phonex"; 
   private static final String QUEUE_USER_NAME  = "appServer";
   private static final String QUEUE_USERS_NAME = "users"; 
   private static final String QUEUE_XMPP_NAME  = "xmpp"; 
   
   /**
    * Timeous sync in milliseconds.
    */
   private static final long TIMEOUT_LISTEN = 1000*5;
   private long lastSync = 0;
   private volatile boolean running=true;
   private volatile boolean connectionOK=false;
   
   private ConnectionFactory factory;
   private Connection connection;
   private Channel channel;
   
   @Autowired
   private PhoenixDataService dataService;
   
   @Autowired
   private JiveGlobals jiveGlobals;
   
   /**
    * Initializes internal running thread.
    */
   @PostConstruct
   public synchronized void init() {
       initThread(this, "AMQPListener");
       
       factory = new ConnectionFactory();
       factory.setHost(QUEUE_SERVER);
       factory.setUsername(QUEUE_USER_NAME);
       factory.setPassword("ug2ooWahchaeghoh");
       factory.setVirtualHost(QUEUE_VHOST);
       
       log.info("AMPQ connected & declared");
       this.start();
   }
   
   @PreDestroy
   public synchronized void deinit(){
       log.info("Deinitializing AMQP listener");
       setRunning(false);
   }
   
   private void ensureConnected(){
       boolean connected = false;
       try {
           connected = connection != null && connection.isOpen();
       } catch(Exception e){
           log.error("Cannot determine if connected or not", e);
       }
       
       if (connected){
           return;
       }
       
       try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_USERS_NAME, true, false, false, null);
            channel.queueDeclare(QUEUE_XMPP_NAME, true, false, false, null);
            connectionOK = true;
       } catch(Exception e){
           log.error("Cannot initialize messaging queue...");
           connectionOK = false;
       }    
   }

   @Transactional
   public void doTheJob(){
       try {
           this.ensureConnected();
           
           final QueueingConsumer consumer = new QueueingConsumer(channel);
           channel.basicConsume(QUEUE_USERS_NAME, true, consumer);
           while (this.running) {
              final QueueingConsumer.Delivery delivery = consumer.nextDelivery(2000);
              if (delivery == null){
                  continue;
              }
              
              processNewMessage(delivery);
           }
           
           // Close channel.
           if (channel != null){
               try {
                   channel.close();
               } catch(Exception ex){
                   log.error("Could not close the channel", ex);
               }
           }
           
           // Close connection.
           if (connection != null){
               try {
                   connection.close();
               } catch(Exception ex){
                   log.error("Could not close the connection", ex);
               }
           }
           
        } catch(Exception ex){
            log.warn("Problem occurred during property sync", ex);
        }
   }
   
    @Override
    public void run() {
        while (this.running) {
            long cmilli = System.currentTimeMillis();
            if ((cmilli-lastSync) > TIMEOUT_LISTEN){
                lastSync = cmilli;
                doTheJob();
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }
        log.info("AMPQListener thread ended.");
    }

    private void processNewMessage(QueueingConsumer.Delivery delivery){
        try {
            final String message = new String(delivery.getBody());
            log.info("Message received: " + message);

            final JSONObject obj = new JSONObject(message);
            final String     job = obj.getString("job");
            if ("ContactListUpdated".equalsIgnoreCase(job)){
                final JSONObject data = obj.getJSONObject("data");
                final String userName = data.getString("username");
            
                log.info("User contact list updated: " + userName);
                dataService.resyncRoster(userName);
                
                // Contactlist refresh push notification to XMPP.
                JSONObject jsonPush = this.buildClistSyncNotification(userName);
                final String jsonPushString = jsonPush.toString();
                this.xmppPublish(jsonPushString.getBytes("UTF-8"));
                log.info("Push message sent: " + jsonPushString);
                
            } else {
                log.info("Unrecognized job: " + job);
            }
            
        } catch(Exception ex){
            log.warn("Exception in processing a new message");
        }
    }
    
    public JSONObject buildClistSyncNotification(String user) throws JSONException{
        JSONObject obj = new JSONObject();
	obj.put("action", "push");
	obj.put("user", user);
        obj.put("msg", "clistSync");
        return obj;
    }
    
    public void xmppPublish(byte[] body) throws IOException{
        channel.basicPublish("", QUEUE_XMPP_NAME, null, body);
    }
    
    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
    
}
