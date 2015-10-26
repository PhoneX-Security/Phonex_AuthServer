package com.phoenix.accounting;

import com.phoenix.db.AccountingAggregated;
import com.phoenix.db.AccountingLog;
import com.phoenix.db.AccountingPermission;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.AMQPListener;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.soap.beans.AccountingFetchRequest;
import com.phoenix.soap.beans.AccountingFetchResponse;
import com.phoenix.soap.beans.AccountingSaveRequest;
import com.phoenix.soap.beans.AccountingSaveResponse;
import com.phoenix.utils.JSONHelper;
import com.phoenix.utils.MiscUtils;
import com.phoenix.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.TypedQuery;
import java.util.*;

/**
 * Manager for accounting logic, e.g., storing accounting logs.
 *
 * Created by dusanklinec on 25.09.15.
 */
@Service
@Repository
public class AccountingManager {
    private static final Logger log = LoggerFactory.getLogger(AccountingManager.class);

    /**
     * Main key for storing accounting logs.
     */
    private static final String REQ_BODY_STORE = "astore";

    /**
     * User resource, identifies particular device that made the action.
     */
    private static final String STORE_USER_RESOURCE = "res";

    /**
     * Array of accounting actions to be persisted.
     */
    private static final String STORE_RECORDS = "records";

    /**
     * Action type. String key of the action to store. e.g., c.os for call.outgoing seconds.
     */
    private static final String STORE_ACTION_TYPE = "type";

    /**
     * Accounting action ID, millisecond timestamp of the action.
     */
    private static final String STORE_ACTION_ID = "aid";

    /**
     * Monotonously increasing counter for accounting ID, valid for user:resource.
     * Used to distinguish several actions that were created in the same time.
     * STORE_ACTION_ID:STORE_ACTION_COUNTER form the unique key for accounting action for user:resource.
     */
    private static final String STORE_ACTION_COUNTER = "ctr";

    /**
     * Number of units associated with the accounting action, e.g. number of seconds of outgoing call, number of messages.
     */
    private static final String STORE_ACTION_VOLUME = "vol";

    /**
     * Accounting action reference. Idea is to hash e.g., callee. So we can possibly match accounting actions with destinations.
     */
    private static final String STORE_ACTION_REFERENCE = "ref";

    /**
     * Aggregate record indicator. If set to >1 it means current records represents a group of individual accounting
     * actions. aid is ID of the last aggregated record. Number corresponds to the number of records in the aggregation period.
     */
    private static final String STORE_ACTION_AGGREGATED = "ag";

    /**
     * Used for aggregated records to indicate aggregation ID start.
     */
    private static final String STORE_ACTION_ID_BEGINNING = "aidbeg";

    private static final String STORE_PERMISSION = "perm";
    private static final String STORE_PERMISSION_LICENSE_ID = "licId";
    private static final String STORE_PERMISSION_ID = "id";

    /**
     * When responding to the store request, this field holds the newest accounting action ID processed.
     * It serves as an ACK to the client that the upload was successful.
     */
    private static final String STORE_RESP_TOP_ACTION_ID = "topaid";

    /**
     * When responding to the store request, this field holds the newest accounting action counter processed.
     * It serves as an ACK to the client that the upload was successful.
     */
    private static final String STORE_RESP_TOP_ACTION_COUNTER = "topctr";

    /**
     * Number of affected aggregate records when merging to aggregate table.
     */
    private static final String STORE_RESP_AG_AFFECTED = "agaffected";

    /**
     * Fetch request main container.
     */
    private static final String FETCH_REQUEST = "areq";

    /**
     * Resource of the user asking for fetch.
     */
    private static final String FETCH_REQUEST_RESOURCE = "res";

    /**
     * Type of the record user is asking for.
     * Can be "aggregate" or "arecords".
     */
    private static final String FETCH_REQUEST_TYPE = "type";
    private static final String FETCH_REQUEST_TYPE_AGGREGATE = "aaggregate";
    private static final String FETCH_REQUEST_TYPE_RECORDS = "arecords";
    private static final String FETCH_REQUEST_TYPE_NONE = "none";

    /**
     * Flags can be specified in store request to return affected permissions / aggregations.
     */
    private static final String FETCH_REQUEST_PERMISSIONS = "permissions";
    private static final String FETCH_REQUEST_AGGREGATIONS = "aggregations";

    /**
     * Time interval of the records to be fetched.
     */
    private static final String FETCH_REQUEST_TIME_FROM = "timefrom";
    private static final String FETCH_REQUEST_TIME_TO = "timeto";

    /**
     * User can specify to fetch only a particular types of actions
     */
    private static final String FETCH_REQUEST_ATYPE = "atype";

    /**
     * List of resources that user want to fetch recors for.
     */
    private static final String FETCH_REQUEST_ARESOURCES = "ares";

    /**
     * Fetch response block key.
     */
    private static final String FETCH_RESP_BODY = "afetch";
    private static final String FETCH_RESP_RECORDS = "records";

    @Autowired
    private PhoenixDataService dataService;

    @Autowired
    private AMQPListener amqpListener;

    @PostConstruct
    public synchronized void init() {
        log.info("Initializing AccountingManager");
    }

    @PreDestroy
    public synchronized void deinit(){
        log.info("Shutting down AccountingManager");
    }

