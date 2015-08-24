package com.phoenix.rest;

import com.phoenix.utils.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Response for log file upload.
 * Created by dusanklinec on 24.08.15.
 */
public class LogUploadResponse {
    private static final Logger log = LoggerFactory.getLogger(LogUploadResponse.class);

    private int statusCode;
    private String auxJson;
    private JSONObject auxJsonObj;

    public LogUploadResponse() {
    }

    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("statusCode", statusCode);

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
            log.error("Exception in generating JSON response for LogUploadResponse.", ex);
        }

        return null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
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

    @Override
    public String toString() {
        return "LogUploadResponse{" +
                "statusCode=" + statusCode +
                ", auxJson='" + auxJson + '\'' +
                ", auxJsonObj=" + auxJsonObj +
                '}';
    }
}
