package com.phoenix.geoip;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;

/**
 * Created by dusanklinec on 05.01.16.
 */
@Service
@Repository
@Scope(value = "singleton")
public class GeoIpManager {
    private static final Logger log = LoggerFactory.getLogger(GeoIpManager.class);

    private long lastGeoIpRefreshTime = 0;
    private final String geoIpCityDb = "/opt/geoip/GeoLite2-City.mmdb";
    private DatabaseReader geoipReader;

    /**
     * Translates IP address to GeoIP record.
     * @param ip
     * @return
     */
    public GeoIpHolder getGeoIp(String ip){
        try {
            final long curTime = System.currentTimeMillis();
            if (geoipReader == null || (curTime - lastGeoIpRefreshTime) > 1000*60*10){
                geoipReader = new DatabaseReader.Builder(new File(geoIpCityDb)).build();
                lastGeoIpRefreshTime = curTime;
            }

            final InetAddress addr = InetAddress.getByName(ip);
            final CityResponse city = geoipReader.city(addr);
            if (city != null){
                return GeoIpHolder.build(city);
            }

            return GeoIpHolder.build(geoipReader.country(addr));

        } catch (Exception e) {
            log.error("Exception in geoip translation, make sure " + geoIpCityDb + " exists.", e);
        }

        return null;
    }
}