    /**
     * Processing a general fetch request - basic entry point.
     * User may fetch several data record types. The default one is aggregate report.
     *
     * {"areq:"{
     *      "res":"abcdef123",
     *      "permissions":1,      (optional, if set to 1 current set of permission counters is fetched).
     *      "permissionsIds":[{"id":1,"licId":123}], (optional, particular set of permission IDs to fetch)
     *      "ares":["abcdef123"], (optional)
     *      "type":"aaggregate",  (optional, default is agregate. or: "arecords", or "none"),
     *      "timefrom":12335522,  (optional)
     *      "timeto":215241234,   (optional)
     *      "atype":["c.os"]      (optional)
     * }}
     *
     * Response:{"afetch":{
     *      records:[{
     *          "res":"abcdef123",              (resource)
     *          "type": "c.os",
     *          "akey": "ar3jdDJ2910sa212d==",  (24char len, base64-encoded, aggregation bucket key)
     *          "dcreated": 1443472673397,      (date created, UTC milli)
     *          "dmodif": 1443472673397,        (date modified, UTC milli)
     *          "vol": 35,                      (aggregated value of the counter for given user:type)
     *          "aidFst": 1443472670397,        (first accounting log ID in this aggregate record)
     *          "ctrFst": 1,                    (first accounting log counter in this aggregate record)
     *          "aidLst": 1443472672397         (last accounting log ID in this aggregate record)
     *          "ctrLst": 13,                   (last accounting log counter in this aggregate record)
     *          "aperiod": 3600000,             (aggregation period of this record, in milliseconds)
     *          "acount": 5,                    (number of logs aggregated in this record)
     *          "astart": 144347260000          (start of the aggregation interval)
     *          "aref": "ed4b607e48009a34d0b79fe70f521cde"  (optional: reference of the accounting ID, if applicable)
     *      }, {rec_2},{rec_3},...,{rec_n}],
     *
     *      permissions:[{
     *          "id": 123,              (internal DB ID)
     *          "permId": 1,            (permission ID)
     *          "licId": 133            (license ID)
     *          "name": "outgoing_calls_seconds"
     *          "akey": "ajkasb901ns02==" (aggregation key, can be ignored)
     *          "dcreated": 1443472673397,
     *          "dmodif": 1443472673397,
     *          "vol": 320,             (current value of the permission counter)
     *          "aidFst": 1443472670397,
     *          "ctrFst": 1,
     *          "aidLst": 1443472672397,
     *          "ctrLst": 13,
     *          "acount": 5             (number of log records aggregated in this permission counter)
     *      }, {permission_2}, {permission_3}, ..., {permission_m}]
     * }}
     *
     * @param caller
     * @param request
     * @param response
     */
    public void processFetchRequest(Subscriber caller, AccountingFetchRequest request, AccountingFetchResponse response) throws JSONException {
        final String reqBody = request.getRequestBody();
        final JSONObject jReq = new JSONObject(reqBody);
        if (!jReq.has(FETCH_REQUEST)){
            log.warn("Unknown request, body not present");
            response.setErrText("Unknown request, body not present");
            response.setErrCode(-2);
            return;
        }

        final JSONObject fetchReq = jReq.getJSONObject(FETCH_REQUEST);
        final String type = fetchReq.has(FETCH_REQUEST_TYPE) ? fetchReq.getString(FETCH_REQUEST_TYPE) : FETCH_REQUEST_TYPE_AGGREGATE;
        if (!FETCH_REQUEST_TYPE_AGGREGATE.equalsIgnoreCase(type) && !FETCH_REQUEST_TYPE_RECORDS.equalsIgnoreCase(type)){
            log.warn("Given type not supported: " + type);
            response.setErrText("Type unrecognized");
            response.setErrCode(-3);
            return;
        }

        // TODO: implement type fetch.
        if (FETCH_REQUEST_TYPE_RECORDS.equalsIgnoreCase(type)){
            log.warn("Not implemented yet");
            response.setErrText("Not implemented yet");
            response.setErrCode(-4);
            return;
        }

        final JSONObject jsonResponse = new JSONObject();
        fetchAggregate(caller, fetchReq, request, response, jsonResponse);
        fetchPermissions(caller, fetchReq, request, response, jsonResponse);

        // Build JSON response.
        response.setAuxJSON(jsonResponse.toString());
    }

    /**
     * Sub call to fetch all aggregate records.
     * @param caller
     * @param fetchReq
     * @param request
     * @param response
     * @param jsonResponse
     * @throws JSONException
     */
    protected void fetchAggregate(Subscriber caller, JSONObject fetchReq,
                                  AccountingFetchRequest request, AccountingFetchResponse response,
                                  JSONObject jsonResponse) throws JSONException
    {
        Long timeFrom = fetchReq.has(FETCH_REQUEST_TIME_FROM) ? MiscUtils.getAsLong(fetchReq, FETCH_REQUEST_TIME_FROM) : null;
        Long timeTo = fetchReq.has(FETCH_REQUEST_TIME_TO) ? MiscUtils.getAsLong(fetchReq, FETCH_REQUEST_TIME_TO) : null;
        try {
            // Default timespan for fetching aggregate data is last 2 months.
            if (timeFrom == null){
                timeFrom = System.currentTimeMillis() - 1000l*60l*60l*24l*62l;
            }

            ArrayList<String> typesList = null;
            ArrayList<String> resList = null;

            // Only interested in particular types?
            if (fetchReq.has(FETCH_REQUEST_ATYPE)){
                final JSONArray atypes = fetchReq.getJSONArray(FETCH_REQUEST_ATYPE);
                typesList = JSONHelper.jsonStringArrayToList(atypes);
            }

            // Only for particular resources?
            if (fetchReq.has(FETCH_REQUEST_ARESOURCES)){
                final JSONArray atypes = fetchReq.getJSONArray(FETCH_REQUEST_ARESOURCES);
                resList = JSONHelper.jsonStringArrayToList(atypes);
            }

            final List<AccountingAggregated> resultList = fetchAggregate(caller, timeFrom, timeTo, typesList, resList);
            final JSONArray resultArray = new JSONArray();
            for(AccountingAggregated curAg : resultList){
                resultArray.put(aggregatedRecordToJson(curAg));
            }

            // Response.
            final JSONObject storeResp = jsonResponse.has(FETCH_RESP_BODY) ? jsonResponse.getJSONObject(FETCH_RESP_BODY) : new JSONObject();
            storeResp.put(FETCH_RESP_RECORDS, resultArray);
            jsonResponse.put(FETCH_RESP_BODY, storeResp);

        } catch(Exception e){
            log.error("Exception when fetching aggregate data", e);
            response.setErrText("DB exception");
            response.setErrCode(-10);
        }
    }

