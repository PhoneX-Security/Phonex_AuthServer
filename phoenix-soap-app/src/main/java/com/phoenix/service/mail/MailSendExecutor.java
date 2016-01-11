package com.phoenix.service.mail;

import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.executor.JobRunnable;
import com.phoenix.service.executor.JobTask;
import com.phoenix.service.revocation.RevocationManager;
import com.phoenix.utils.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by dusanklinec on 05.01.16.
 */
@Service
@Repository
@Scope(value = "singleton")
public class MailSendExecutor extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(MailSendExecutor.class);

    @Autowired(required = true)
    public RevocationManager revocationManager;

    @Autowired
    private JiveGlobals jiveGlobals;

    /**
     * Executor for tasks being executed.
     */
    private final ExecutorService executor;

    public MailSendExecutor() {
        executor = Executors.newSingleThreadExecutor();;
    }

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        initThread(this, "MailSendExecutor");
    }

    @PreDestroy
    public synchronized void deinit(){
        executor.shutdownNow();
    }

    @Override
    public void run() {
        // Nothing to do.
    }

    /**
     * Instantiates sender from the server configuration.
     * @return
     * @throws IOException
     */
    public JavaMailSender mailSender() throws IOException {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(jiveGlobals.getProperty("mail.server.host"));
        mailSender.setPort(jiveGlobals.getIntProperty("mail.server.port", 465));
        mailSender.setProtocol(jiveGlobals.getProperty("mail.server.protocol"));
        mailSender.setUsername(jiveGlobals.getProperty("mail.server.username"));
        mailSender.setPassword(jiveGlobals.getProperty("mail.server.password"));
        mailSender.setDefaultEncoding("utf-8");
        mailSender.setJavaMailProperties(javaMailProperties());
        return mailSender;
    }

    private Properties javaMailProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(new ClassPathResource("properties/javamail.properties").getInputStream());
        return properties;
    }

    public void enqueueJob(JobTask job){
        executor.submit(job);
    }

    public void shutdown(){
        executor.shutdown();
    }

    /**
     * Enqueues new CRL generation to the executor.
     *
     * Has to be done here, outside of manager. If async method is in the same class as transactional method no proxy
     * would be used as direct call on the transactional method is invoked. Here transactional proxy wraps
     * calls to revocation manager.
     *
     * @param blockUntilFinished
     */
    public void sendMimeMessageAsync(final MimeMessage mimeMsg, boolean blockUntilFinished){
        final JobTask task = new JobTask("sendMail", new JobRunnable() {
            @Override
            public void run() {
                try {
                    mailSender().send(mimeMsg);
                } catch (Exception e) {
                    log.error("Exception when sending an email", e);
                }
            }
        });

        enqueueJob(task);
        if (blockUntilFinished){
            final boolean waitOk = task.waitCompletionUntil(1, TimeUnit.DAYS);
            log.info("sendMail: Execution finished {}", waitOk);
        }
    }
}
