package com.phoenix.rest;

import com.phoenix.utils.MiscUtils;
import com.phoenix.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Response on user activity query.
 * Created by dusanklinec on 24.02.16.
 */
public class UserActivityResponse {
    private static final Logger log = LoggerFactory.getLogger(UserActivityResponse.class);

    private int statusCode=0;
    private String statusText;

    private List<UserActivityRecord> users;

    public synchronized List<UserActivityRecord> getList(){
        if (users == null){
            users = new LinkedList<UserActivityRecord>();
        }

        return users;
    }

    public JSONObject toJSON() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put("statusCode", statusCode);
        if (!StringUtils.isEmpty(statusText)){
            json.put("statusText", statusText);
        }

        JSONArray jArr = new JSONArray();
        if (MiscUtils.collectionIsEmpty(users)){
            for(UserActivityRecord rec : users){
                JSONObject jUsr = rec.toJSON();
                jArr.put(jUsr);
            }
        }

        json.put("users", jArr);
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

    public List<UserActivityRecord> getUsers() {
        return users;
    }

    public void setUsers(List<UserActivityRecord> users) {
        this.users = users;
    }
}
