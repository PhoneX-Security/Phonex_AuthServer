package com.phoenix.service.executor;

/**
 * Created by dusanklinec on 15.01.16.
 */
public interface StripedRunner {
    Object getStripeClass();
    void run();
}
