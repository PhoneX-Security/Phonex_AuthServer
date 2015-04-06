package com.phoenix.service.executor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by dusanklinec on 06.04.15.
 */
public class JobFuture<T> implements Future<T> {
    private final Future<T> future;
    private JobTask job;

    public JobFuture(Future<T> future) {
        this.future = future;
    }

    @Override
    public boolean cancel(boolean b) {
        final boolean cancelled =  future.cancel(b);
        job.cancelledFromOutside();
        return cancelled;
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public T get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(l, timeUnit);
    }

    public JobTask getJob() {
        return job;
    }

    public void setJob(JobTask job) {
        this.job = job;
    }
}
