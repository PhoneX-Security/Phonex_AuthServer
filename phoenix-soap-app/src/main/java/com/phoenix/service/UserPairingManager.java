package com.phoenix.service;

import com.phoenix.db.PairingRequest;
import com.phoenix.db.RemoteUser;
import com.phoenix.db.extra.PairingRequestResolution;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.utils.MiscUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.*;
import java.util.*;

/**
 * Pairing request manager.
 * Created by dusanklinec on 19.08.15.
 */
@Service
@Repository
public class UserPairingManager {
    private static final Logger log = LoggerFactory.getLogger(UserPairingManager.class);

    public static final int INSERT_PAIRING_SUCCESS = 0;
    public static final int INSERT_PAIRING_ALREADY_IN_CONTACTS = -10;
    public static final int INSERT_PAIRING_EXISTS_BLOCKED = -12;
    public static final int INSERT_PAIRING_EXISTS_ACCEPTED = -13;
    public static final int INSERT_PAIRING_EXISTS_NONE = -14;

    @Autowired
    private PhoenixDataService dataService;

    @Autowired
    private AMQPListener amqpListener;

    @PostConstruct
    public synchronized void init() {
        log.info("Initializing UserPairingManager");
    }

    @PreDestroy
    public synchronized void deinit(){
        log.info("Shutting down UserPairingManager");
    }

    /**
     * Main handler for pairing request. User X removed user Y from its contact list.
     * Thus remove X request from the Y pairing request table, if Y is local.
     * Remove pairing request with resolution = none.
     *
     * @param xOwner
     * @param yUser
     */
    public void onUserXRemovedYFromContactlist(Subscriber xOwner, String yUser){
        final Subscriber yUserSub = dataService.getLocalUser(yUser);
        if (yUserSub != null){
            onUserXRemovedYFromContactlist(xOwner, yUserSub);
            return;
        }

        // Remote user?
        final RemoteUser yUserRemote = this.dataService.getRemoteUser(yUser);
        if (yUserRemote != null){
            onUserXRemovedYFromContactlist(xOwner, yUserRemote);
        }

        // Failure.
    }

    /**
     * User X removed local user Y from the contact list.
     *
     * @param xOwner
     * @param yUser
     */
    public void onUserXRemovedYFromContactlist(Subscriber xOwner, Subscriber yUser){
        // Remove X request from the Y pairing request table, Y is local.
        removePairingRequest(yUser, PhoenixDataService.getSIP(xOwner), true);
    }

    /**
     * User X removed remote user Y from the contact list.
     * For now, it is not implemented. We would need to call pairing request cancellation on the remote server.
     * This is left to the client.
     *
     * No other job is done in this scenario.
     *
     * @param xOwner
     * @param yUser
     */
    public void onUserXRemovedYFromContactlist(Subscriber xOwner, RemoteUser yUser){
        // Nothing to do. Removal of the remote request is not supported on the server.
        // It is left up to the client.
    }

    /**
     * Main handler for pairing request. User X added Y to its contact list.
     *    a) set pairing request of Y to X as accepted, if exists.
     *    b) insert a new pairing request of X to Y pairing database.
     *       If local/remote, insertion if already in contact list returns failure.
     *       If user is remote, no action is performed for now.
     *    c) delete all denied && blocked pairing requests of Y to X.
     *
     * @param xOwner
     * @param yUser
     */
    public void onUserXAddedYToContactlist(Subscriber xOwner, String yUser){
        final Subscriber yUserSub = dataService.getLocalUser(yUser);
        if (yUserSub != null){
            onUserXAddedYToContactlist(xOwner, yUserSub);
            return;
        }

        // Remote user?
        final RemoteUser yUserRemote = this.dataService.getRemoteUser(yUser);
        if (yUserRemote != null){
            onUserXAddedYToContactlist(xOwner, yUserRemote);
        }

        // Failure.
    }

