package com.phoenix.service.push;

/**
 * Push message event signalizing server-stored contactlist has changed and should be reloaded on end devices.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class NewFileEventMessage extends SimplePushPart {
    public static final String PUSH = "newFile";

    public NewFileEventMessage() {
        this.setAction(PUSH);
        this.setUnique(true);
    }

    public NewFileEventMessage(long tstamp) {
        super(PUSH, tstamp);
        this.setUnique(true);
    }
}
