package com.phoenix.service.push;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Abstract class represents a single push part. For code simplicity each push part has own object.
 * Created by dusanklinec on 11.03.15.
 */
public abstract class SimplePushPart implements PushMessage {
    public static final String FIELD_PUSH = "push";
    public static final String FIELD_TIME_STAMP = "tstamp";
    public static final String FIELD_DATA = "data";

    /**
     * Push action.
     */
    protected String action;

    /**
     * Push message timestamp.
     */
    protected long tstamp;

    /**
     * Aux push data to be transmitted with the push message in data:{} object.
     */
    protected JSONObject auxData;

    /**
     * If TRUE then this push notification is of unique type, thus only one (and newer) is accepted in the push message.
     * If is FALSE then multiple of this messages can be in push message.
     */
    protected boolean unique = true;

    /**
     * Id of the corresponding database push message.
     */
    protected Long messageId;

    public SimplePushPart() {
    }

    public SimplePushPart(String action, long tstamp) {
        this.action = action;
        this.tstamp = tstamp;
    }

    @Override
    public JSONObject getJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(FIELD_PUSH, action);
        obj.put(FIELD_TIME_STAMP, tstamp);
        if (auxData != null){
            obj.put(FIELD_DATA, auxData);
        }

        return obj;
    }

    /**
     * Returns true if action of provided part is equal to ours.
     *
     * @param part
     * @return
     */
    public boolean isEqualAction(SimplePushPart part){
        return part != null && action.equals(part.getAction());
    }

    /**
     * Returns true if this part can be successfully merged with given part.
     * @param part
     * @return
     */
    public boolean canMergeWith(SimplePushPart part){
        return isEqualAction(part);
    }

    /**
     * Merges this part with given one, returning true if this part was changed during merge.
     * @param part
     * @return
     */
    public boolean mergeWith(SimplePushPart part){
        if (!canMergeWith(part)){
            throw new RuntimeException("This part cannot be merged with given one");
        }

        if (tstamp < part.getTstamp()){
            tstamp = part.getTstamp();
            messageId = part.getMessageId();
            return true;
        }

        return false;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public long getTstamp() {
        return tstamp;
    }

    public void setTstamp(long tstamp) {
        this.tstamp = tstamp;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public JSONObject getAuxData() {
        return auxData;
    }

    public void setAuxData(JSONObject auxData) {
        this.auxData = auxData;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimplePushPart that = (SimplePushPart) o;

        if (tstamp != that.tstamp) return false;
        if (unique != that.unique) return false;
        if (!action.equals(that.action)) return false;
        if (auxData != null ? !auxData.equals(that.auxData) : that.auxData != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + (int) (tstamp ^ (tstamp >>> 32));
        result = 31 * result + (auxData != null ? auxData.hashCode() : 0);
        result = 31 * result + (unique ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SimplePushPart{" +
                "action='" + action + '\'' +
                ", tstamp=" + tstamp +
                ", auxData=" + auxData +
                ", unique=" + unique +
                '}';
    }
}
