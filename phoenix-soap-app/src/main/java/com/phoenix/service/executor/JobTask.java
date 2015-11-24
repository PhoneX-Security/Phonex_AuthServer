package com.phoenix.service.executor;

import com.phoenix.service.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for jobs execution.
 */
public class JobTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);
    private final JobRunnable job;
    private String name;
    private JobTaskFinishedListener listener;

    private final Semaphore finishSemaphore = new Semaphore(0);
    private final AtomicBoolean done = new AtomicBoolean(false);

    public JobTask(JobRunnable job) {
        this.job = job;
    }

    public JobTask(String name, JobRunnable job) {
        this.name = name;
        this.job = job;
    }

    @Override
    public void run() {
        try {
            job.run();
        } catch(Exception e){
            log.error(String.format("Exception in executing a job %s", name), e);
        }

        // Notify job has ended its execution.
        if (listener != null){
            listener.jobFinished(this);
        }

        // Semaphore signalization.
        if (!done.get()) {
            done.set(true);
            finishSemaphore.release();
        }
    }

    public void cancelledFromOutside(){
        // Notify job has ended its execution.
        if (listener != null){
            listener.jobFinished(this);
        }

        // Semaphore signalization.
        if (!done.get()) {
            done.set(true);
            finishSemaphore.release();
        }
    }

    /**
     * Waits until jobs is completed or given time amount is expired.
     * @param time
     * @param timeUnit
     * @return
     */
    public boolean waitCompletionUntil(long time, TimeUnit timeUnit){
        if (done.get()){
            return true;
        }

        try {
            final boolean acquired = finishSemaphore.tryAcquire(1, time, timeUnit);
            if (acquired){
                finishSemaphore.release();
            }

            return acquired;

        } catch(InterruptedException e){
            log.error("Waiting interrupted", e);
            return false;
        }
    }

    public JobTaskFinishedListener getListener() {
        return listener;
    }

    public void setListener(JobTaskFinishedListener listener) {
        this.listener = listener;
    }

    public JobRunnable getJob() {
        return job;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
