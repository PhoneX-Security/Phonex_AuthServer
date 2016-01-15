package com.phoenix.service;

import com.phoenix.service.executor.*;
import com.phoenix.utils.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executor engine. Enables to submit a job for parallel background execution or
 * when using a strip object to execute in a serial queue corresponding to the strip object.
 *
 * Created by dusanklinec on 13.03.15.
 */
@Service
@Scope(value = "singleton")
public class TaskExecutor  extends BackgroundThreadService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);
    private static final int NTHREADS = 5;

    private volatile boolean running = true;
    private final ConcurrentLinkedQueue<JobTask> jobs = new ConcurrentLinkedQueue<JobTask>();
    private ExecutorService executor;

    /**
     * Serial queues executor.
     */
    private final StripedExecutorService serialExecutors = new StripedExecutorService();

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        initThread(this, "TaskExecutor");
        executor = Executors.newFixedThreadPool(NTHREADS);

        log.info(String.format("TaskExecutor starting: %s", this));
        this.start();
    }

    @PreDestroy
    public synchronized void deinit() {
        log.info(String.format("Deinitializing TaskExecutor, this=%s", this));
        final List<Runnable> runnables = executor.shutdownNow();
        for(Runnable r : runnables){
            if (!(r instanceof JobTask)) {
                continue;
            }

            final JobTask job = (JobTask) r;
            job.cancelledFromOutside();
        }

        final List<Runnable> runnables2 = serialExecutors.shutdownNow();
        for(Runnable r : runnables2){
            if (!(r instanceof JobTask)) {
                continue;
            }

            final JobTask job = (JobTask) r;
            job.cancelledFromOutside();
        }

        setRunning(false);
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public JobFuture<?> submit(String name, Object stripe, JobRunnable job, final JobFinishedListener listener){
        final JobTask xjob = new JobTask(name, job);
        JobTaskFinishedListener finishListener = null;
        if (listener != null){
            finishListener = new JobTaskFinishedListener() {
                @Override
                public void jobFinished(JobTask job) {
                    listener.jobFinished(job.getJob(), null);

                    // Here you may make some precautions if task is in a serial queue.
                    // ...
                }
            };
        }

        xjob.setListener(finishListener);
        xjob.setStripe(stripe);

        final Future<?> future = executor.submit(xjob);
        final JobFuture<?> jobFuture = new JobFuture(future);
        jobFuture.setJob(xjob);

        return jobFuture;
    }

    /**
     * Submit a job to the executor.
     * @param job
     */
    public JobFuture<?> submit(String name, JobRunnable job, final JobFinishedListener listener){
        return submit(name, null, job, listener);
    }

    @Transactional
    public void doTheJob() {
        // Main working loop.
        while(running){
            while(!jobs.isEmpty() && running){
                JobTask job = jobs.poll();
                if (job == null){
                    continue;
                }

                // Here the job can be configured before execution.
                // Add job to the executor.
                // ...
            }

            // Job queue is empty --> wait a second.
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                log.error("Sleep interrupted", e);
                break;
            }
        }
    }

    @Override
    public void run() {
        this.running = true;
        while (this.running) {
            try {
                doTheJob();
            } catch(Exception e){
                log.error("Exception during task execution", e);
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                log.error("Interrupted", ex);
                break;
            }
        }

        log.info(String.format("TaskExecutor thread ended. Running: %s, this: %s", running, this));
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

}
