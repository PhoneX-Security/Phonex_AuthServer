package com.phoenix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Calendar;
import java.util.Date;

/**
 * Service responsible for cleaning trial events logs from the database over the time.
 *
 * @author ph4r05
 */
@Service
@Scope(value = "singleton")
public class TrialLogCleaner extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(TrialLogCleaner.class);

    /**
     * Maximum allowed delay of the task invocation.
     * If task was not executed in last MAX_TIMEOUT milliseconds it will be executed
     * as soon as possible.
     */
    private static final long MAX_TIMEOUT = 1000L * 60L * 60L * 6L;

    /**
     * How long to keep trial log events in the database.
     * If the entry is older than given time they are deleted.
     */
    private static final long DELETE_TIME = 1000L * 60L * 60L * 24L * 3;

    private long lastRefresh = 0;
    private boolean running = true;

    @Autowired
    private PhoenixDataService dataService;

    public TrialLogCleaner() {

    }

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        initThread(this, "TrialLogCleaner");
        this.start();
    }

    @PreDestroy
    public synchronized void deinit() {
        setRunning(false);
    }

    @Transactional
    public void doTheJob() {
        try {
            log.info("TrialLogCleaner started");

            final Date deleteOlderThanBoundary = new Date(System.currentTimeMillis() - DELETE_TIME);
            int res = dataService.cleanTrialLogsOlderThan(deleteOlderThanBoundary);

            log.info("TrialLogCleaner ended, deleted=" + res);
        } catch (Exception ex) {
            log.info("Problem occurred during TrialLogCleaner", ex);
        }
    }

    @Override
    public void run() {
        while (this.running) {
            Calendar cal = Calendar.getInstance();
            long cmilli = System.currentTimeMillis();
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            // This task is executed it was not executed in previous MAX_TIMEOUT milliseconds.
            if ((cmilli - lastRefresh) > MAX_TIMEOUT) {
                lastRefresh = cmilli;
                doTheJob();
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }
        log.info("TrialLogCleaner thread ended.");
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