    public void onUserXAddedYToContactlist(Subscriber xOwner, Subscriber yUser){
        // Set pairing request of Y to X as accepted, if exists.
        acceptPairingRequest(xOwner, PhoenixDataService.getSIP(yUser));

        // Insert a new pairing request of X to Y pairing database, if does not exist already. Check.
        // If blocked, insert nothing.
        insertPairingRequest(xOwner, yUser, true, null);

        // delete all denied && blocked pairing requests of Y to X.
        ArrayList<String> criteria = new ArrayList<String>(1);
        Map<String, Object> params = new HashMap<String, Object>();
        criteria.add("pr.resolution=:resblock OR pr.resolution=:resdenied");
        params.put("resblock", PairingRequestResolution.BLOCKED);
        params.put("resdenied", PairingRequestResolution.DENIED);
        removePairingRequest(xOwner, PhoenixDataService.getSIP(yUser), criteria, params);
    }

    /**
     * User X removed remote user Y from the contact list.
     * Job b - inserting a new pairing request is not handled here since we would need to call pairing request on the remote server.
     * This is left to the client.
     *
     * Job a - accepting existing pairing request is done here.
     *
     * @param xOwner
     * @param yUser
     */
    public void onUserXAddedYToContactlist(Subscriber xOwner, RemoteUser yUser){
        // Set pairing request of Y to X as accepted, if exists.
        acceptPairingRequest(xOwner, yUser.getSip());

        // In remote scenario we do not place insert request on the remote server,
        // left for the user. Nothing to do more.
    }

    /**
     * Sets pairing request from user fromUser to xOwner's pairing database to accepted, if exists.
     * @param xOwner
     * @param fromUser
     */
    @Transactional
    public int acceptPairingRequest(Subscriber xOwner, String fromUser){
        return acceptPairingRequest(xOwner, fromUser, new ResolutionDetails(PairingRequestResolution.ACCEPTED));
    }

    /**
     * Sets pairing request from user fromUser to xOwner's pairing database to accepted, if exists.
     * @param xOwner
     * @param fromUser
     */
    @Transactional
    public int acceptPairingRequest(Subscriber xOwner, String fromUser, ResolutionDetails details){
        final String updateQueryTpl = "UPDATE pairingRequest pr SET %s " +
                " WHERE pr.toUser=:toUser AND pr.fromUser=:fromUser AND resolution=:resnone";

        final String ownerSip = PhoenixDataService.getSIP(xOwner);
        final Map<String, Object> params = new HashMap<String, Object>();
        final List<String> updates = new LinkedList<String>();

        // Where parameters.
        params.put("toUser", ownerSip);
        params.put("fromUser", fromUser);
        params.put("resnone", PairingRequestResolution.NONE);

        // Update parameters.
        // Resolution is for sure.
        updates.add("pr.resolution=:resolution");
        params.put("resolution", details);

        // Timestamp?
        Date tstamp = details.getResolutionTstamp() != null ? details.getResolutionTstamp() : new Date();
        updates.add("pr.resolutionTstamp=:tstamp");
        params.put("tstamp", tstamp);

        // Resource
        if (details.getResolutionResource() != null){
            updates.add("pr.resolutionResource=:res");
            params.put("res", details.getResolutionResource());
        }

        // Message
        if (details.getResolutionMessage() != null){
            updates.add("pr.resolutionMessage=:msg");
            params.put("msg", details.getResolutionMessage());
        }

        // Aux
        if (details.getResolutionAux() != null){
            updates.add("pr.resolutionAux=:aux");
            params.put("aux", details.getResolutionAux());
        }

        // Join updates together.
        try {
            final String updSql = MiscUtils.join(updates, ", ");
            final String querySql = String.format(updateQueryTpl, updSql);
            final Query query = dataService.createQuery(querySql);
            dataService.setQueryParameters(query, params);
            return dataService.update(query);

        } catch(Exception e){
            log.error("Cannot accept pairing request, exception.", e);
        }

        return -1;
    }

    protected boolean isInContactList(Subscriber xOwner, Subscriber fromUser){
        return dataService.getContactlistForSubscriber(xOwner, fromUser) != null;
    }

    protected boolean isInContactList(Subscriber xOwner, String fromUser){
        return dataService.getContactlistForSubscriber(xOwner, fromUser) != null;
    }

