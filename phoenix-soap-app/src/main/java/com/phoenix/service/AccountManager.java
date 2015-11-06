package com.phoenix.service;

import com.phoenix.db.Contactlist;
import com.phoenix.db.TrialEventLog;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.soap.beans.AccountSettingsUpdateV1Request;
import com.phoenix.soap.beans.AccountSettingsUpdateV1Response;
import com.phoenix.utils.MiscUtils;
import com.phoenix.utils.PasswordGenerator;
import com.phoenix.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

/**
 * Manager for account settings logic.
 *
 * Created by dusanklinec on 25.09.15.
 */
@Service
@Repository
public class AccountManager {
    private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

    public static final String JSON_SETTINGS_UPDATE = "settingsUpdate";
    public static final String JSON_SETTINGS_LOGIN = "loggedIn";
    public static final String JSON_SETTINGS_MUTE_PUSH = "mutePush";

    public static final String AUTH_TURN_PASSWD_KEY = "turnPwd";
    public static final String JSON_SETTINGS_LOGOUT_DATE = "logoutDate";

    public static final String AMQP_OFFLINE_FROM = "from";
    public static final String AMQP_OFFLINE_TO = "to";
    public static final String AMQP_OFFLINE_MSG_TYPE = "msgType";
    public static final String AMQP_OFFLINE_TIMESTAMP_SECONDS = "timestampSeconds";

    @Autowired
    private PhoenixDataService dataService;

    @Autowired
    private AMQPListener amqpListener;

    @PostConstruct
    public synchronized void init() {
        log.info("Initializing AccountManager");
    }

    @PreDestroy
    public synchronized void deinit(){
        log.info("Shutting down AccountManager");
    }

    /**
     * Main entry point for updating user settings.
     * Common settings update request body:
     * {"settingsUpdate":{
     *     "loggedIn": 0,
     *     "mutePush": 1446476185000,
     *
     * }}
     *
     * @param caller
     * @param request
     * @param response
     * @throws JSONException
     */
    public void processSettingsUpdateRequest(Subscriber caller, AccountSettingsUpdateV1Request request, AccountSettingsUpdateV1Response response) throws JSONException {
        final String reqBody = request.getRequestBody();
        final JSONObject jReq = new JSONObject(reqBody);

        if (!jReq.has(JSON_SETTINGS_UPDATE)){
            log.warn("Unknown request, body not present");
            response.setErrText("Unknown request, body not present");
            response.setErrCode(-2);
            return;
        }

        final JSONObject settingReq = jReq.getJSONObject(JSON_SETTINGS_UPDATE);

        // "Is logged" flag in setting. Affects how
        if (settingReq.has(JSON_SETTINGS_LOGIN)){
            final boolean isLoggedIn = MiscUtils.getAsBoolean(settingReq, JSON_SETTINGS_LOGIN);
            if (isLoggedIn) {
                caller.setDateCurrentLogout(null);
            } else {
                caller.setDateCurrentLogout(Calendar.getInstance());
            }
        }

        // Mute until
        if (settingReq.has(JSON_SETTINGS_MUTE_PUSH)){
            final long muteUntil = MiscUtils.getAsLong(settingReq, JSON_SETTINGS_MUTE_PUSH);
            caller.setPrefMuteUntil(muteUntil);
        }

        dataService.persist(caller, true);

        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("result", 0);

        // Build JSON response.
        response.setErrCode(0);
        response.setResponseBody(jsonResponse.toString());
    }

