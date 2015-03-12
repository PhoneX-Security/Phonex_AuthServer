package com.phoenix.service.push;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Push message event signalizing user has logged in with certificate with given not before time.
 * Usage: notify old logged in devices that user with newer certificate has logged in so they can log out.
 *
 * Created by dusanklinec on 11.03.15.
 */
public class NewCertEventMessage extends SimplePushPart {
    public static final String PUSH = "newCert";
    public static final String FIELD_NOT_BEFORE = "certNotBefore";
    public static final String FIELD_CERT_HASH_PREFIX = "certHashPref";

    private long certNotBefore;
    private String certHashPrefix;

    public NewCertEventMessage() {
        setAction(PUSH);
        this.setUnique(true);
    }

    public NewCertEventMessage(long tstamp, long certNotBefore) {
        super(PUSH, tstamp);
        this.certNotBefore = certNotBefore;
        this.setUnique(true);
    }

    public NewCertEventMessage(long tstamp, long certNotBefore, String certHashPrefix) {
        super(PUSH, tstamp);
        this.certNotBefore = certNotBefore;
        this.certHashPrefix = certHashPrefix;
        this.setUnique(true);
    }

    public JSONObject getDataJson() throws JSONException {
        JSONObject obj = new JSONObject();

        obj.put(FIELD_NOT_BEFORE, certNotBefore);
        if (certHashPrefix != null && certHashPrefix.length() > 0){
            obj.put(FIELD_CERT_HASH_PREFIX, certHashPrefix);
        }

        return obj;
    }

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = getDataJson();
        setAuxData(obj);
        return super.getJson();
    }

    /**
     * Merges with given push part only if it carries more recent certificate information than this one.
     * @param part
     * @return
     */
    @Override
    public boolean mergeWith(SimplePushPart part) {
        if (!canMergeWith(part)){
            throw new RuntimeException("Cannot merge with given one");
        }

        NewCertEventMessage npart = (NewCertEventMessage) part;
        if (certNotBefore < npart.getCertNotBefore()){
            certNotBefore = npart.getCertNotBefore();
            messageId = npart.getMessageId();
            final String theirPrefix = npart.getCertHashPrefix();
            if (theirPrefix != null && theirPrefix.length() > 0){
                certHashPrefix = theirPrefix;
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean canMergeWith(SimplePushPart part) {
        return (part instanceof NewCertEventMessage) && super.canMergeWith(part);
    }

    public long getCertNotBefore() {
        return certNotBefore;
    }

    public void setCertNotBefore(long certNotBefore) {
        this.certNotBefore = certNotBefore;
    }

    public String getCertHashPrefix() {
        return certHashPrefix;
    }

    public void setCertHashPrefix(String certHashPrefix) {
        this.certHashPrefix = certHashPrefix;
    }
}
