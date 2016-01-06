package com.phoenix.service;

import com.phoenix.db.*;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.geoip.GeoIpManager;
import com.phoenix.rest.RecoveryCodeResponse;
import com.phoenix.rest.VerifyRecoveryCodeResponse;
import com.phoenix.soap.beans.AccountSettingsUpdateV1Request;
import com.phoenix.soap.beans.AccountSettingsUpdateV1Response;
import com.phoenix.utils.MiscUtils;
import com.phoenix.utils.PasswordGenerator;
import com.phoenix.utils.StringUtils;
import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.lang.LocaleUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

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
    public static final String JSON_SETTINGS_RECOVERY_EMAIL = "recoveryEmail";

    public static final String AUTH_TURN_PASSWD_KEY = "turnPwd";
    public static final String JSON_SETTINGS_LOGOUT_DATE = "logoutDate";

    public static final String AMQP_OFFLINE_FROM = "from";
    public static final String AMQP_OFFLINE_TO = "to";
    public static final String AMQP_OFFLINE_MSG_TYPE = "msgType";
    public static final String AMQP_OFFLINE_TIMESTAMP_SECONDS = "timestampSeconds";

    public static final String RECOVERY_CODE_CHARSET = "123456789abcdefghijkmnopqrstuvwxz";
    public static final String PASSWORD_EMAIL_FROM = "system@phone-x.net";

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    private PhoenixDataService dataService;

    @Autowired(required = true)
    private EndpointAuth auth;

    @Autowired
    private StringsManager strings;

    @Autowired
    private MailSender mailSender;

    @Autowired
    private GeoIpManager geoIp;

    @Autowired
    private AMQPListener amqpListener;

    /**
     * Username+IP -> last recovery attempt in milliseconds, throttling recovery code requests.
     */
    private final LRUMap recoveryMap = new LRUMap(1024);

    /**
     * IP -> last recovery attempt in milliseconds, throttling recovery code requests.
     */
    private final LRUMap recoveryIpMap = new LRUMap(1024);
    private final Object recoveryMapsLock = new Object();

    /**
     * Username+IP -> last recovery attempt in milliseconds, throttling recovery code confirm requests.
     */
    private final LRUMap recoveryConfirmMap = new LRUMap(1024);

    /**
     * IP -> last recovery attempt in milliseconds, throttling recovery code confirm requests.
     */
    private final LRUMap recoveryIpConfirmMap = new LRUMap(1024);
    private final Object recoveryConfirmMapsLock = new Object();

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

        // Recovery email
        if (settingReq.has(JSON_SETTINGS_RECOVERY_EMAIL)){
            caller.setRecoveryEmail(settingReq.getString(JSON_SETTINGS_RECOVERY_EMAIL));
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

            accountSettingsRoot.put(JSON_SETTINGS_RECOVERY_EMAIL,
                    localUser.getRecoveryEmail());

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

    /**
     * Get recovery code request.
     * Creates new recovery code record and send an email with the recovery code.
     *
     * Throttling username / ip to 1 attempt per minute.
     *
     * @param sipUser
     * @param resource
     * @param appVersion
     * @param resp
     * @param request
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public void processGetRecoveryCodeRequest(String sipUser, String resource, String appVersion,
                                              RecoveryCodeResponse resp, HttpServletRequest request)
    {
        try {
            final String sip = StringUtils.takeMaxN(sipUser, 256);
            final String ip = auth.getIp(request);
            final String userIpKey = sip+";"+ip;
            final long now = System.currentTimeMillis();
            synchronized (recoveryMapsLock) {
                final Long userIpTime = (Long)recoveryMap.get(userIpKey);
                if (userIpTime != null && now-userIpTime < 1000*5){
                    resp.setStatusCode(-4);
                    resp.setStatusText("RecoveryTooOften");
                    return;
                }

                final Long ipTime = (Long) recoveryIpMap.get(ip);
                if (ipTime != null && now-ipTime < 1000*5){
                    resp.setStatusCode(-5);
                    resp.setStatusText("RecoveryTooOftenIp");
                    return;
                }

                recoveryMap.put(userIpKey, now);
                recoveryIpMap.put(ip, now);
            }

            // Load user, if not found, mask as if no email was set.
            final Subscriber caller = this.dataService.getLocalUser(sip);
            if (caller == null){
                resp.setStatusCode(-3);
                resp.setStatusText("EmptyRecoveryMail");
                return;
            }

            // Empty recovery email?
            if (StringUtils.isEmpty(caller.getRecoveryEmail())){
                resp.setStatusCode(-3);
                resp.setStatusText("EmptyRecoveryMail");
                return;
            }

            final String newRecoveryCode = generateRecoveryCode();
            final RecoveryCode recCodeDb = new RecoveryCode();
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);

            recCodeDb.setSubscriberSip(sip);
            recCodeDb.setOwner(caller);
            recCodeDb.setRecoveryEmail(caller.getRecoveryEmail());
            recCodeDb.setRequestIp(ip);
            recCodeDb.setDateCreated(new Date());
            recCodeDb.setDateValid(cal.getTime());
            recCodeDb.setCodeIsValid(true);
            recCodeDb.setRecoveryCode(newRecoveryCode);
            recCodeDb.setRequestAppVersion(StringUtils.takeMaxN(appVersion, 4096));
            recCodeDb.setResource(StringUtils.takeMaxN(resource, 32));
            dataService.persist(recCodeDb, true);

            // Send templated email to a given recovery mail.
            sendRecoveryMail(caller, recCodeDb, extractLocalesFromAppVersion(appVersion));

            resp.setValidTo(cal.getTime().getTime());
            resp.setStatusCode(0);
            resp.setStatusText("CodeSent");

        } catch(Exception e){
            log.error("Exception in processing verification code request.", e);
            resp.setStatusCode(-1);
        }
    }

    /**
     * Get recovery code request.
     *
     * @param sipUser
     * @param resource
     * @param appVersion
     * @param recoveryCode
     * @param resp
     * @param request
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public void processVerifyRecoveryCodeRequest(String sipUser, String resource, String appVersion,
                                                 String recoveryCode,
                                                 VerifyRecoveryCodeResponse resp, HttpServletRequest request)
    {
        try {
            final String sip = StringUtils.takeMaxN(sipUser, 256);
            final String ip = auth.getIp(request);
            final String userIpKey = sip+";"+ip;
            final long now = System.currentTimeMillis();

            // Throttling requests.
            synchronized (recoveryConfirmMapsLock) {
                final Long userIpTime = (Long)recoveryConfirmMap.get(userIpKey);
                if (userIpTime != null && now-userIpTime < 1000*5){
                    resp.setStatusCode(-4);
                    resp.setStatusText("RecoveryTooOften");
                    return;
                }

                final Long ipTime = (Long) recoveryIpConfirmMap.get(ip);
                if (ipTime != null && now-ipTime < 1000*5){
                    resp.setStatusCode(-5);
                    resp.setStatusText("RecoveryTooOftenIp");
                    return;
                }

                recoveryConfirmMap.put(userIpKey, now);
                recoveryIpConfirmMap.put(ip, now);
            }

            // Load user, if not found, mask as if no email was set.
            final Subscriber caller = this.dataService.getLocalUser(sip);
            if (caller == null){
                resp.setStatusCode(-3);
                resp.setStatusText("EmptyRecoveryMail");
                return;
            }

            // Empty recovery email?
            if (StringUtils.isEmpty(caller.getRecoveryEmail())){
                resp.setStatusCode(-3);
                resp.setStatusText("EmptyRecoveryMail");
                return;
            }

            // Load recovery.
            // Fetch newest auth state record.
            final TypedQuery<RecoveryCode> dbQuery = em.createQuery("SELECT rcode FROM RecoveryCode rcode " +
                    " WHERE rcode.owner=:owner " +
                    " AND rcode.recoveryCode=:recoveryCode " +
                    " AND rcode.dateValid >= NOW() " +
                    " AND rcode.codeIsValid=1 " +
                    " ORDER BY rcode.dateCreated DESC", RecoveryCode.class);
            dbQuery.setParameter("owner", caller);
            dbQuery.setParameter("recoveryCode", recoveryCode);

            final List<RecoveryCode> recoveryCodes = dbQuery.getResultList();
            if (recoveryCodes.isEmpty()){
                resp.setStatusCode(-10);
                resp.setStatusText("InvalidRecoveryCode");
                return;
            }

            final RecoveryCode codeDb = recoveryCodes.get(0);
            codeDb.setCodeIsValid(false);
            codeDb.setConfirmAppVersion(StringUtils.takeMaxN(appVersion, 4096));
            codeDb.setConfirmIp(ip);
            codeDb.setDateConfirmed(new Date());
            em.persist(codeDb);

            // Generate a new password.
            final String newPassword = PasswordGenerator.genPassword(16, false);
            final String ha1 = MiscUtils.getHA1(sip, newPassword);
            final String ha1b = MiscUtils.getHA1b(sip, newPassword);
            caller.setHa1(ha1);
            caller.setHa1b(ha1b);
            caller.setForcePasswordChange(true);
            em.persist(caller);

            resp.setNewPassword(newPassword);
            resp.setStatusCode(0);
            resp.setStatusText("OK");

            // Send templated email to a given recovery mail that password was reset from given IP address.
            sendPasswordRecoveredMail(caller, codeDb, extractLocalesFromAppVersion(appVersion));

        } catch(Exception e){
            log.error("Exception in processing verification of a recovery code.", e);
            resp.setStatusCode(-1);
        }
    }

    /**
     * Extracs available locales from app version.
     * @param appVersion
     * @return
     */
    public List<Locale> extractLocalesFromAppVersion(String appVersion){
        if (StringUtils.isEmpty(appVersion)){
            return Collections.emptyList();
        }

        try {
            final List<Locale> localesToReturn = new ArrayList<Locale>();
            final JSONObject appJson = new JSONObject(appVersion);
            if (!appJson.has("locales")){
                return localesToReturn;
            }

            final JSONArray locales = appJson.getJSONArray("locales");
            final int len = locales.length();
            if (len == 0){
                return localesToReturn;
            }

            for(int i=0; i<len; i++){
                try {
                    final String locString = locales.getString(i);
                    final Locale curLoc = LocaleUtils.toLocale(locString);
                    localesToReturn.add(curLoc);

                } catch(Exception ex){
                    log.error("Exception in subparse", ex);
                }
            }

            return localesToReturn;

        } catch(Exception e){
            log.error("Exception in parsing app version", e);
        }

        return Collections.emptyList();
    }

    /**
     *
     * Source: http://www.thymeleaf.org/doc/articles/springmail.html
     * @param caller
     * @param recCodeDb
     * @param locales
     */
    public void sendRecoveryMail(Subscriber caller, RecoveryCode recCodeDb, List<Locale> locales){
        try {
            final PhxStrings mailRecoveryHtml = strings.loadString("mail_recovery_html", locales);
            final PhxStrings mailRecoveryTxt = strings.loadString("mail_recovery_txt", locales);
            final PhxStrings mailRecoverySubject = strings.loadString("mail_recovery_html_subject", locales);
            final List<Locale> fixedLocales = strings.fixupLocales(locales, true);
            final String subject = mailRecoverySubject != null ? mailRecoverySubject.getValue() : "PhoneX Password recovery";

            final TemplateEngine templateEngine = new TemplateEngine();
            final Context ctx = new Context(fixedLocales.get(0));
            ctx.setVariable("caller", caller);
            ctx.setVariable("recovery", recCodeDb);
            ctx.setVariable("code", recoveryCodeToDisplayFormat(recCodeDb.getRecoveryCode()));
            ctx.setVariable("geoIp", geoIp.getGeoIp(recCodeDb.getRequestIp()));

            final String htmlContent = mailRecoveryHtml == null ? null : templateEngine.process(mailRecoveryHtml.getValue(), ctx);
            final String txtContent = mailRecoveryTxt == null ? null : templateEngine.process(mailRecoveryTxt.getValue(), ctx);

            mailSender.sendMailAsync(
                    PASSWORD_EMAIL_FROM,
                    caller.getRecoveryEmail(),
                    subject,
                    txtContent,
                    htmlContent);
        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Sends password recovered notification after successful forgotten-password procedure.
     * @param caller
     * @param recCodeDb
     * @param locales
     */
    public void sendPasswordRecoveredMail(Subscriber caller, RecoveryCode recCodeDb, List<Locale> locales){
        try {
            final PhxStrings mailRecoveredHtml = strings.loadString("mail_password_recovered_html", locales);
            final PhxStrings mailRecoveredTxt = strings.loadString("mail_password_recovered_txt", locales);
            final PhxStrings mailRecoveredSubject = strings.loadString("mail_password_recovered_subject", locales);
            final List<Locale> fixedLocales = strings.fixupLocales(locales, true);
            final String subject = mailRecoveredSubject != null ? mailRecoveredSubject.getValue() : "PhoneX Password recovered";

            final TemplateEngine templateEngine = new TemplateEngine();
            final Context ctx = new Context(fixedLocales.get(0));
            ctx.setVariable("caller", caller);
            ctx.setVariable("recovery", recCodeDb);
            ctx.setVariable("code", recoveryCodeToDisplayFormat(recCodeDb.getRecoveryCode()));
            ctx.setVariable("geoIp", geoIp.getGeoIp(recCodeDb.getConfirmIp()));

            final String htmlContent = mailRecoveredHtml == null ? null : templateEngine.process(mailRecoveredHtml.getValue(), ctx);
            final String txtContent = mailRecoveredTxt == null ? null : templateEngine.process(mailRecoveredTxt.getValue(), ctx);

            mailSender.sendMailAsync(
                    PASSWORD_EMAIL_FROM,
                    caller.getRecoveryEmail(),
                    subject,
                    txtContent,
                    htmlContent);
        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Sends email with password changed notification
     * @param caller
     * @param ip
     */
    public void sendPasswordChangedMail(Subscriber caller, String ip){
        try {
            if (caller == null || StringUtils.isEmpty(caller.getRecoveryEmail())){
                return;
            }

            final List<Locale> locales = extractLocalesFromAppVersion(caller.getAppVersion());
            final PhxStrings mailHtml = strings.loadString("mail_password_changed_html", locales);
            final PhxStrings mailTxt = strings.loadString("mail_password_changed_txt", locales);
            final PhxStrings mailSubject = strings.loadString("mail_password_changed_subject", locales);
            if (mailSubject == null || (mailHtml == null && mailTxt == null)){
                log.error("Could not send password changed email, mail templates not defined");
                return;
            }

            final List<Locale> fixedLocales = strings.fixupLocales(locales, true);
            final String subject = mailSubject.getValue();

            final TemplateEngine templateEngine = new TemplateEngine();
            final Context ctx = new Context(fixedLocales.get(0));
            ctx.setVariable("caller", caller);
            ctx.setVariable("changeDate", new Date());
            ctx.setVariable("geoIp", geoIp.getGeoIp(ip));

            final String htmlContent = mailHtml == null ? null : templateEngine.process(mailHtml.getValue(), ctx);
            final String txtContent = mailTxt == null ? null : templateEngine.process(mailTxt.getValue(), ctx);

            mailSender.sendMailAsync(
                    PASSWORD_EMAIL_FROM,
                    caller.getRecoveryEmail(),
                    subject,
                    txtContent,
                    htmlContent);
        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Sends email with new certificate notification.
     * @param caller
     * @param ip
     * @param cert
     */
    public void sendNewCertificateMail(Subscriber caller, String ip, X509Certificate cert){
        try {
            if (caller == null || StringUtils.isEmpty(caller.getRecoveryEmail())){
                return;
            }

            final List<Locale> locales = extractLocalesFromAppVersion(caller.getAppVersion());
            final PhxStrings mailHtml = strings.loadString("mail_new_cert_html", locales);
            final PhxStrings mailTxt = strings.loadString("mail_new_cert_txt", locales);
            final PhxStrings mailSubject = strings.loadString("mail_new_cert_subject", locales);
            if (mailSubject == null || (mailHtml == null && mailTxt == null)){
                log.error("Could not send new certificate notification email, mail templates not defined");
                return;
            }

            final List<Locale> fixedLocales = strings.fixupLocales(locales, true);
            final String subject = mailSubject.getValue();

            final TemplateEngine templateEngine = new TemplateEngine();
            final Context ctx = new Context(fixedLocales.get(0));
            ctx.setVariable("caller", caller);
            ctx.setVariable("cert", cert);
            ctx.setVariable("changeDate", new Date());
            ctx.setVariable("geoIp", geoIp.getGeoIp(ip));

            final String htmlContent = mailHtml == null ? null : templateEngine.process(mailHtml.getValue(), ctx);
            final String txtContent = mailTxt == null ? null : templateEngine.process(mailTxt.getValue(), ctx);

            mailSender.sendMailAsync(
                    PASSWORD_EMAIL_FROM,
                    caller.getRecoveryEmail(),
                    subject,
                    txtContent,
                    htmlContent);
        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Generates a new random recovery code.
     * @return
     */
    public String generateRecoveryCode(){
        final Random rnd = new SecureRandom();
        final StringBuilder sb = new StringBuilder(3*3+2);
        for( int i = 0; i < 9; i++ ) {
            sb.append(RECOVERY_CODE_CHARSET.charAt(rnd.nextInt(RECOVERY_CODE_CHARSET.length())));
        }
        return sb.toString();
    }

    /**
     * Turns 123456789 into 123-456-789.
     * @param originalCode
     * @return
     */
    public String recoveryCodeToDisplayFormat(String originalCode){
        final StringBuilder sb = new StringBuilder();
        final int len = originalCode.length();
        for(int i=0; i<len; i++){
            sb.append(originalCode.charAt(i));
            if (((i+1) % 3) == 0 && (i+1) < len){
                sb.append("-");
            }
        }

        return sb.toString();
    }
}
