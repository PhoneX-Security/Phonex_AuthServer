package com.phoenix.service.push;

/**
 * Push message event signalizing PhoneX client should immediately logout from the application.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class LogoutEventMessage extends SimplePushPart {
    public static final String PUSH = "logout";

    public LogoutEventMessage() {
        this.setAction(PUSH);
        this.setUnique(true);
    }

    public LogoutEventMessage(long tstamp) {
        super(PUSH, tstamp);
        this.setUnique(true);
    }
}
