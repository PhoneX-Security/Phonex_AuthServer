package com.phoenix.service.executor;

import java.util.concurrent.Callable;

/**
 * Created by dusanklinec on 15.01.16.
 */
public interface StripedCallable<V> extends Callable<V>, StripedObject {
}
