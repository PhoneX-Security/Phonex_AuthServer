package com.phoenix.service.executor;

import com.phoenix.service.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for jobs execution.
 */
public class JobTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);
    private final JobRunnable job;
    private String name;
    private JobTaskFinishedListener listener;

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
    }

    public void cancelledFromOutside(){
        // Notify job has ended its execution.
        if (listener != null){
            listener.jobFinished(this);
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