package com.phoenix.service;

import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.files.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Calendar;
import java.util.List;

/**
 * Service periodically scans license database and notifies users with expired licenses if was not notified before.
 * Created by dusanklinec on 27.03.15.
 */
@Service
@Scope(value = "singleton")
public class LicenseChecker extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(LicenseChecker.class);

    /**
     * Timeout sleep for re-checking license expiration. Each 2 hours.
     */
    private static final long MAX_TIMEOUT = 1000L*60L*60L*2L;
    private long lastCheck = 0;

    private boolean running=true;

    @Autowired
    private PhoenixDataService dataService;

    @Autowired
    private AMQPListener amqpListener;

    public LicenseChecker() {

    }

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        initThread(this, "LicenseChecker");
        this.start();
    }

    @PreDestroy
    public synchronized void deinit(){
        setRunning(false);
    }

    @Transactional
    public void doTheJob(){
        try {
            // Get all expired accounts, possibly not those with marked notification.
            List<Subscriber> expiredLicense = dataService.getAllExpiredNonNotifiedLicenses();
            for(Subscriber s : expiredLicense){
                if (s == null || s.getExpires() == null){
                    log.warn("Null subscriber or expiration: " + s);
                    continue;
                }

                // Send push message for this guy.
                try {
                    final String sip = PhoenixDataService.getSIP(s);
                    log.info("Sending license check notification " + sip);

                    amqpListener.pushLicenseCheck(sip);
                } catch(Exception ex){
                    log.error("Error in pushing dh key used event", ex);
                }

                // Store information about this notification to database so user is not spammed with this notification each X hours.
                dataService.setLicenseNotification(s);
            }

        } catch(Exception ex){
            log.info("Problem occurred during license check", ex);
        }
    }

    @Override
    public void run() {
        while (this.running) {
            long cmilli = System.currentTimeMillis();

            // This task is executed if
            //  a) it was not executed in previous MAX_TIMEOUT milliseconds OR
            //  b) current hour of day is START_HOUR or 1 hour later.
            if ((cmilli - lastCheck) > MAX_TIMEOUT)  {
                lastCheck = cmilli;
                doTheJob();
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }
        log.info("License checker thread ended.");
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
