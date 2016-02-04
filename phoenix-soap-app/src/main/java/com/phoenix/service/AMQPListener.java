/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.accounts.AccountManager;
import com.phoenix.service.push.*;
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
 *
 * @author dusanklinec
 */
@Service
@Scope(value = "singleton")
public class AMQPListener extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(AMQPListener.class);
    private static final String QUEUE_SERVER = "phone-x.net";
    private static final String QUEUE_VHOST = "/phonex";
    private static final String QUEUE_USER_NAME = "appServer";
    private static final String QUEUE_USERS_NAME = "users";
    private static final String QUEUE_XMPP_NAME = "xmpp";

    /**
     * Timeous sync in milliseconds.
     */
    private static final long TIMEOUT_LISTEN = 1000 * 5;
    private long lastSync = 0;
    private volatile boolean running = true;
    private volatile boolean connectionOK = false;

    private ConnectionFactory factory;
    private Connection connection;
    private Channel channel;

    @Autowired
    private PhoenixDataService dataService;

    @Autowired
    private JiveGlobals jiveGlobals;

    @Autowired
    private AccountManager accountMgr;

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

        log.info(String.format("AMPQ starting: %s", this));
        this.start();
    }

    @PreDestroy
    public synchronized void deinit() {
        log.info(String.format("Deinitializing AMQP listener, this=%s", this));
        setRunning(false);
    }

    private void ensureConnected() {
        boolean connected = false;
        try {
            connected = connection != null && connection.isOpen() && channel != null && channel.isOpen();
            ;
        } catch (Exception e) {
            log.error("Cannot determine if connected or not", e);
        }

        if (connected) {
            return;
        }

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_USERS_NAME, true, false, false, null);
            channel.queueDeclare(QUEUE_XMPP_NAME, true, false, false, null);
            connectionOK = true;
            log.info(String.format("AMQP connected, listener=%s, connection=%s, channel=%s", this, connection, channel));

        } catch (Exception e) {
            log.error("Cannot initialize messaging queue...");
            connectionOK = false;
        }
    }

    private void ensureClosed() {
        // Close channel.
        if (channel != null) {
            try {
                log.info(String.format("Going to close channel: %s", channel));

                channel.close();
                channel = null;
            } catch (Exception ex) {
                log.error("Could not close the channel", ex);
            }
        }

        // Close connection.
        if (connection != null) {
            try {
                log.info(String.format("Going to close connection: %s", connection));

                connection.close();
                connection = null;
            } catch (Exception ex) {
                log.error("Could not close the connection", ex);
            }
        }
    }

    @Transactional
    public void doTheJob() {
        try {
            this.ensureConnected();

            final QueueingConsumer consumer = new QueueingConsumer(channel);
            final String consumerTag = consumer.getConsumerTag();
            log.info(String.format("Consumer tag: %s", consumerTag));

            final String serverConsumerTag = channel.basicConsume(QUEUE_USERS_NAME, false, consumer);
            log.info(String.format("Server consumer tag: %s", serverConsumerTag));

            while (this.running) {
                final QueueingConsumer.Delivery delivery = consumer.nextDelivery(2000);
                if (delivery == null) {
                    continue;
                }

                final long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                log.info(String.format("Valid message received, tag=%d", deliveryTag));

                // Ack message here.
                channel.basicAck(deliveryTag, false);
                try {
                    processNewMessage(delivery);
                } catch(Exception ex){
                    log.error("Exception in message processing", ex);
                }
            }

            // Close channel.
            ensureClosed();

        } catch (Exception ex) {
            log.warn("Problem occurred during property sync", ex);
        }
    }

    @Override
    public void run() {
        this.running = true;
        while (this.running) {
            doTheJob();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }

        ensureClosed();
        log.info(String.format("AMPQListener thread ended. Running: %s, this: %s", running, this));
    }

    /**
     * Process new message coming from users amqp queue.
     * @param delivery
     */
    private void processNewMessage(QueueingConsumer.Delivery delivery) {
        try {
            final String message = new String(delivery.getBody());
            log.info("Message received: " + message);

            final JSONObject obj = new JSONObject(message);
            final String job = obj.getString("job");
            if ("ContactListUpdated".equalsIgnoreCase(job)) {
                final JSONObject data = obj.getJSONObject("data");
                final String userName = data.getString("username");

                log.info("User contact list updated: " + userName);
                dataService.resyncRoster(userName);

                // Contactlist refresh push notification to XMPP.
                pushClistSync(userName);

            } else if ("versionUpdated".equalsIgnoreCase(job)) {
                // TODO: TBD.

            } else if ("licenseUpdated".equalsIgnoreCase(job)){
                final JSONObject data = obj.getJSONObject("data");
                final String userName = data.getString("username");
                log.info("License updated: " + userName);

                // License check refresh push notification to XMPP.
                pushLicenseCheck(userName);

            } else if ("logout".equalsIgnoreCase(job)){
                final JSONObject data = obj.getJSONObject("data");
                final String userName = data.getString("username");
                log.info("Logout user: " + userName);

                dataService.deleteSensitiveDataForUser(userName);
                pushLogout(userName);

            } else if ("offlineMessage".equalsIgnoreCase(job)){
                final JSONObject data = obj.getJSONObject("data");
                final String userName = data.getString("to");
                log.info("Offline message, user: " + userName);

                accountMgr.onNewOfflineMessage(data);

            } else {
                log.info("Unrecognized job: " + job);
            }

        } catch (Exception ex) {
            log.warn("Exception in processing a new message", ex);
        }
    }

    /**
     * Sends push APN request to the XMPP server.
     * Will send request for a push message for this event to the XMPP queue for processing.
     *
     * @param from
     * @param to
     * @param timestamp
     * @throws JSONException
     * @throws IOException
     */
    public void pushNewOfflineMessage(String from, String to, long timestamp, String msgType) throws JSONException, IOException {
        JSONObject jsonPush = this.buildNewOfflineMessage(from, to, timestamp, msgType);

        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message for new certificate event.
     * Will send request for a push message for this event to the XMPP queue for processing.
     *
     * @param user
     * @param certNotBefore
     * @param certHashPrefix
     * @throws JSONException
     * @throws IOException
     */
    public void pushNewCertificate(String user, long certNotBefore, String certHashPrefix) throws JSONException, IOException {
        JSONObject jsonPush = this.buildLoginPushMsg(user, certNotBefore, certHashPrefix);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Pushes notification to sync contact list.
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushClistSync(String user) throws JSONException, IOException {
        JSONObject jsonPush = this.buildClistSyncNotification(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message for DH key used event.
     * Notifies user about DH Key download, he may be interested in keeping some amount of DHkeys on the server.
     *
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushDHKeyUsed(String user)  throws JSONException, IOException {
        JSONObject jsonPush = this.buildDHKeyUsedhMsg(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message for license check event.
     * Sends user a push message notifying him something has changed with his license so he should check it.
     * License also might got expired.
     *
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushLicenseCheck(String user)  throws JSONException, IOException {
        JSONObject jsonPush = this.buildLicenseCheckMsg(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message to logout user from device.
     * Sends user a push message notifying him something has changed with his license so he should check it.
     * License also might got expired.
     *
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushLogout(String user)  throws JSONException, IOException {
        JSONObject jsonPush = this.buildLogoutMsg(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message for application version check.
     * Sends user a push message notifying him something has changed with his license so he should check it.
     * License also might got expired.
     *
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushVersionCheck(String user)  throws JSONException, IOException {
        JSONObject jsonPush = this.buildVersionCheckMsg(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Broadcasts new file notification.
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushNewFile(String user) throws JSONException, IOException {
        JSONObject jsonPush = this.buildNewFileNotification(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message for new certificate event.
     * Will send request for a push message for this event to the XMPP queue for processing.
     *
     * @param user
     * @param certNotBefore
     * @param certHashPrefix
     * @throws JSONException
     * @throws IOException
     */
    public void pushContactCertUpdate(String user, String destUser, long certNotBefore, String certHashPrefix) throws JSONException, IOException {
        JSONObject jsonPush = this.buildContactCertUpdateMsg(user);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    /**
     * Send push message for a pairing request check.
     *
     * @param user
     * @throws JSONException
     * @throws IOException
     */
    public void pushPairingRequestCheck(String user, long tstamp)  throws JSONException, IOException {
        JSONObject jsonPush = this.buildPairingRequestCheckMsg(user, tstamp);
        final String jsonPushString = jsonPush.toString();
        this.xmppPublish(jsonPushString.getBytes("UTF-8"));
        log.info("Push message sent: " + jsonPushString);
    }

    public JSONObject buildClistSyncNotification(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final ClistSyncEventMessage part = new ClistSyncEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildNewFileNotification(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final NewFileEventMessage part = new NewFileEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildLoginPushMsg(String user, long certNotBefore, String certHashPrefix) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final NewCertEventMessage part = new NewCertEventMessage(tstamp, certNotBefore, certHashPrefix);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildDHKeyUsedhMsg(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final DHKeyUsedEventMessage part = new DHKeyUsedEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildLicenseCheckMsg(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final LicenseCheckEventMessage part = new LicenseCheckEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildLogoutMsg(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final LogoutEventMessage part = new LogoutEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildVersionCheckMsg(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final VersionCheckEventMessage part = new VersionCheckEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildContactCertUpdateMsg(String user) throws JSONException {
        final long tstamp = System.currentTimeMillis();
        final ContactCertUpdateEventMessage part = new ContactCertUpdateEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    public JSONObject buildPairingRequestCheckMsg(String user, long tstamp) throws JSONException {
        final PairingRequestCheckEventMessage part = new PairingRequestCheckEventMessage(tstamp);
        final SimplePushMessage msg = new SimplePushMessage(user, tstamp);
        msg.addPart(part);

        return msg.getJson();
    }

    /**
     * Generates pushRequest message for XMPP server.
     *
     * {"action"    :"pushReq",
     *  "user"      :"test1@phone-x.net",
     *  "pushreq"   :[
     *    {"push":"newEvent",       "target": "test-internal3@phone-x.net", "tstamp": 1446487435000},
     *    {"push":"newAttention",   "target": "test-internal3@phone-x.net"},
     *    {"push":"newMessage",     "target": "test-internal3@phone-x.net"},
     *    {"push":"newMissedCall",  "target": "test-internal3@phone-x.net"},
     *    {"push":"newCall",        "target": "test-internal3@phone-x.net", "key":"af45bed", "expire":180000,}
     *  ]
     *  }
     *
     * @param from
     * @param to
     * @param timestamp
     * @return
     * @throws JSONException
     */
    public JSONObject buildNewOfflineMessage(String from, String to, long timestamp, String msgType) throws JSONException {
        final JSONObject jObj = new JSONObject();
        jObj.put("action", "pushReq");
        jObj.put("user", from);

        final JSONArray pushreq = new JSONArray();
        final JSONObject pushMsg = new JSONObject();
        pushMsg.put("target", to);
        pushMsg.put("tstamp", timestamp);

        if ("m".equals(msgType)){
            pushMsg.put("push", "newMessageOffline");
            pushMsg.put("mtype", msgType);
        } else if ("c".equals(msgType)){
            pushMsg.put("push", "newMissedCallOffline");
            pushMsg.put("mtype", msgType);
        } else {
            pushMsg.put("push", "newOfflineMsg");
            pushMsg.put("mtype", msgType);
        }
        pushreq.put(pushMsg);

        jObj.put("pushreq", pushreq);
        return jObj;
    }

    /**
     * Send given message to XMPP queue.
     * @param body
     * @throws IOException
     */
    public void xmppPublish(byte[] body) throws IOException {
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
