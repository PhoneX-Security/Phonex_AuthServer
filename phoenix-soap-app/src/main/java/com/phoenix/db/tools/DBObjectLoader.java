package com.phoenix.db.tools;

import com.phoenix.service.PhoenixDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bulk DB loader using IN(...) construct.
 *
 * Created by dusanklinec on 15.06.15.
 */
public class DBObjectLoader<T, W> {
    private static final Logger log = LoggerFactory.getLogger(DBObjectLoader.class);
    private static final String TAG = "DBBulkQuery";
    private static final int OPERATION_THRESHOLD = 100;

    protected final Class<T> typeParameterClass;

    /**
     * Service for loading data.
     */
    protected final PhoenixDataService svc;

    /**
     * SQL WHERE part, constant part. May be null / empty.
     */
    protected String sqlStatement;

    /**
     * Column for x IN (...) query part.
     */
    protected String whereInColumn;

    /**
     * Argument names to be set in prepared query.
     */
    protected String[] argsNames;

    /**
     * Arguments for where part. May be null.
     */
    protected Object[] argsWhere;

    /**
     * Threshold for number of elements in the queue to trigger the operation.
     * If -1 operation is not triggered until finish() is called.
     */
    protected int operationThreshold = OPERATION_THRESHOLD;

    /**
     * List of string arguments in IN(...) part.
     */
    protected final Queue<W> argsIn = new ConcurrentLinkedQueue<W>();

    /**
     * Cached objects from the database.
     */
    protected final Queue<T> objects = new ArrayDeque<T>();

    /**
     * Current object when iterating.
     */
    protected T curObject;

    public DBObjectLoader(Class<T> typeParameterClass, PhoenixDataService svc) {
        this.typeParameterClass = typeParameterClass;
        this.svc = svc;
    }

    /**
     * Adds string argument to the buffer, update is triggered once number of elements reached threshold or
     * after calling finish().
     * @param elem element to add to queue.
     */
    public void add(W elem){
        argsIn.add(elem);
    }

    /**
     * Adds all strings argument to the buffer, update is triggered once number of elements reached threshold or
     * after calling finish().
     * @param elems list of elements to add to the queue.
     */
    public void add(List<W> elems){
        argsIn.addAll(elems);
    }

    /**
     * Triggers a new query so current queue is non-empty. Does not modify curObject nor poll()s the queue.
     * Must not be used with moveToNext() API.
     * @return
     */
    public boolean loadNewData(){
        return internalMove(false);
    }

    /**
     * Copies all loaded data and returns as a list, clearing internal queue of loaded objects.
     * @return
     */
    public List<T> getLoadedData(){
        List<T> toReturn = new ArrayList<T>(objects.size());
        toReturn.addAll(objects);
        objects.clear();

        return toReturn;
    }

    /**
     * Iterates over the records. If current query has no more records, new one is executed and next object is set to current object.
     * Should be used together with getCurrent() as it modified queue of loaded objects and current object.
     *
     * @return true if moving to next record succeeded.
     */
    public boolean moveToNext(){
        return internalMove(true);
    }

    /**
     * Iterates over the records. If current query has no more records, new one is executed.
     * @param setCurrent if true, first element from queue is poll()-ed and set as current object.
     * @return
     */
    protected boolean internalMove(boolean setCurrent){
        // First call to moveToNext? Do query.
        while(true){
            // If there is existing cursor and can be moved to next element, return true.
            if (!objects.isEmpty()){
                if (setCurrent) {
                    curObject = objects.poll();
                }

                return true;
            }

            // Cursor is null here, load new, if there is any.
            if (!hasNext()){
                return false;
            }

            // Do the next query, if it cannot be performed anymore, stop iteration.
            if (!doQuery()){
                return false;
            }
        }
    }

    /**
     * Close all remaining cursors, deletes data.
     */
    public void close(){
        argsIn.clear();
    }

    /**
     * Executes bulk query and returns true if succeeded.
     * Result is stored to the local queue.
     * @return
     */
    public boolean doQuery(){
        if (!hasNext()){
            return false;
        }

        try {
            final List<W> toProcess = takeN(false);
            log.trace(String.format("Query, numElements: %s", toProcess.size()));

            // Do the query in transaction module.
            final List result = svc.queryIn(typeParameterClass, sqlStatement, argsNames, argsWhere, whereInColumn, toProcess);
            objects.addAll(result);

            return true;

        } catch (Exception ex) {
            log.error("Exception in bulk operation", ex);
        }

        return false;
    }

    /**
     * Returns currently loaded object.
     * @return
     */
    public T getCurrent(){
        return curObject;
    }

    /**
     * Returns true if there is more data in the queue to query.
     * @return
     */
    public boolean hasNext(){
        return argsIn.size() > 0;
    }

    /**
     * Takes N parameters from the string list, returns as string.
     * Modifies queue, removes elements from it.
     * @return list of SQL escaped string arguments to process in where condition.
     */
    protected List<W> takeN(boolean all) {
        final int size = argsIn.size();
        final int limitToSelect = (operationThreshold < 0 || all || size <= operationThreshold) ? size : operationThreshold;
        final ArrayList<W> toProcess = new ArrayList<W>(limitToSelect);

        // Fill array list with data in queue.
        for (int i = 0; i < limitToSelect; i++) {
            final W curArg = argsIn.poll();
            if (curArg == null) {
                break;
            }

            toProcess.add(curArg);
        }

        return toProcess;
    }

    public PhoenixDataService getSvc() {
        return svc;
    }

    public String getSqlStatement() {
        return sqlStatement;
    }

    public void setSqlStatement(String sqlStatement) {
        this.sqlStatement = sqlStatement;
    }

    public String getWhereInColumn() {
        return whereInColumn;
    }

    public void setWhereInColumn(String whereInColumn) {
        this.whereInColumn = whereInColumn;
    }

    public String[] getArgsNames() {
        return argsNames;
    }

    public void setArgsNames(String[] argsNames) {
        this.argsNames = argsNames;
    }

    public Object[] getArgsWhere() {
        return argsWhere;
    }

    public void setArgsWhere(Object[] argsWhere) {
        this.argsWhere = argsWhere;
    }

    public int getOperationThreshold() {
        return operationThreshold;
    }

    public void setOperationThreshold(int operationThreshold) {
        this.operationThreshold = operationThreshold;
    }
}