    /**
     * Broadcasts pairing request change push event.
     * @param owner
     * @param timestamp
     */
    public void broadcastPairingChange(String owner, long timestamp){
        try {
            amqpListener.pushPairingRequestCheck(owner, timestamp);
        } catch (Exception ex) {
            log.error("Error in pushing pairing request check event", ex);
        }
    }

    /**
     * Inserts a new pairing request to the database according to the criteria.
     * Inserts pairing request from user fromUser to the to xOwner pairing database.
     *
     * If fromUser is already in xOwner's contact list, no request is placed.
     * If there is already a request with blocked / accepted / none, nothing is inserted.
     * If there is a request with denied, the previous request is deleted and new is placed.
     *
     * @param xOwner
     * @param fromUserSub
     * @param aux has to ne new object. User can specify aux data for the insertion.
     * @return
     */
    public int insertPairingRequest(Subscriber xOwner, Subscriber fromUserSub, boolean bcastPush, PairingRequest aux){
        if (isInContactList(xOwner, fromUserSub)){
            return INSERT_PAIRING_ALREADY_IN_CONTACTS;
        }

        return insertPairingRequestAfterClistCheck(xOwner, PhoenixDataService.getSIP(fromUserSub), bcastPush, aux);
    }

    /**
     * Inserts pairing request.
     * @param xOwner
     * @param fromUser
     * @param bcastPush
     * @param aux
     * @return
     */
    @Transactional
    public int insertPairingRequest(Subscriber xOwner, String fromUser, boolean bcastPush, PairingRequest aux){
        if (isInContactList(xOwner, fromUser)){
            return INSERT_PAIRING_ALREADY_IN_CONTACTS;
        }

        return insertPairingRequestAfterClistCheck(xOwner, fromUser, bcastPush, aux);
    }

    /**
     * Inserts new pairing request assuming contact list check was performed before.
     * Should not be called by the app code, used only internally.
     *
     * @param xOwner
     * @param fromUser
     * @param bcastPush
     * @param aux
     * @return
     */
    @Transactional
    protected int insertPairingRequestAfterClistCheck(Subscriber xOwner, String fromUser, boolean bcastPush, PairingRequest aux){
        final String toUser = PhoenixDataService.getSIP(xOwner);
        //
        // Fetch previous pairing request.
        // User might be deleted from the contact list. In this case, there should be no pairing request stored in the database
        // and caller might be able to ask for adding again.
        // On the other hand, there might be blocking resolution stored, which blocks user from further requests.
        //
        final String prevRequestQuerySql = "SELECT pr FROM pairingRequest pr " +
                " WHERE fromUser=:fromUser AND toUser=:toUser " +
                " ORDER BY tstamp DESC";

        TypedQuery<PairingRequest> prevRequestQuery = dataService.createQuery(prevRequestQuerySql, PairingRequest.class);
        prevRequestQuery.setParameter("fromUser", fromUser)
                .setParameter("toUser", toUser)
                .setMaxResults(1);

        PairingRequest prevReq = prevRequestQuery.getSingleResult();

        // If the previous pairing request is blocked, block further requests.
        if (prevReq != null){
            final PairingRequestResolution resolution = prevReq.getResolution();
            if (resolution == PairingRequestResolution.BLOCKED){
                return UserPairingManager.INSERT_PAIRING_EXISTS_BLOCKED;
            }

            // If resolution is accepted, the contact should be already in the contact list of the callee.
            if (resolution == PairingRequestResolution.ACCEPTED){
                return UserPairingManager.INSERT_PAIRING_EXISTS_ACCEPTED;
            }

            // If resolution is none, it was still not handled, do nothing.
            // It might be in the processing right now, no modification.
            if (resolution == PairingRequestResolution.NONE){
                return UserPairingManager.INSERT_PAIRING_EXISTS_NONE;
            }

            // If resolution is denied, remove old request and place a new one. Same with reverted case
            if (resolution == PairingRequestResolution.DENIED || resolution == PairingRequestResolution.REVERTED){
                dataService.remove(prevReq, false);
            }
        }

        // Create a new pairing request and insert it to the database.
        final Date tstamp = new Date();
        PairingRequest newPr = aux != null ? aux : new PairingRequest();

        // To & from are mandatory. Set here.
        newPr.setToUser(toUser);
        newPr.setFromUser(fromUser);

        // Timestamp is current if was not specified by aux.
        if (newPr.getTstamp() == null) {
            newPr.setTstamp(tstamp);
        }

        // Resolution is by default NONE. Cannot be overriden.
        newPr.setResolution(PairingRequestResolution.NONE);
        dataService.persist(newPr);

        // Broadcast new push message, pairing request records were changed.
        if (bcastPush) {
            broadcastPairingChange(toUser, System.currentTimeMillis());
        }

        return INSERT_PAIRING_SUCCESS;
    }

