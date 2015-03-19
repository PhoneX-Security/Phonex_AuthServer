package com.phoenix.service.push;

/**
 * Push message event signalizing remote contact in our roster has changed his certificate.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class ContactCertUpdateEventMessage extends SimplePushPart {
    public static final String PUSH = "cCrtUpd";

    public ContactCertUpdateEventMessage() {
        this.setAction(PUSH);
        this.setUnique(true);
    }

    public ContactCertUpdateEventMessage(long tstamp) {
        super(PUSH, tstamp);
        this.setUnique(true);
    }
}
