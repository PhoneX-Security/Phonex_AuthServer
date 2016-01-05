package com.phoenix.rest;

import com.phoenix.utils.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dusanklinec on 05.01.16.
 */
public class VerifyRecoveryCodeResponse {
    private static final Logger log = LoggerFactory.getLogger(VerifyRecoveryCodeResponse.class);

    private int statusCode=0;
    private String statusText;
    private String newPassword;

    private String auxJson;
    private JSONObject auxJsonObj;

    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("statusCode", statusCode);
        if (!StringUtils.isEmpty(statusText)){
            json.put("statusText", statusText);
        }

        if (newPassword != null){
            json.put("newPassword", newPassword);
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

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
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
}