    /**
     * Sub call to fetch all permissions records.
     * @param caller
     * @param fetchReq
     * @param request
     * @param response
     * @param jsonResponse
     * @throws JSONException
     */
    protected void fetchPermissions(Subscriber caller, JSONObject fetchReq,
                                  AccountingFetchRequest request, AccountingFetchResponse response,
                                  JSONObject jsonResponse) throws JSONException
    {
        if (!fetchReq.has(FETCH_REQUEST_PERMISSIONS) || !MiscUtils.getAsBoolean(fetchReq, FETCH_REQUEST_PERMISSIONS)){
            return;
        }

        try {

            final List<AccountingPermission> resultList = fetchPermissions(caller, null, null);
            final JSONArray resultArray = new JSONArray();
            for(AccountingPermission curPerm : resultList){
                resultArray.put(permissionRecordToJson(curPerm));
            }

            // Response.
            final JSONObject storeResp = jsonResponse.has(FETCH_RESP_BODY) ? jsonResponse.getJSONObject(FETCH_RESP_BODY) : new JSONObject();
            storeResp.put(FETCH_REQUEST_PERMISSIONS, resultArray);
            jsonResponse.put(FETCH_RESP_BODY, storeResp);

        } catch(Exception e){
            log.error("Exception when fetching aggregate data", e);
            response.setErrText("DB exception");
            response.setErrCode(-10);
        }
    }

    /**
     * Fetches aggregate records from database according to given criteria
     * @param caller
     * @param timeFrom
     * @param timeTo
     * @param typesList
     * @param resList
     * @return
     */
    protected List<AccountingAggregated> fetchAggregate(Subscriber caller, Long timeFrom, Long timeTo,
                                   List<String> typesList, List<String> resList){
        try {
            // Default timespan for fetching aggregate data is last 2 months.
            if (timeFrom == null){
                timeFrom = System.currentTimeMillis() - 1000l*60l*60l*24l*62l;
            }

            final String sqlFetch = "SELECT ag FROM AccountingAggregated ag WHERE ag.owner=:owner " +
                    " AND ag.aggregationStart >= :timeFrom ";

            final ArrayList<String> criteria = new ArrayList<String>(4);
            final Map<String, Object> args = new HashMap<String, Object>();
            args.put("owner", caller);
            args.put("timeFrom", timeFrom);

            // Limited to particular timeTo?
            if (timeTo != null){
                criteria.add("ag.aggregationStart <= :timeTo");
                args.put("timeTo", timeTo);
            }

            // Only interested in particular types?
            if (!MiscUtils.collectionIsEmpty(typesList)){
                criteria.add("ag.type IN :types");
                args.put("types", typesList);
            }

            // Only for particular resources?
            if (!MiscUtils.collectionIsEmpty(resList)){
                criteria.add("ag.resource IN :ares");
                args.put("ares", resList);
            }

            final String sql = dataService.buildQueryString(sqlFetch, criteria, " ORDER BY ag.aggregationStart, ag.type, ag.actionIdFirst ");
            final TypedQuery<AccountingAggregated> query = dataService.createQuery(sql, AccountingAggregated.class);
            dataService.setQueryParameters(query, args);
            final List<AccountingAggregated> resultList = query.getResultList();
            return resultList;

        } catch(Exception e){
            log.error("Exception when fetching aggregate data", e);
            return null;
        }
    }

    /**
     * Fetches permissions records from database according to given criteria
     * @param caller
     * @param timeFrom
     * @param idxs
     * @return
     */
    protected List<AccountingPermission> fetchPermissions(Subscriber caller, Long timeFrom, List<PermissionIdx> idxs){
        try {
            final String sqlFetch = "SELECT aper FROM AccountingPermission aper WHERE aper.owner=:owner";
            final ArrayList<String> criteria = new ArrayList<String>(4);
            final Map<String, Object> args = new HashMap<String, Object>();
            args.put("owner", caller);

            // Limited to particular timeTo?
            if (timeFrom != null){
                criteria.add("aper.dateModified >= :timeFrom");
                args.put("timeFrom", timeFrom);
            }

            // Only interested in particular types?
            if (!MiscUtils.collectionIsEmpty(idxs)){
                // Not implemented yet.
            }

            final String sql = dataService.buildQueryString(sqlFetch, criteria, " ORDER BY aper.licenseId, aper.permId ");
            final TypedQuery<AccountingPermission> query = dataService.createQuery(sql, AccountingPermission.class);
            dataService.setQueryParameters(query, args);
            final List<AccountingPermission> resultList = query.getResultList();
            return resultList;

        } catch(Exception e){
            log.error("Exception when fetching permissions data", e);
            return null;
        }
    }

