package com.phoenix.service;

import com.phoenix.db.PhxStrings;
import com.phoenix.db.RecoveryCode;
import com.phoenix.db.extra.PluralFormsEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.*;

/**
 * Loading localized strings from database.
 *
 * Created by dusanklinec on 05.01.16.
 */
@Service
@Repository
public class StringsManager {
    private static final Logger log = LoggerFactory.getLogger(StringsManager.class);
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    private PhoenixDataService dataService;

    /**
     * Loads string with given locale and plural form from the database.
     * If given string is not found in a given locale according to the prefered order, default one is loaded.
     *
     * @param key
     * @param locales
     * @param pluralForm
     * @return
     */
    public PhxStrings loadString(String key, List<Locale> locales, PluralFormsEnum pluralForm){
        final List<Locale> realLocales = fixupLocales(locales, true);
        final ArrayList<String> localeString = new ArrayList<String>();
        for (Locale l : realLocales){
            localeString.add(l.toString());
        }

        // Load recovery.
        // Fetch newest auth state record.
        final TypedQuery<PhxStrings> dbQuery = em.createQuery("SELECT st FROM PhxStrings st " +
                " WHERE st.key=:key " +
                " AND st.pluralType=:plural " +
                " AND st.locale IN :locales " +
                " ", PhxStrings.class);
        dbQuery.setParameter("key", key);
        dbQuery.setParameter("plural", pluralForm.toString());
        dbQuery.setParameter("locales", localeString);

        final List<PhxStrings> strings = dbQuery.getResultList();
        if (strings.isEmpty()){
            return null;
        }

        // Pick in the given order.
        final Map<String, PhxStrings> stringMap = new HashMap<String, PhxStrings>();
        for(PhxStrings str : strings){
            stringMap.put(str.getLocale(), str);
        }

        for(String strLoc : localeString){
            final PhxStrings tmpStr = stringMap.get(strLoc);
            if (tmpStr != null){
                return tmpStr;
            }
        }

        return null;
    }

    /**
     * Loads string with given locale and plural form from the database.
     * If given string is not found, default one is loaded.
     * @param key
     * @param locale
     * @param pluralForm
     * @return
     */
    public PhxStrings loadString(String key, Locale locale, PluralFormsEnum pluralForm){
        return loadString(key, Collections.singletonList(locale), pluralForm);
    }

    public PhxStrings loadString(String key, List<Locale> locales){
        return loadString(key, locales, PluralFormsEnum.NONE);
    }

    public PhxStrings loadString(String key, Locale locale){
        return loadString(key, locale, PluralFormsEnum.NONE);
    }

    public PhxStrings loadString(String key, PluralFormsEnum pluralForm){
        return loadString(key, DEFAULT_LOCALE, pluralForm);
    }

    public PhxStrings loadString(String key){
        return loadString(key, DEFAULT_LOCALE, PluralFormsEnum.NONE);
    }

    /**
     * Removes duplicates from locale list, adds default to the end if not present already.
     * @param locales
     * @param addDefault
     * @return
     */
    public List<Locale> fixupLocales(List<Locale> locales, boolean addDefault){
        if (locales == null){
            locales = Collections.emptyList();
        }

        final Set<Locale> localeSet = new HashSet<Locale>(locales);
        if (localeSet.size() == locales.size()){
            if (!addDefault){
                return locales;
            }

            if (localeSet.contains(DEFAULT_LOCALE)){
                return locales;
            }

            final ArrayList<Locale> srcLocale = new ArrayList<Locale>(locales);
            srcLocale.add(DEFAULT_LOCALE);
            return srcLocale;
        }

        // Make a copy.
        final ArrayList<Locale> srcLocale = new ArrayList<Locale>(locales);
        final ArrayList<Locale> dstLocale = new ArrayList<Locale>(localeSet.size()+1);
        final Set<Locale> dstSet = new HashSet<Locale>();

        // Size differs, so prunning is required anyway. If default locale should be added, do it now.
        if (addDefault) {
            srcLocale.add(DEFAULT_LOCALE);
            localeSet.add(DEFAULT_LOCALE);
        }

        for(Locale curLcl : srcLocale){
            if (dstSet.contains(curLcl)){
                continue;
            }

            dstLocale.add(curLcl);
            dstSet.add(curLcl);
        }

        return dstLocale;
    }

}