    /**
     * Takes a JSON object and fills it with aux data contained in the Subscriber for authRequest and accountInfo requests.
     *
     * JSON will contain accountSettings, testingSettings, auxData, expiredPolicy, currentPolicy, evtlog.
     *
     * @param localUser
     * @param jsonAuxObj
     * @return
     */
    public JSONObject fillAuxJsonFromSubscriber(Subscriber localUser, JSONObject jsonAuxObj){

        // Account settings
        try {
            JSONObject accountSettingsRoot = new JSONObject();

            // App settings from database
            if (!StringUtils.isEmpty(localUser.getAccountSettings())){
                try {
                    final String accountSettingsStr = localUser.getAccountSettings();
                    accountSettingsRoot = new JSONObject(accountSettingsStr);

                    jsonAuxObj.put("accountSettings", accountSettingsRoot);

                } catch(Exception e){
                    log.error("Aux data exception: db account settings", e);
                }
            }

            // Augment with last logout and muteUntil fields which have separate fields.
            accountSettingsRoot.put(JSON_SETTINGS_LOGOUT_DATE,
                    localUser.getDateCurrentLogout() == null ? 0 : localUser.getDateCurrentLogout().getTime().getTime());

            accountSettingsRoot.put(JSON_SETTINGS_MUTE_PUSH,
                    localUser.getPrefMuteUntil());

            jsonAuxObj.put("accountSettings", accountSettingsRoot);

        } catch(Exception e){
            log.error("Aux data exception: account settings", e);
        }

        // Testing settings enable to set some client side parameters from the server (e.g., log level).
        if (!StringUtils.isEmpty(localUser.getTestingSettings())){
            try {
                final String testingSettingsStr = localUser.getTestingSettings();
                final JSONObject testingSettings = new JSONObject(testingSettingsStr);

                jsonAuxObj.put("testingSettings", testingSettings);

            } catch(Exception e){
                log.error("Aux data exception: testing settings", e);
            }
        }

        // AuxData
        if (!StringUtils.isEmpty(localUser.getAuxData())){
            try {
                final String auxDataStr = localUser.getAuxData();
                final JSONObject auxData = new JSONObject(auxDataStr);

                jsonAuxObj.put("auxData", auxData);

            } catch(Exception e){
                log.error("Aux data exception: aux data", e);
            }
        }

        // Expired policy
        if (!StringUtils.isEmpty(localUser.getUsagePolicyExpired())){
            try {
                final String policyStr = localUser.getUsagePolicyExpired();
                final JSONObject policy = new JSONObject(policyStr);

                jsonAuxObj.put("expiredPolicy", policy);

            } catch(Exception e){
                log.error("Aux data exception: expired policy", e);
            }
        }

        // Current policy
        if (!StringUtils.isEmpty(localUser.getUsagePolicyCurrent())){
            try {
                final String policyStr = localUser.getUsagePolicyCurrent();
                final JSONObject policy = new JSONObject(policyStr);

                jsonAuxObj.put("currentPolicy", policy);

            } catch(Exception e){
                log.error("Aux data exception: current policy", e);
            }
        }

        // AUXJson - trial event logs.
        if (localUser.getExpires() != null && localUser.getExpires().before(Calendar.getInstance())){
            try {
                final List<TrialEventLog> logs = dataService.getTrialEventLogs(localUser, null);
                final JSONObject jsonObj = dataService.eventLogToJson(logs, localUser);

                jsonAuxObj.put("evtlog", jsonObj);

            } catch(Exception e){
                log.error("Aux data exception: event log", e);
            }

        }

        return jsonAuxObj;
    }

