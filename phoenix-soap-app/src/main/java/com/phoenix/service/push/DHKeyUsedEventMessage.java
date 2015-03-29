package com.phoenix.service.push;

/**
 * Push message event signalizing one or more DHKey was used and user may regenerate some DHKeys in order to provide FT service.
 * Created by dusanklinec on 27.03.15.
 */
public class DHKeyUsedEventMessage  extends SimplePushPart {
    public static final String PUSH = "dhUse";

    public DHKeyUsedEventMessage() {
        this.setAction(PUSH);
        this.setUnique(true);
    }

    public DHKeyUsedEventMessage(long tstamp) {
        super(PUSH, tstamp);
        this.setUnique(true);
    }
}

