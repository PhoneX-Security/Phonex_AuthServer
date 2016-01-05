package com.phoenix.geoip;

import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;

import com.phoenix.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dusanklinec on 05.01.16.
 */
public class GeoIpHolder {
    private static final Logger log = LoggerFactory.getLogger(GeoIpHolder.class);
    private String countryIso = "";
    private String countryName = "";
    private String cityName = "";

    private Country country;
    private Country countryRegistered;
    private City city;

    public static GeoIpHolder build(CityResponse city){
        if (city == null){
            return null;
        }

        GeoIpHolder gholder = new GeoIpHolder();
        try {
            gholder.countryIso = city.getCountry().getIsoCode();
            gholder.countryName = city.getCountry().getName();
            gholder.country = city.getCountry();
            gholder.countryRegistered = city.getRegisteredCountry();
            gholder.cityName = city.getCity().getName();
        } catch(Exception e){
            log.error("Could not create geoip holder", e);
        }

        return gholder;
    }

    public static GeoIpHolder build(CountryResponse country) {
        if (country == null) {
            return null;
        }

        GeoIpHolder gholder = new GeoIpHolder();
        try {
            gholder.countryIso = country.getCountry().getIsoCode();
            gholder.countryName = country.getCountry().getName();
            gholder.country = country.getCountry();
            gholder.countryRegistered = country.getRegisteredCountry();
        } catch(Exception e){
            log.error("Could not create geoip holder", e);
        }

        return gholder;
    }

    @Override
    public String toString() {
        if (StringUtils.isEmpty(cityName)){
            return "" + countryName + " (" + countryIso + ")";
        }

        return "" + countryName + " (" + countryIso + "), " + cityName;
    }
}
