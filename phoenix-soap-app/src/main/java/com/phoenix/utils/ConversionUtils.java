package com.phoenix.utils;

import com.phoenix.db.ContactGroup;
import com.phoenix.db.PairingRequest;
import com.phoenix.db.extra.PairingRequestResolution;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.soap.beans.Cgroup;
import com.phoenix.soap.beans.PairingRequestElement;
import com.phoenix.soap.beans.PairingRequestResolutionEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dusanklinec on 18.08.15.
 */
public class ConversionUtils {
    private static final Logger log = LoggerFactory.getLogger(ConversionUtils.class);

    /**
     * Returns pairing request element to return to the user from the database entry.
     * @param pr
     * @return
     */
    public static PairingRequestElement pairingRequestDbToElement(PairingRequest pr){
        PairingRequestElement elem = new PairingRequestElement();
        elem.setId(pr.getId());
        elem.setOwner(pr.getToUser());
        elem.setTstamp(pr.getTstamp().getTime());

        elem.setFromUser(pr.getFromUser());
        elem.setFromUserResource(pr.getFromUserResource());
        elem.setFromUserAux(StringUtils.isEmpty(pr.getFromUserAux()) ? null : pr.getFromUserAux());

        elem.setRequestMessage(StringUtils.isEmpty(pr.getRequestMessage()) ? null : pr.getRequestMessage());
        elem.setRequestAux(StringUtils.isEmpty(pr.getRequestAux()) ? null : pr.getRequestAux());

        final PairingRequestResolution res = pr.getResolution();
        if (res == PairingRequestResolution.NONE){
            elem.setResolution(PairingRequestResolutionEnum.NONE);
        } else if (res == PairingRequestResolution.ACCEPTED){
            elem.setResolution(PairingRequestResolutionEnum.ACCEPTED);
        } else if (res == PairingRequestResolution.DENIED){
            elem.setResolution(PairingRequestResolutionEnum.DENIED);
        } else if (res == PairingRequestResolution.BLOCKED){
            elem.setResolution(PairingRequestResolutionEnum.BLOCKED);
        } else if (res == PairingRequestResolution.REVERTED){
            elem.setResolution(PairingRequestResolutionEnum.REVERTED);
        } else {
            log.error("Unknown resolution from database");
            elem.setResolution(PairingRequestResolutionEnum.NONE);
        }

        elem.setResolutionResource(StringUtils.isEmpty(pr.getResolutionResource()) ? null : pr.getResolutionResource());
        elem.setResolutionTstamp(pr.getResolutionTstamp() == null ? null : pr.getResolutionTstamp().getTime());
        elem.setResolutionMessage(StringUtils.isEmpty(pr.getResolutionMessage()) ? null : pr.getResolutionMessage());
        elem.setResolutionAux(StringUtils.isEmpty(pr.getResolutionAux()) ? null : pr.getResolutionAux());
        return elem;
    }

    /**
     * Converts request resolution to DB resolution.
     * @param res
     * @return
     */
    public static PairingRequestResolution getDbResolutionFromRequest(PairingRequestResolutionEnum res){
        switch(res){
            case NONE:     return PairingRequestResolution.NONE;
            case ACCEPTED: return PairingRequestResolution.ACCEPTED;
            case DENIED:   return PairingRequestResolution.DENIED;
            case BLOCKED:  return PairingRequestResolution.BLOCKED;
            case REVERTED: return PairingRequestResolution.REVERTED;
            default:       return null;
        }
    }

    /**
     * Converts database representation of the contact group to the response representation.
     * @param cgroup
     * @return
     */
    public static Cgroup dbCgroupToResponse(ContactGroup cgroup){
        if (cgroup == null){
            return null;
        }

        final Cgroup cg = new Cgroup();
        cg.setId(cgroup.getId());
        cg.setOwner(cgroup.getOwner() == null ? "" : PhoenixDataService.getSIP(cgroup.getOwner()));
        cg.setGroupKey(cgroup.getGroupKey());
        cg.setGroupType(cgroup.getGroupType());
        cg.setGroupName(cgroup.getGroupName());
        cg.setAuxData(cgroup.getAuxData());
        return cg;
    }
}
