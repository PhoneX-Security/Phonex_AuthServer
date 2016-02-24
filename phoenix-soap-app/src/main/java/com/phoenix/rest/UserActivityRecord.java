package com.phoenix.rest;

import com.phoenix.utils.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple record carrying single user activity metrics.
 * Created by dusanklinec on 24.02.16.
 */
public class UserActivityRecord {
    private static final Logger log = LoggerFactory.getLogger(UserActivityRecord.class);

    /**
     * User name, record identifier.
     * Should be always present.
     */
    private String user;

    /**
     * Last logout record;
     */
    private Long lastLogoutTimestamp;

    /**
     * Last activity timestamp
     */
    private Long lastCallTimestamp;
    private Long lastActiveTimestamp;
    private Long lastXmppMessage;
    private Long lastRegistration;

    private String auxJson;
    private JSONObject auxJsonObj;

    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("user", user);

        if (lastLogoutTimestamp != null){
            json.put("tLastLogout", lastLogoutTimestamp);
        }

        if (lastCallTimestamp != null){
            json.put("tLastCall", lastCallTimestamp);
        }

        if (lastActiveTimestamp != null){
            json.put("tLastActive", lastActiveTimestamp);
        }

        if (lastXmppMessage != null){
            json.put("tLastXmpp", lastXmppMessage);
        }

        if (lastRegistration != null){
            json.put("tLastReg", lastRegistration);
        }

        if (!StringUtils.isEmpty(auxJson)) {
            json.put("auxJson", auxJson);
        }

        if (auxJsonObj != null){
            json.put("json", auxJsonObj);
        }

        return json;
    }

    public String toJSONString() throws JSONException {
        return toJSON().toString();
    }

    public String tryToJSONString() {
        try {
            return toJSON().toString();
        } catch(Exception ex){
            log.error("Exception in generating JSON response for RecoveryCodeResponse.", ex);
        }

        return null;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public Long getLastLogoutTimestamp() {
        return lastLogoutTimestamp;
    }

    public void setLastLogoutTimestamp(Long lastLogoutTimestamp) {
        this.lastLogoutTimestamp = lastLogoutTimestamp;
    }

    public Long getLastActiveTimestamp() {
        return lastActiveTimestamp;
    }

    public void setLastActiveTimestamp(Long lastActiveTimestamp) {
        this.lastActiveTimestamp = lastActiveTimestamp;
    }

    public Long getLastXmppMessage() {
        return lastXmppMessage;
    }

    public void setLastXmppMessage(Long lastXmppMessage) {
        this.lastXmppMessage = lastXmppMessage;
    }

    public Long getLastRegistration() {
        return lastRegistration;
    }

    public void setLastRegistration(Long lastRegistration) {
        this.lastRegistration = lastRegistration;
    }

    public String getAuxJson() {
        return auxJson;
    }

    public void setAuxJson(String auxJson) {
        this.auxJson = auxJson;
    }

    public JSONObject getAuxJsonObj() {
        return auxJsonObj;
    }

    public void setAuxJsonObj(JSONObject auxJsonObj) {
        this.auxJsonObj = auxJsonObj;
    }

    public Long getLastCallTimestamp() {
        return lastCallTimestamp;
    }

    public void setLastCallTimestamp(Long lastCallTimestamp) {
        this.lastCallTimestamp = lastCallTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserActivityRecord that = (UserActivityRecord) o;

        return !(user != null ? !user.equals(that.user) : that.user != null);

    }

    @Override
    public int hashCode() {
        return user != null ? user.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "UserActivityRecord{" +
                "user='" + user + '\'' +
                ", lastLogoutTimestamp=" + lastLogoutTimestamp +
                ", lastActiveTimestamp=" + lastActiveTimestamp +
                ", lastXmppMessage=" + lastXmppMessage +
                ", lastRegistration=" + lastRegistration +
                ", auxJson='" + auxJson + '\'' +
                ", auxJsonObj=" + auxJsonObj +
                '}';
    }
}
