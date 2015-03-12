package com.phoenix.service.push;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by dusanklinec on 11.03.15.
 */
public interface PushMessage {

    /**
     * Builds JSON representation of the object.
     *
     * @return
     * @throws org.json.JSONException
     */
    public JSONObject getJson() throws JSONException;
}
