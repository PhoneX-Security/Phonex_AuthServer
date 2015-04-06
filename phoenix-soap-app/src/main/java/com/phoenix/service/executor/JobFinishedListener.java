package com.phoenix.service.executor;

import java.util.concurrent.Future;

/**
 * Created by dusanklinec on 06.04.15.
 */
public interface JobFinishedListener {
    public void jobFinished(JobRunnable job, Future<?> future);
}
