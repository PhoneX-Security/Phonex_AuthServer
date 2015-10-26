package com.phoenix.accounting;

/**
 * Simple class for counting top ids of accounting logs.
 */
public class TopIdCounter {
    private long id = 0;
    private int ctr = 0;

    public TopIdCounter() {

    }

    public void insert(long aId, int aCtr){
        if (id <= aId){
            if (id == aId && ctr < aCtr){
                ctr = aCtr;
            } else if (id < aId){
                ctr = aCtr;
            }

            id = aId;
        }
    }

    public long getId() {
        return id;
    }

    public int getCtr() {
        return ctr;
    }
}
