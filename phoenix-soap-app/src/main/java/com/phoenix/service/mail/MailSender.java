package com.phoenix.service.mail;

import com.phoenix.utils.JiveGlobals;
import com.phoenix.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import javax.mail.internet.MimeMessage;

/**
 * Created by dusanklinec on 05.01.16.
 */
@Service
@Repository
public class MailSender {
    private static final Logger log = LoggerFactory.getLogger(MailSender.class);

    @Autowired
    private JiveGlobals jiveGlobals;

    @Autowired(required = true)
    private MailSendExecutor executor;

    /**
     * Sends mail on the background thread.
     * @param from
     * @param to
     * @param subject
     * @param txtBody
     * @param htmlBody
     */
    public void sendMailAsync(String from, String fromName, String to, String subject, String txtBody, String htmlBody){
        try {
            final MimeMessage mime = buildMimeMessage(from, fromName, to, subject, txtBody, htmlBody);
            executor.sendMimeMessageAsync(mime, false);

        } catch (Exception e) {
            log.error("Exception in sending mail", e);
        }
    }

    public void sendMail(MimeMessage mime){
        try {
            final JavaMailSender sender = executor.mailSender();
            sender.send(mime);

        } catch (Exception e) {
            log.error("Exception in sending mail", e);
        }
    }

    public void sendMail(String from, String fromName, String to, String subject, String txtBody, String htmlBody){
        try {
            final JavaMailSender sender = executor.mailSender();
            final MimeMessage mime = buildMimeMessage(from, fromName, to, subject, txtBody, htmlBody);
            sender.send(mime);

        } catch (Exception e) {
            log.error("Exception in sending mail", e);
        }
    }

    /**
     * Sends mail from the current thread.
     * @param from
     * @param to
     * @param subject
     * @param txtBody
     * @param htmlBody
     */
    public MimeMessage buildMimeMessage(String from, String fromName, String to, String subject, String txtBody, String htmlBody){
        try {
            final JavaMailSender sender = executor.mailSender();

            final MimeMessage mimeMessage = sender.createMimeMessage();
            final MimeMessageHelper message = new MimeMessageHelper(mimeMessage, "utf-8");

            if (!StringUtils.isEmpty(fromName)){
                message.setFrom(from, fromName);
            } else {
                message.setFrom(from);
            }

            message.setTo(to);
            message.setSubject(subject);

            // Txt / html body.
            if (!StringUtils.isEmpty(txtBody) && !StringUtils.isEmpty(htmlBody)) {
                message.setText(txtBody, htmlBody);
            } else if (!StringUtils.isEmpty(txtBody)){
                message.setText(txtBody, false);
            } else {
                message.setText(htmlBody, true);
            }

            return mimeMessage;

        } catch (Exception e) {
            log.error("Exception in sending mail", e);
        }

        return null;
    }

}