    /**
     * Returns JSON representation of the aggregated record.
     * @param ag
     * @return
     */
    protected JSONObject aggregatedRecordToJson(AccountingAggregated ag) throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("res", ag.getResource());
        o.put("type", ag.getType());
        o.put("akey", ag.getAggregationKey());
        o.put("dcreated", ag.getDateCreated().getTime());
        o.put("dmodif", ag.getDateModified().getTime());
        o.put("vol", ag.getAmount());

        o.put("aidFst", ag.getActionIdFirst());
        o.put("ctrFst", ag.getActionCounterFirst());
        o.put("aidLst", ag.getActionIdLast());
        o.put("ctrLst", ag.getActionCounterLast());

        o.put("aperiod", ag.getAggregationPeriod());
        o.put("acount", ag.getAggregationCount());
        o.put("astart", ag.getAggregationStart());

        if (!StringUtils.isEmpty(ag.getAaref())) {
            o.put("aref", ag.getAaref());
        }

        return o;
    }

    /**
     * Returns JSON representation of the aggregated record.
     * @param perm
     * @return
     */
    protected JSONObject permissionRecordToJson(AccountingPermission perm) throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("id", perm.getId());
        o.put("permId", perm.getPermId());
        o.put("licId", perm.getLicenseId());
        o.put("name", perm.getName());

        o.put("akey", perm.getCacheKey());
        o.put("dcreated", perm.getDateCreated().getTime());
        o.put("dmodif", perm.getDateModified().getTime());
        o.put("vol", perm.getAmount());

        o.put("aidFst", perm.getActionIdFirst());
        o.put("ctrFst", perm.getActionCounterFirst());
        o.put("aidLst", perm.getActionIdLast());
        o.put("ctrLst", perm.getActionCounterLast());
        o.put("acount", perm.getAggregationCount());

        if (!StringUtils.isEmpty(perm.getAaref())) {
            o.put("aref", perm.getAaref());
        }

        return o;
    }

    /**
     * Processing save request - basic entry point.
     * Example JSON:
     * {"astore":{
     *     "res":"abcdef123",
     *      "permissions":1,      (optional, if set to 1 AFFECTED permissions are returned)
     *      "aggregate":1,        (optional, if set to 1 AFFECTED aggregate records are returned)
     *      "records":[
     *          {"type":"c.os", "aid":1443185424488, "ctr":1, "vol": "120", "ref":"ed4b607e48009a34d0b79fe70f521cde"},
     *          {"type":"c.os", "aid":1443185524488, "ctr":2, "vol": "10", "perm":{"licId":123, "permId":1}},
     *          {"type":"m.om", "aid":1443185624488, "ctr":3, "vol": "120", "perm":{"licId":123, "permId":2}},
     *          {"type":"m.om", "aid":1443185724488, "ctr":4, "vol": "10", "ag":1, "aidbeg":1443185724488},
     *          {"type":"f.id", "aid":1443185824488, "ctr":5, "vol": "1"}
     *     ]
     * }}
     *
     * Response: {
     *     "store":{
     *          "topaid":1443185824488,
     *          "topctr":5,
     *          "permissions:"[
     *              {permission_1}, {permission_2}, ..., {permission_m}
     *          ],
     *          "aggregate":[
     *              {ag_1,} {ag_2}, ..., {ag_n}
     *          ]
     *     }
     * }
     * @param request
     * @param response
     */
    public void processSaveRequest(Subscriber caller, AccountingSaveRequest request, AccountingSaveResponse response) throws JSONException {
        final String reqBody = request.getRequestBody();
        final JSONObject jReq = new JSONObject(reqBody);
        if (!jReq.has(REQ_BODY_STORE)){
            log.warn("Unknown request, body not present");
            response.setErrCode(-2);
            return;
        }

        final JSONObject storeReq = jReq.getJSONObject(REQ_BODY_STORE);
        final String resource = storeReq.getString(STORE_USER_RESOURCE);
        if (StringUtils.isEmpty(resource)){
            log.warn("Resource cannot be empty");
            response.setErrCode(-3);
            return;
        }

        final JSONArray records = storeReq.getJSONArray(STORE_RECORDS);
        if (records == null){
            log.warn("Null records array");
            response.setErrCode(-4);
            return;
        }

        final JSONObject jsonResponse = new JSONObject();
        store(caller, resource, records, request, response, storeReq, jsonResponse);

        // Build JSON response.
        response.setAuxJSON(jsonResponse.toString());
    }

    /**
     * Performs insertion of the user-supplied accounting logs records to the database with duplicate detection
     * and aggregation.
     *
     * Duplicate detection is made via rKey.
     *
     * @param caller
     * @param toInsert
     * @param semiAggregation
     */
    protected void persistAccountingLogMap(Subscriber caller,
                                           final Map<String, AccountingLog> toInsert,
                                           final Map<String, AccountingAggregated> semiAggregation,
                                           final Map<String, AccountingPermission> semiPermAggregation,
                                           final TopIdCounter topId)
    {
        if (toInsert.isEmpty()){
            return;
        }

        final Set<String> rkeys = toInsert.keySet();
        final int rkeysSize = rkeys.size();

        // Duplicate detection: Fetch all rkeys in the database that match given set.
        final String sqlDuplicate = "SELECT alog.rkey FROM AccountingLog alog WHERE alog.rkey IN :rkeys";
        final TypedQuery<String> query = dataService.createQuery(sqlDuplicate, String.class);
        query.setParameter("rkeys", new ArrayList<String>(rkeys));

        final List<String> resultList = query.getResultList();
        for(String rkey : resultList){
            toInsert.remove(rkey);
        }

        // Duplicates were removed, new processing.
        final int newRkeysSize = toInsert.size();
        if (rkeysSize != newRkeysSize){
            log.info(String.format("accounting log reduced from %d to %d", rkeysSize, newRkeysSize));
        }

        if (toInsert.isEmpty()){
            return;
        }

        int i = 0;
        for (AccountingLog alog : toInsert.values()) {
            i += 1;
            dataService.persist(alog, i >= newRkeysSize);

            // Request-wide aggregation for permissions.
            AccountingPermission curPerm = null;
            if (alog.getPermId() != null && alog.getLicenseId() != null){
                curPerm = new AccountingPermission();
                curPerm.setPermId(alog.getPermId());
                curPerm.setLicenseId(alog.getLicenseId());
                curPerm.setAggregationCount(1);
                curPerm.setAmount(alog.getAmount());

                curPerm.setOwner(alog.getOwner());
                curPerm.setAaref(alog.getAaref());

                curPerm.setActionIdFirst(alog.getActionId());
                curPerm.setActionCounterFirst(alog.getActionCounter());
                curPerm.setActionIdLast(alog.getActionId());
                curPerm.setActionCounterLast(alog.getActionCounter());

                final String cacheKey = getPermissionCacheKey(curPerm);
                curPerm.setCacheKey(cacheKey);
            }

            // Request-wide aggregation logic.
            final String aggregationKey = getAggregationCacheKey(alog);
            final AccountingAggregated curAg = new AccountingAggregated();

            curAg.setOwner(alog.getOwner());
            curAg.setResource(alog.getResource());
            curAg.setType(alog.getType());
            curAg.setDateCreated(alog.getDateCreated());
            curAg.setDateModified(alog.getDateCreated());

            curAg.setActionIdFirst(alog.getActionId());
            curAg.setActionCounterFirst(alog.getActionCounter());
            curAg.setActionIdLast(alog.getActionId());
            curAg.setActionCounterLast(alog.getActionCounter());

            curAg.setAaref(alog.getAaref());
            curAg.setAmount(alog.getAmount());

            curAg.setAggregationKey(aggregationKey);
            curAg.setAggregationCount(1);
            curAg.setAggregationPeriod(getDefaultAggregationIntervalSize(alog));
            curAg.setAggregationStart(getAggregationStart(alog, curAg.getAggregationPeriod()));

            if (!semiAggregation.containsKey(aggregationKey)){
                semiAggregation.put(aggregationKey, curAg);
            } else {
                final AccountingAggregated agRec = semiAggregation.get(aggregationKey);
                mergeAggregatedRecords(agRec, curAg);
            }

            if (curPerm != null){
                final String permCacheKey = curPerm.getCacheKey();
                if (semiPermAggregation.containsKey(permCacheKey)){
                    semiPermAggregation.put(permCacheKey, curPerm);
                } else {
                    final AccountingPermission pRec = semiPermAggregation.get(permCacheKey);
                    mergePermissionRecords(pRec, curPerm);
                }
            }

            // Remove, in case of an exception.
            toInsert.remove(alog.getRkey());
        }

        // Everything was processed, clear for next round / call.
        toInsert.clear();
    }

    /**
     * Performs storing of the log to the database.
     *
     * @param caller
     * @param resource
     * @param records
     * @param request
     * @param response
     * @param jsonResponse
     * @throws JSONException
     */
    protected void store(Subscriber caller, String resource, JSONArray records,
                         AccountingSaveRequest request, AccountingSaveResponse response,
                         final JSONObject storeReq, JSONObject jsonResponse) throws JSONException
    {
        final int recSize = records.length();
        final Date dateCreated = new Date();

        final Boolean returnPermissions = storeReq.has(FETCH_REQUEST_PERMISSIONS) ? MiscUtils.tryGetAsBoolean(storeReq, FETCH_REQUEST_PERMISSIONS) : null;
        final Boolean returnAggregations = storeReq.has(FETCH_REQUEST_AGGREGATIONS) ? MiscUtils.tryGetAsBoolean(storeReq, FETCH_REQUEST_AGGREGATIONS) : null;

        // Store newest accounting action id + counter.
        final TopIdCounter topId = new TopIdCounter();

        // Request-wide aggregation.
        final Map<String, AccountingAggregated> semiAggregation = new HashMap<String, AccountingAggregated>();

        // Request-wide aggregation for permission counters.
        final Map<String, AccountingPermission> semiPermAggregation = new HashMap<String, AccountingPermission>();

        // Ignore duplicated keys.
        final Map<String, AccountingLog> toInsert = new HashMap<String, AccountingLog>();
        for (int i = 0; i < recSize; i++) {
            final JSONObject curRec = records.getJSONObject(i);

            // Sanitization.
            if (!curRec.has(STORE_ACTION_TYPE)
                    || curRec.has(STORE_ACTION_ID)
                    || curRec.has(STORE_ACTION_COUNTER)
                    || curRec.has(STORE_ACTION_VOLUME)) {
                log.warn("Improperly formatted accounting action, missing some key attributes");
                continue;
            }

            final AccountingLog alog = new AccountingLog();
            alog.setDateCreated(dateCreated);
            alog.setOwner(caller);
            alog.setResource(resource);
            alog.setType(curRec.getString(STORE_ACTION_TYPE));
            alog.setActionId(MiscUtils.getAsLong(curRec, STORE_ACTION_ID));
            alog.setActionCounter(MiscUtils.getAsInteger(curRec, STORE_ACTION_COUNTER));
            alog.setAmount(MiscUtils.getAsLong(curRec, STORE_ACTION_VOLUME));
            alog.setAaref(curRec.has(STORE_ACTION_REFERENCE) ? curRec.getString(STORE_ACTION_REFERENCE) : null);
            alog.setAggregated(curRec.has(STORE_ACTION_AGGREGATED) ? MiscUtils.getAsInteger(curRec, STORE_ACTION_AGGREGATED) : 0);
            alog.setRkey(getAlogRkey(alog));

            // Permission counter association?
            if (curRec.has(STORE_PERMISSION)){
                final JSONObject permObj = curRec.getJSONObject(STORE_PERMISSION);
                final long permId = MiscUtils.getAsLong(permObj, STORE_PERMISSION_ID);
                final long licId = MiscUtils.getAsLong(permObj, STORE_PERMISSION_LICENSE_ID);
                alog.setPermId(permId);
                alog.setLicenseId(licId);
            }

            // Top processed action ID & counter.
            topId.insert(alog.getActionId(), alog.getActionCounter());

            // Insert for processing.
            final AccountingLog prev = toInsert.put(alog.getRkey(), alog);
            if (prev != null){
                log.warn(String.format("Warning, collision detected on accounting logs %s vs %s", prev, alog));
            }

            // Each x-cycles do the dump to database with collision check.
            if ((i % 25) == 0) {
                persistAccountingLogMap(caller, toInsert, semiAggregation, semiPermAggregation, topId);
            }
        }

        persistAccountingLogMap(caller, toInsert, semiAggregation, semiPermAggregation, topId);

        // Update aggregated records using semiAggregation.
        final int changed = mergeAggregationWithDB(semiAggregation);
        log.info(String.format("Aggregations updated to DB, records changed: %d", changed));

        final int changedPerms = mergePermissionsWithDB(semiPermAggregation);
        log.info(String.format("Permissions updated to DB, records changed: %d", changedPerms));

        // Response.
        final JSONObject storeResp = jsonResponse.has(REQ_BODY_STORE) ? jsonResponse.getJSONObject(REQ_BODY_STORE) : new JSONObject();

        // Returning affected permissions.
        if (returnPermissions != null && returnPermissions){
            final JSONArray permJson = new JSONArray();
            for (AccountingPermission permission : semiPermAggregation.values()) {
                permJson.put(permissionRecordToJson(permission));
            }

            storeResp.put(FETCH_REQUEST_PERMISSIONS, permJson);
        }

        // Returned affected aggregations
        if (returnAggregations != null && returnAggregations){
            final JSONArray agJson = new JSONArray();
            for (AccountingAggregated ag : semiAggregation.values()) {
                agJson.put(aggregatedRecordToJson(ag));
            }

            storeResp.put(FETCH_REQUEST_AGGREGATIONS, agJson);
        }

        // Aggregate caches now contain updated records from database, can put to response.
        storeResp.put(STORE_RESP_TOP_ACTION_ID, topId.getId());
        storeResp.put(STORE_RESP_TOP_ACTION_COUNTER, topId.getCtr());
        storeResp.put(STORE_RESP_AG_AFFECTED, changed);
        jsonResponse.put(REQ_BODY_STORE, storeResp);
    }

    /**
     * Merges a collection of aggregation records with those in database, persists changes to DB.
     * @param aggregations agKey -> agRecord mapping to be merged with the database records.
     * @return Number of changed & inserted records.
     */
    public int mergeAggregationWithDB(Map<String, AccountingAggregated> aggregations){
        // Get all aggregation records from DB.
        final String sqlFetch = "SELECT ag FROM AccountingAggregated ag WHERE ag.aggregationKey IN :keys";
        final TypedQuery<AccountingAggregated> query = dataService.createQuery(sqlFetch, AccountingAggregated.class);
        query.setParameter("keys", aggregations.keySet());

        int changed = 0;
        final Set<String> nonDbEntries = new HashSet<String>(aggregations.keySet());
        final List<AccountingAggregated> resultList = query.getResultList();
        for(AccountingAggregated ag : resultList){
            final String key = ag.getAggregationKey();

            // Remove aggregation keys from non-db entries set as it was found in the result set. Does not need to be inserted.
            nonDbEntries.remove(key);

            final int mergeResult = mergeAggregatedRecords(ag, aggregations.get(key));
            if (mergeResult < 0){
                // Could not merge, nothing to be done. Should not happen - assertion. Keys matches..
                continue;
            } else if (mergeResult == 0){
                // No change was made. No persistency is needed.
                continue;
            }

            // Change was made, persist a new record.
            dataService.persist(ag, false);
            // Update aggregation cache.
            aggregations.put(key, ag);
            changed += 1;
        }

        // Insert non-db entries to the database as a new ones.
        int newCtr = 0;
        final int newSize = nonDbEntries.size();
        for(String agKey : nonDbEntries){
            newCtr += 1;
            changed += 1;
            final AccountingAggregated ag = aggregations.get(agKey);
            ag.setDateCreated(new Date());
            ag.setDateCreated(new Date());
            dataService.persist(ag, newCtr >= newSize);
        }

        return changed;
    }

    /**
     * Merges aggregation records a1 and a2 together, if possible.
     * Merges a2 to a1 record. If no change was made, returns 0. On error or incompatibility, negative value is returned,
     * on a successful merge with change, positive non-zero value is returned.
     *
     * @param a1 Aggregation record that a2 will be merged into.
     * @param a2 Aggregation record which will be merged into a1.
     * @return
     */
    public int mergeAggregatedRecords(AccountingAggregated a1, AccountingAggregated a2){
        if (a1 == null && a2 == null){
            return -1;
        } else if (a1 != null && a2 == null){
            return 0;
        } else if (a1 == null){
            return 0;
        }

        // Test if can be merged, aggregation keys has to match.
        if (!a1.getAggregationKey().equals(a2.getAggregationKey())){
            return -1;
        }

        boolean changed = false;

        // Aggregation logic, add a2 -> a1 record.

        // First = keep minimal.
        if (compareIds(a1.getActionIdFirst(), a1.getActionCounterFirst(), a2.getActionIdFirst(), a2.getActionCounterFirst()) == 1){
            a1.setActionIdFirst(a2.getActionIdFirst());
            a1.setActionCounterFirst(a2.getActionCounterFirst());
            changed = true;
        }

        // Last = keep maximal.
        if (compareIds(a1.getActionIdLast(), a1.getActionCounterLast(), a2.getActionIdLast(), a2.getActionCounterLast()) == -1){
            a1.setActionIdLast(a2.getActionIdLast());
            a1.setActionCounterLast(a2.getActionCounterLast());
            changed = true;
        }

        if (a2.getAggregationCount() > 0) {
            a1.setAggregationCount(a1.getAggregationCount() + a2.getAggregationCount());
            changed = true;
        }

        if (a2.getAmount() > 0) {
            a1.setAmount(a1.getAmount() + a2.getAmount());
            changed = true;
        }

        if (changed){
            a1.setDateModified(new Date());
        }

        return changed ? 1 : 0;
    }

    /**
     * Merges a collection of permissions records with those in database, persists changes to DB.
     * @param permissions key -> permRecord mapping to be merged with the database records.
     * @return Number of changed & inserted records.
     */
    public int mergePermissionsWithDB(Map<String, AccountingPermission> permissions){
        // Get all aggregation records from DB.
        final String sqlFetch = "SELECT aperm FROM AccountingPermission aperm WHERE aperm.cacheKey IN :keys";
        final TypedQuery<AccountingPermission> query = dataService.createQuery(sqlFetch, AccountingPermission.class);
        query.setParameter("keys", permissions.keySet());

        int changed = 0;
        final Set<String> nonDbEntries = new HashSet<String>(permissions.keySet());
        final List<AccountingPermission> resultList = query.getResultList();
        for(AccountingPermission perm : resultList){
            final String key = perm.getCacheKey();

            // Remove aggregation keys from non-db entries set as it was found in the result set. Does not need to be inserted.
            nonDbEntries.remove(key);

            final int mergeResult = mergePermissionRecords(perm, permissions.get(key));
            if (mergeResult < 0){
                // Could not merge, nothing to be done. Should not happen - assertion. Keys matches..
                continue;
            } else if (mergeResult == 0){
                // No change was made. No persistency is needed.
                continue;
            }

            // Change was made, persist a new record.
            dataService.persist(perm, false);
            // Update cache.
            permissions.put(key, perm);
            changed += 1;
        }

        // Insert non-db entries to the database as a new ones.
        int newCtr = 0;
        final int newSize = nonDbEntries.size();
        for(String agKey : nonDbEntries){
            newCtr += 1;
            changed += 1;
            final AccountingPermission perm = permissions.get(agKey);
            perm.setDateCreated(new Date());
            perm.setDateCreated(new Date());
            dataService.persist(perm, newCtr >= newSize);
        }

        return changed;
    }

    /**
     * Merges permissions records a1 and a2 together, if possible.
     * Merges a2 to a1 record. If no change was made, returns 0. On error or incompatibility, negative value is returned,
     * on a successful merge with change, positive non-zero value is returned.
     *
     * @param a1 Aggregation record that a2 will be merged into.
     * @param a2 Aggregation record which will be merged into a1.
     * @return
     */
    public int mergePermissionRecords(AccountingPermission a1, AccountingPermission a2){
        if (a1 == null && a2 == null){
            return -1;
        } else if (a1 != null && a2 == null){
            return 0;
        } else if (a1 == null){
            return 0;
        }

        // Test if can be merged, aggregation keys has to match.
        if (!a1.getCacheKey().equals(a2.getCacheKey())){
            return -1;
        }

        boolean changed = false;

        // Aggregation logic, add a2 -> a1 record.

        // First = keep minimal.
        if (compareIds(a1.getActionIdFirst(), a1.getActionCounterFirst(), a2.getActionIdFirst(), a2.getActionCounterFirst()) == 1){
            a1.setActionIdFirst(a2.getActionIdFirst());
            a1.setActionCounterFirst(a2.getActionCounterFirst());
            changed = true;
        }

        // Last = keep maximal.
        if (compareIds(a1.getActionIdLast(), a1.getActionCounterLast(), a2.getActionIdLast(), a2.getActionCounterLast()) == -1){
            a1.setActionIdLast(a2.getActionIdLast());
            a1.setActionCounterLast(a2.getActionCounterLast());
            changed = true;
        }

        if (a2.getAggregationCount() > 0) {
            a1.setAggregationCount(a1.getAggregationCount() + a2.getAggregationCount());
            changed = true;
        }

        if (a2.getAmount() > 0) {
            a1.setAmount(a1.getAmount() + a2.getAmount());
            changed = true;
        }

        if (changed){
            a1.setDateModified(new Date());
        }

        return changed ? 1 : 0;
    }

    /**
     * Performs action:ctr1 < id2:ctr2.
     * @param id1
     * @param ctr1
     * @param id2
     * @param ctr2
     * @return
     */
    public int compareIds(long id1, int ctr1, long id2, int ctr2){
        if (id1 == id2) {
            if (ctr1 == ctr2) {
                return 0;
            }

            return ctr1 < ctr2 ? -1 : 1;
        }

        return id1 < id2 ? -1 : 1;
    }

    /**
     * Returns default aggregation interval size.
     * It may depend on the type of the accounting log.
     *
     * @param alog
     * @return
     */
    public long getDefaultAggregationIntervalSize(AccountingLog alog){
        // TODO: implement different aggregations also.
        return 3600000l;
    }

    /**
     * Returns true if given accounting log needs aref field, i.e., is identified also by this field.
     * Accounting logs for user-dependant counters needs aref to differentiate between different destination users.
     * @param alog
     * @return
     */
    public boolean isArefType(AccountingLog alog){
        // TODO: implement.
        return false;
    }

    /**
     * Returns true if given accounting log needs aref field, i.e., is identified also by this field.
     * Accounting logs for user-dependant counters needs aref to differentiate between different destination users.
     * @param cperm
     * @return
     */
    public boolean isArefType(AccountingPermission cperm){
        // TODO: implement.
        return false;
    }

    /**
     * Returns aggregation boundary in milliseconds to which this accounting log belongs.
     * @param alog
     * @return
     */
    public long getAggregationStart(AccountingLog alog, Long intervalSize){
        final long intervalSizeReal = intervalSize == null ? getDefaultAggregationIntervalSize(alog) : intervalSize;
        final long actionId = alog.getActionId();
        final long aggregationLowBound = actionId - (actionId % intervalSizeReal);
        return aggregationLowBound;
    }

    /**
     * Generates rkey base for alog. Used as unique ID for the alog.
     * @param alog
     * @return
     */
    public String getAlogRkeyBase(AccountingLog alog){
        final String ownerSip = PhoenixDataService.getSIP(alog.getOwner());
        final StringBuilder sb = new StringBuilder();
        sb.append(ownerSip)
                .append(";")
                .append(alog.getResource())
                .append(";")
                .append(alog.getType())
                .append(";");

        if (isArefType(alog)){
            sb.append(alog.getAaref()).append(";");
        }

        sb.append(alog.getActionId())
                .append(";")
                .append(alog.getActionCounter());

        return sb.toString();
    }

    /**
     * Generates rkey (to be used in database) for alog. Used as unique ID for the alog record uploaded from the user.
     * @param alog
     * @return
     */
    public String getAlogRkey(AccountingLog alog){
        final String alogRkeyBase = getAlogRkeyBase(alog);
        try {
            return MiscUtils.generateMD5HashBase64Encoded(alogRkeyBase.getBytes("UTF-8"));
        } catch (Exception e) {
            log.error("Could not generate hash", e);
        }

        return alogRkeyBase;
    }

    /**
     * Returns string forming unique key for this accounting log.
     * No aggregation is taken into account.
     *
     * Key: user;resource;type[;aref]
     *
     * @param alog
     * @return
     */
    public String getAlogKeyBase(AccountingLog alog){
        final String ownerSip = PhoenixDataService.getSIP(alog.getOwner());
        final StringBuilder sb = new StringBuilder();
        sb.append(ownerSip)
                .append(";")
                .append(alog.getType());


        // If this accounting log needs aref field, add it here.
        // It is suitable e.g., for per user limits, where aref holds hash of the destination user.
        if (isArefType(alog)){
            sb.append(";");
            sb.append(alog.getAaref());
        }

        return sb.toString();
    }

    /**
     * Generates unique cache key for the aggregation for given record.
     * Default aggregation period is 1 hour, rounded on full hour, in GMT.
     *
     * Key: user;resource;type;[aref];aggregationLowLimit;
     *
     * @param alog
     * @return
     */
    public String getAggregationCacheKey(AccountingLog alog){
        // Get nearest hour boundary from the record.
        final long aggregationLowBound = getAggregationStart(alog, null);
        final String alogKey = getAlogKeyBase(alog);

        final StringBuilder sb = new StringBuilder();
        sb.append(alogKey)
                .append(";")
                .append(aggregationLowBound)
                .append(";");

        // Hash it with MD5 and return result.
        try {
            return MiscUtils.generateMD5HashBase64Encoded(sb.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            log.error("Could not generate hash", e);
        }

        return sb.toString();
    }

    /**
     * Generates unique cache key for the aggregation for given record.
     *
     * @param cperm
     * @return
     */
    public String getPermissionCacheKey(AccountingPermission cperm){
        final String ownerSip = PhoenixDataService.getSIP(cperm.getOwner());
        final StringBuilder sb = new StringBuilder();
        sb.append(ownerSip)
                .append(";")
                .append(cperm.getPermId())
                .append(";")
                .append(cperm.getLicenseId());

        if (isArefType(cperm)){
            sb.append(";").append(cperm.getAaref());
        }

        // Hash it with MD5 and return result.
        try {
            return MiscUtils.generateMD5HashBase64Encoded(sb.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            log.error("Could not generate hash", e);
        }

        return sb.toString();
    }

}
