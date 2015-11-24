package com.phoenix.service.revocation;

import com.phoenix.service.BackgroundThreadService;
import com.phoenix.service.executor.JobTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Takes care about executing revocation processing on the single thread executor to avoid race conditions on shared
 * resource - revocation record.
 *
 * Created by dusanklinec on 24.11.15.
 */
@Service
@Scope(value = "singleton")
public class RevocationExecutor extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(RevocationExecutor.class);

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
}
