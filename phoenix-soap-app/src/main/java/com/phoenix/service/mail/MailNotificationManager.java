package com.phoenix.service.mail;

import com.phoenix.db.PhxRecoveryCode;
import com.phoenix.db.PhxStrings;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.geoip.GeoIpManager;
import com.phoenix.service.*;
import com.phoenix.utils.AccountUtils;
import com.phoenix.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Mail notification manager. Sends mail to users about important events, password rests & so on.
 *
 * Created by dusanklinec on 11.01.16.
 */
@Service
@Repository
public class MailNotificationManager {
    private static final Logger log = LoggerFactory.getLogger(MailNotificationManager.class);
    public static final String PASSWORD_EMAIL_FROM = "system@phone-x.net";
    public static final String PASSWORD_EMAIL_FROM_NAME = "PhoneX Security";

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

    /**
     *
     * Source: http://www.thymeleaf.org/doc/articles/springmail.html
     * @param caller
     * @param recCodeDb
     * @param locales
     */
    public void sendRecoveryMail(Subscriber caller, PhxRecoveryCode recCodeDb, List<Locale> locales){
        try {
            final Map<String, Object> ctx = new HashMap<String, Object>();
            ctx.put("caller", caller);
            ctx.put("recovery", recCodeDb);
            ctx.put("code", AccountManager.recoveryCodeToDisplayFormat(recCodeDb.getRecoveryCode()));
            ctx.put("codeLink", String.format("https://www.phone-x.net/recoverycode/%s/%s",
                    URLEncoder.encode(recCodeDb.getSubscriberSip(), "UTF-8"),
                    recCodeDb.getRecoveryCode()));
            ctx.put("codeLinkDisplay", String.format("https://www.phone-x.net/recoverycode/%s/%s",
                    recCodeDb.getSubscriberSip(),
                    recCodeDb.getRecoveryCode()));
            ctx.put("geoIp", geoIp.getGeoIp(recCodeDb.getRequestIp()));

            sendGeneralMail("mail_recovery", locales, ctx, caller.getRecoveryEmail());

        }catch (Exception e){
            log.error("Exception in sending email", e);
        }
    }

    /**
     * Sends password recovered notification after successful forgotten-password procedure.
     * @param caller
     * @param recCodeDb
     * @param locales
     */
    public void sendPasswordRecoveredMail(Subscriber caller, PhxRecoveryCode recCodeDb, List<Locale> locales){
        try {
            final Map<String, Object> ctx = new HashMap<String, Object>();
            ctx.put("caller", caller);
            ctx.put("recovery", recCodeDb);
            ctx.put("code", AccountManager.recoveryCodeToDisplayFormat(recCodeDb.getRecoveryCode()));
            ctx.put("geoIp", geoIp.getGeoIp(recCodeDb.getConfirmIp()));

            sendGeneralMail("mail_password_recovered", locales, ctx, caller.getRecoveryEmail());

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

            final List<Locale> locales = AccountUtils.extractLocalesFromAppVersion(caller.getAppVersion());
            final Map<String, Object> ctx = new HashMap<String, Object>();
            ctx.put("caller", caller);
            ctx.put("sip", AccountUtils.getSIP(caller));
            ctx.put("ip", ip);
            ctx.put("changeDate", new Date());
            ctx.put("geoIp", geoIp.getGeoIp(ip));

            sendGeneralMail("mail_password_changed", locales, ctx, caller.getRecoveryEmail());

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

            final List<Locale> locales = AccountUtils.extractLocalesFromAppVersion(caller.getAppVersion());
            final Map<String, Object> ctx = new HashMap<String, Object>();
            ctx.put("caller", caller);
            ctx.put("cert", cert);
            ctx.put("sip", AccountUtils.getSIP(caller));
            ctx.put("ip", ip);
            ctx.put("changeDate", new Date());
            ctx.put("geoIp", geoIp.getGeoIp(ip));

            sendGeneralMail("mail_new_cert", locales, ctx, caller.getRecoveryEmail());

        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Sends email with confirmation that this address will be used for password reset purposes.
     * @param caller
     * @param ip
     */
    public void sendEmailSetConfirmationMail(Subscriber caller, String ip){
        try {
            if (caller == null || StringUtils.isEmpty(caller.getRecoveryEmail())){
                return;
            }

            final List<Locale> locales = AccountUtils.extractLocalesFromAppVersion(caller.getAppVersion());
            final Map<String, Object> ctx = new HashMap<String, Object>();
            ctx.put("caller", caller);
            ctx.put("sip", AccountUtils.getSIP(caller));
            ctx.put("ip", ip);
            ctx.put("changeDate", new Date());
            ctx.put("geoIp", geoIp.getGeoIp(ip));

            sendGeneralMail("mail_new_email_confirm", locales, ctx, caller.getRecoveryEmail());

        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Mail sent to previous email when password recovery mail has changed.
     * @param caller
     * @param oldMail
     * @param ip
     */
    public void sendEmailChangedMail(Subscriber caller, String oldMail, String ip){
        try {
            if (caller == null || StringUtils.isEmpty(caller.getRecoveryEmail())){
                return;
            }

            final List<Locale> locales = AccountUtils.extractLocalesFromAppVersion(caller.getAppVersion());
            final Map<String, Object> ctx = new HashMap<String, Object>();
            ctx.put("caller", caller);
            ctx.put("sip", AccountUtils.getSIP(caller));
            ctx.put("ip", ip);
            ctx.put("changeDate", new Date());
            ctx.put("geoIp", geoIp.getGeoIp(ip));
            ctx.put("newMail", caller.getRecoveryEmail());

            sendGeneralMail("mail_new_email_notif", locales, ctx, oldMail);

        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Generalized way of sending emails to a given user, with defined context values.
     * @param templateName
     * @param locales
     * @param contextValues
     * @param to
     * @return
     */
    protected int sendGeneralMail(String templateName, List<Locale> locales, Map<String, Object> contextValues, String to){
        try {
            if (StringUtils.isEmpty(to)){
                return -1;
            }

            final List<Locale> fixedLocales = strings.fixupLocales(locales, true);
            final PhxStrings mailHtml = strings.loadString(templateName + "_html", fixedLocales);
            final PhxStrings mailTxt = strings.loadString(templateName + "_txt", fixedLocales);
            final PhxStrings mailSubject = strings.loadString(templateName + "_subject", fixedLocales);
            if (mailSubject == null || (mailHtml == null && mailTxt == null)){
                log.error("Could not send "+templateName+" notification email, mail templates not defined");
                return -2;
            }

            final String subject = mailSubject.getValue();
            final TemplateEngine templateEngine = new TemplateEngine();
            final Context ctx = new Context(fixedLocales.get(0));
            ctx.setVariables(contextValues);

            final String htmlContent = mailHtml == null ? null : templateEngine.process(mailHtml.getValue(), ctx);
            final String txtContent = mailTxt == null ? null : templateEngine.process(mailTxt.getValue(), ctx);

            mailSender.sendMailAsync(
                    PASSWORD_EMAIL_FROM,
                    PASSWORD_EMAIL_FROM_NAME,
                    to,
                    subject,
                    txtContent,
                    htmlContent);
            return 0;
        } catch(Exception e){
            log.error("Exception in sending mail", e);
        }

        return -3;
    }
}
