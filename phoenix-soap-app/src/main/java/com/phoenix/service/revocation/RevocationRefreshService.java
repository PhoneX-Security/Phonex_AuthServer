package com.phoenix.service.revocation;

import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.PhoenixDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service used to generate a new CRL at regular intervals.
 *
 * Created by dusanklinec on 24.11.15.
 */
@Service
@Scope(value = "singleton")
public class RevocationRefreshService  extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(RevocationRefreshService.class);

    /**
     * Timeout sync in milliseconds.
     */
    private static final long TIMEOUT_SYNC = 1000L * 60L * 60L * 8L;

    private long lastSync = 0;
    private final AtomicBoolean running = new AtomicBoolean(true);

    @Autowired
    private PhoenixDataService dataService;

    @Autowired
    private RevocationManager revocationManager;

    @Autowired
    private RevocationExecutor revocationExecutor;

    public RevocationRefreshService() {

    }

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        initThread(this, "RevocationRefreshService");
        this.start();
    }

    @PreDestroy
    public synchronized void deinit() {
        setRunning(false);
    }

    @Transactional
    public void doTheJob() {
        try {
            log.info("Going to generate new CRL");
            revocationExecutor.generateNewCrlAsync(true);

        } catch (Exception ex) {
            log.info("Problem occurred during property sync", ex);
        }
    }

    @Override
    public void run() {
        while (this.running.get()) {
            long cmilli = System.currentTimeMillis();
            if ((cmilli - lastSync) > TIMEOUT_SYNC) {
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
        log.info("PropertySync thread ended.");
    }

    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setRunning(boolean running) {
        this.running.set(running);
    }
}