    /**
     * Adds support contact elements to the object given.
     * @param s
     * @param objToSet
     */
    public void setSupportContacts(Subscriber s, JSONObject objToSet) throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put("phonex-support@phone-x.net");
        objToSet.put("support_contacts", arr);
    }

    /**
     * Sets TURN password to the auxJson field.
     * Fixes turn password if is empty or does not have HA1b form.
     *
     * @param localUser
     * @param jsonAuxObj
     */
    public void turnPasswordSetOrFix(Subscriber localUser, JSONObject jsonAuxObj){
        try {
            final String turnPasswd = localUser.getTurnPasswd();
            if (turnPasswd == null || turnPasswd.length() == 0) {
                final String turnPasswdGen = PasswordGenerator.genPassword(24, true);
                localUser.setTurnPasswd(turnPasswdGen);
                localUser.setTurnPasswdHa1b(MiscUtils.getHA1(PhoenixDataService.getSIP(localUser), localUser.getDomain(), turnPasswdGen));
                // TODO: send AMQP message to the TURN server so it updates auth credentials.
            }

            // Fix turn ha1b password if missing.
            final String turnHa1b = localUser.getTurnPasswdHa1b();
            if (turnHa1b == null || turnHa1b.length() == 0){
                localUser.setTurnPasswdHa1b(MiscUtils.getHA1(PhoenixDataService.getSIP(localUser), localUser.getDomain(), turnPasswd));
                // TODO: send AMQP message to the TURN server so it updates auth credentials.
            }

            // Base field - action/method of this message.
            jsonAuxObj.put(AUTH_TURN_PASSWD_KEY, localUser.getTurnPasswd());

        } catch(Throwable th){
            log.error("Exception in authcheck, turn password set.", th);
        }
    }

    /**
     * Returns true when the local user is muted for notifications.
     *
     * @param localUser
     */
    public boolean isLocalUserMutedForNotifications(Subscriber localUser){
        if (localUser == null){
            return true;
        }

        final long muteUntil = localUser.getPrefMuteUntil();
        return System.currentTimeMillis() < muteUntil;
    }

    /**
     * Called when a new AMQP message arrives indicating a new offline message was stored.
     *
     * MessageFormat:
     * {"job":"offlineMessage", "data":{"from":"test@phone-x.net","to":"test-internal3@phone-x.net","timestampSeconds":1446480038, "msgType":"2;4"}}
     *
     * We get data object here.
     *
     * @param data
     */
    @Transactional(readOnly = true)
    public void onNewOfflineMessage(final JSONObject data) throws JSONException, IOException {
        if (data == null
                || !data.has(AMQP_OFFLINE_FROM)
                || !data.has(AMQP_OFFLINE_TO)
                || !data.has(AMQP_OFFLINE_TIMESTAMP_SECONDS))
        {
            log.error("Offline message AMQP message is malformed: " + data);
            return;
        }

        String from = data.getString(AMQP_OFFLINE_FROM);
        String to = data.getString(AMQP_OFFLINE_TO);
        final String msgType = data.has(AMQP_OFFLINE_MSG_TYPE) ? data.getString(AMQP_OFFLINE_MSG_TYPE) : null;
        final long timestamp = MiscUtils.getAsLong(data, AMQP_OFFLINE_TIMESTAMP_SECONDS) * 1000l;

        from = from.replaceFirst("sips:", "");
        from = from.replaceFirst("sip:", "");

        to = to.replaceFirst("sips:", "");
        to = to.replaceFirst("sip:", "");

        try {
            // Load local user.
            final Subscriber toSubs = dataService.getLocalUser(to);
            if (toSubs == null){
                log.error("Unrecognized to user: " + to);
                return;
            }

            // Check if $to is not intentionally logged out.
            if (toSubs.getDateCurrentLogout() != null){
                log.info(String.format("User %s is logged out, new offline from %s", to, from));
                return;
            }

            // Check if $to has not blocked messages.
            if (isLocalUserMutedForNotifications(toSubs)){
                log.info(String.format("User %s blocked from updates, new offline from %s", to, from));
                return;
            }

            // Check if $to has $from in contactlist.
            final Contactlist toContactFrom = dataService.getContactlistForSubscriber(toSubs, from);
            if (toContactFrom == null){
                log.info(String.format("User %s not found in %s's contact list", from, to));
                return;
            }

            // Check if $from is not muted for notifications.
            if (timestamp < toContactFrom.getPrefMuteUntil()){
                log.info(String.format("User %s is muted %s's contact list until %d", from, to, toContactFrom.getPrefMuteUntil()));
            }

            // Send AMQP message to XMPP server, with pushReq.
            amqpListener.pushNewOfflineMessage(from, to, timestamp);

        }catch (Exception e){
            log.error("Exception when processing new offline message event", e);
        }
    }
}