    /**
     * Removes pairing request from xOwners pairing request table from fromUser.
     * @param xOwner
     * @param fromUser
     * @param onlyWithNoneResolution if true, only pairing requests with resolution = none are going to be deleted.
     * @return
     */
    public int removePairingRequest(Subscriber xOwner, String fromUser, boolean onlyWithNoneResolution){
        if (!onlyWithNoneResolution){
            return removePairingRequest(xOwner, fromUser, null, null);
        }

        ArrayList<String> criteria = new ArrayList<String>();
        Map<String, Object> params = new HashMap<String, Object>();

        criteria.add("pr.resolution=:resnone");
        params.put("resnone", PairingRequestResolution.NONE);
        return removePairingRequest(xOwner, fromUser, criteria, params);
    }

    /**
     * Removes pairing request from xOwners pairing request table from fromUser.
     * @param xOwner
     * @param fromUser
     * @param criteria
     * @param auxParams
     *
     * @return
     */
    @Transactional
    public int removePairingRequest(Subscriber xOwner, String fromUser, Collection<String> criteria, Map<String, Object> auxParams){
        final String ownerSip = PhoenixDataService.getSIP(xOwner);
        final Map<String, Object> params = new HashMap<String, Object>();

        // Where parameters.
        params.put("toUser", ownerSip);
        params.put("fromUser", fromUser);

        String delQuery = "DELETE FROM pairingRequest pr WHERE pr.toUser=:toUser AND pr.fromUser=:fromUser";

        // Criteria & auxParams.
        if (criteria != null && !criteria.isEmpty()){
            delQuery = delQuery + " AND (( " + MiscUtils.join(criteria, " ) AND ( ") + " ))";
            params.putAll(auxParams);
        }

        // Join updates together.
        try {
            final Query query = dataService.createQuery(delQuery);
            dataService.setQueryParameters(query, params);
            return dataService.update(query);

        } catch(Exception e){
            log.error("Cannot accept pairing request, exception.", e);
        }

        return -1;
    }

    /**
     * Optional holder for specifying resolution details for the pairing request.
     */
    public static class ResolutionDetails {
        private PairingRequestResolution resolution;

        // Optional parts.
        private String resolutionResource;
        private Date resolutionTstamp;
        private String resolutionMessage;
        private String resolutionAux;

        public ResolutionDetails() {
        }

        public ResolutionDetails(PairingRequestResolution resolution) {
            this.resolution = resolution;
        }

        public PairingRequestResolution getResolution() {
            return resolution;
        }

        public void setResolution(PairingRequestResolution resolution) {
            this.resolution = resolution;
        }

        public String getResolutionResource() {
            return resolutionResource;
        }

        public void setResolutionResource(String resolutionResource) {
            this.resolutionResource = resolutionResource;
        }

        public Date getResolutionTstamp() {
            return resolutionTstamp;
        }

        public void setResolutionTstamp(Date resolutionTstamp) {
            this.resolutionTstamp = resolutionTstamp;
        }

        public String getResolutionMessage() {
            return resolutionMessage;
        }

        public void setResolutionMessage(String resolutionMessage) {
            this.resolutionMessage = resolutionMessage;
        }

        public String getResolutionAux() {
            return resolutionAux;
        }

        public void setResolutionAux(String resolutionAux) {
            this.resolutionAux = resolutionAux;
        }
    }

}
