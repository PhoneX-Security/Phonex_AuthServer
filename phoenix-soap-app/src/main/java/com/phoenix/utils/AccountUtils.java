package com.phoenix.utils;

import com.phoenix.db.opensips.Subscriber;
import org.apache.commons.lang.LocaleUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Account / subscriber related utils.
 *
 * Created by dusanklinec on 11.01.16.
 */
public class AccountUtils {
    private static final Logger log = LoggerFactory.getLogger(AccountUtils.class);

    /**
     * Extracs available locales from app version.
     * @param appVersion
     * @return
     */
    public static List<Locale> extractLocalesFromAppVersion(String appVersion){
        if (StringUtils.isEmpty(appVersion)){
            return Collections.emptyList();
        }

        try {
            final List<Locale> localesToReturn = new ArrayList<Locale>();
            final JSONObject appJson = new JSONObject(appVersion);
            if (!appJson.has("locales")){
                return localesToReturn;
            }

            final JSONArray locales = appJson.getJSONArray("locales");
            final int len = locales.length();
            if (len == 0){
                return localesToReturn;
            }

            for(int i=0; i<len; i++){
                try {
                    final String locString = locales.getString(i);
                    final Locale curLoc = LocaleUtils.toLocale(locString);
                    localesToReturn.add(curLoc);

                } catch(Exception ex){
                    log.error("Exception in subparse", ex);
                }
            }

            return localesToReturn;

        } catch(Exception e){
            log.error("Exception in parsing app version", e);
        }

        return Collections.emptyList();
    }

    /**
     * Returns SIP address from subscriber record
     * @param s
     * @return
     */
    public static String getSIP(Subscriber s){
        if (s==null) return "";
        return (s.getUsername() + "@" + s.getDomain());
    }
}
