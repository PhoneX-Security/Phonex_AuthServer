package com.phoenix.service.revocation;

import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.executor.JobRunnable;
import com.phoenix.service.executor.JobTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Takes care about executing revocation processing on the single thread executor to avoid race conditions on shared
 * resource - revocation record.
 *
 * Created by dusanklinec on 24.11.15.
 */
@Service
@Repository
@Scope(value = "singleton")
public class RevocationExecutor extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(RevocationExecutor.class);

    @Autowired(required = true)
    public RevocationManager revocationManager;

    /**
     * Executor for tasks being executed.
     */
    private final ExecutorService executor;

    public RevocationExecutor() {
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        initThread(this, "RevocationExecutor");
    }

    @PreDestroy
    public synchronized void deinit(){
        executor.shutdownNow();
    }

    @Override
    public void run() {
        // Nothing to do.
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
    public void generateNewCrlAsync(boolean blockUntilFinished){
        final RevocationManager localRevocationManager = revocationManager;
        final JobTask task = new JobTask("newCrl", new JobRunnable() {
            @Override
            public void run() {
                try {
                    log.info("Going to regenerate CRL");
                    localRevocationManager.generateNewCrl();
                } catch (Exception e) {
                    log.error("Exception when generating new CRL", e);
                }
            }
        });

        enqueueJob(task);
        if (blockUntilFinished){
            final boolean waitOk = task.waitCompletionUntil(1, TimeUnit.DAYS);
            log.info("newCrl: Execution finished {}", waitOk);
        }
    }

    /**
     * Enqueues new CRL generation to the executor.
     *
     * Has to be done here, outside of manager. If async method is in the same class as transactional method no proxy
     * would be used as direct call on the transactional method is invoked. Here transactional proxy wraps
     * calls to revocation manager.
     *
     * @param certificateSerial
     * @param blockUntilFinished
     */
    public void addNewCrlEntryAsync(final long certificateSerial, boolean blockUntilFinished){
        final RevocationManager localRevocationManager = revocationManager;
        final JobTask task = new JobTask("addNewCrlEntry", new JobRunnable() {
            @Override
            public void run() {
                try {
                    log.info("Adding a new certificate to CRL {}", certificateSerial);
                    localRevocationManager.addNewCrlEntry(certificateSerial);
                } catch (Exception e) {
                    log.error("Exception when adding a new CRL record");
                }
            }
        });

        enqueueJob(task);
        if (blockUntilFinished){
            final boolean waitOk = task.waitCompletionUntil(1, TimeUnit.DAYS);
            log.info("newCrlEntry: Execution finished {}", waitOk);
        }
    }
}
