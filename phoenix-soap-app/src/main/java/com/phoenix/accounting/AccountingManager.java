package com.phoenix.accounting;

import com.phoenix.db.AccountingAggregated;
import com.phoenix.db.AccountingLog;
import com.phoenix.db.opensips.Acc;
import com.phoenix.db.opensips.Subscriber;
import com.phoenix.service.AMQPListener;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.soap.beans.AccountingSaveRequest;
import com.phoenix.soap.beans.AccountingSaveResponse;
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
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
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
    private static final String REQ_BODY_STORE = "store";

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
     * Processing save request - basic entry point.
     * Example JSON:
     * {"store":{
     *     "res":"abcdef123",
     *     "records":[
     *          {"type":"c.os", "aid":1443185424488, "ctr":1, "vol": "120", "ref":"ed4b607e48009a34d0b79fe70f521cde"},
     *          {"type":"c.os", "aid":1443185524488, "ctr":2, "vol": "10"},
     *          {"type":"m.om", "aid":1443185624488, "ctr":3, "vol": "120"},
     *          {"type":"m.om", "aid":1443185724488, "ctr":4, "vol": "10", "ag":1, "aidbeg":1443185724488},
     *          {"type":"f.id", "aid":1443185824488, "ctr":5, "vol": "1"}
     *     ]
     * }}
     *
     * Response: {
     *     "store":{
     *          "topaid":1443185824488, "topctr":5
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
        store(caller, resource, records, request, response, jsonResponse);

        // Build JSON response.
        response.setAuxJSON(jsonResponse.toString());
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
                         AccountingSaveRequest request, AccountingSaveResponse response, JSONObject jsonResponse) throws JSONException
    {
        final String callerSip = PhoenixDataService.getSIP(caller);
        final int recSize = records.length();
        final Date dateCreated = new Date();

        // Store newest accounting action id + counter.
        long topAid = -1;
        int topACtr = -1;

        // Request-wide aggregation.
        final Map<String, AccountingAggregated> semiAggregation = new HashMap<String, AccountingAggregated>();
        for (int i = 0; i < recSize; i++){
            final JSONObject curRec = records.getJSONObject(i);

            // Sanitization.
            if (!curRec.has(STORE_ACTION_TYPE)
                    || curRec.has(STORE_ACTION_ID)
                    || curRec.has(STORE_ACTION_COUNTER)
                    || curRec.has(STORE_ACTION_VOLUME)){
                log.warn("Improperly formatted accounting action, missing some key attributes");
                continue;
            }

            final AccountingLog alog = new AccountingLog();
            alog.setDateCreated(dateCreated);
            alog.setOwner(caller);
            alog.setResource(resource);
            alog.setType(curRec.getString(STORE_ACTION_TYPE));
            alog.setActionId(curRec.getLong(STORE_ACTION_ID));
            alog.setActionCounter(curRec.getInt(STORE_ACTION_COUNTER));
            alog.setAmount(curRec.getLong(STORE_ACTION_VOLUME));
            alog.setAaref(curRec.has(STORE_ACTION_REFERENCE) ? curRec.getString(STORE_ACTION_REFERENCE) : null);
            alog.setAggregated(curRec.has(STORE_ACTION_AGGREGATED) ? curRec.getInt(STORE_ACTION_AGGREGATED) : 0);
            dataService.persist(alog, i+1 >= recSize);

            // Top processed action ID & counter.
            if (topAid <= alog.getActionId()){
                if (topAid == alog.getActionId() && topACtr < alog.getActionCounter()){
                    topACtr = alog.getActionCounter();
                } else if (topAid < alog.getActionId()){
                    topACtr = alog.getActionCounter();
                }

                topAid = alog.getActionId();
            }

            // Request-wide aggregation logic.
            final String aggregationKey = getAggregationCacheKey(alog);
            AccountingAggregated curAg = new AccountingAggregated();

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
        }

        // Update aggregated records using semiAggregation.
        final int changed = mergeAggregationWithDB(semiAggregation);
        log.info(String.format("Aggregations updated to DB, records changed: %d", changed));

        // Response.
        final JSONObject storeResp = jsonResponse.has(REQ_BODY_STORE) ? jsonResponse.getJSONObject(REQ_BODY_STORE) : new JSONObject();
        storeResp.put(STORE_RESP_TOP_ACTION_ID, topAid);
        storeResp.put(STORE_RESP_TOP_ACTION_COUNTER, topACtr);
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
        final String sqlFetch = "SELECT ag FROM AccountingAggregated WHERE aggregationKey IN :keys";
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
            changed += 1;
        }

        // Insert non-db entries to the database as a new ones.
        int newCtr = 0;
        final int newSize = nonDbEntries.size();
        for(String agKey : nonDbEntries){
            newCtr += 1;
            changed += 1;
            final AccountingAggregated ag = aggregations.get(agKey);
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
                .append(alog.getResource())
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
            return MiscUtils.generateMD5Hash(sb.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            log.error("Could not generate hash", e);
        }

        return sb.toString();
    }


}
