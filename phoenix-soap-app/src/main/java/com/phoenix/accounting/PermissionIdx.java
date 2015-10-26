package com.phoenix.accounting;

/**
 * Permission unique ID.
 */
public class PermissionIdx {
    private long permId;
    private long licId;

    public PermissionIdx(long permId, long licId) {
        this.permId = permId;
        this.licId = licId;
    }

    public PermissionIdx() {
    }

    public long getPermId() {
        return permId;
    }

    public void setPermId(long permId) {
        this.permId = permId;
    }

    public long getLicId() {
        return licId;
    }

    public void setLicId(long licId) {
        this.licId = licId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PermissionIdx that = (PermissionIdx) o;

        if (permId != that.permId) return false;
        return licId == that.licId;

    }

    @Override
    public int hashCode() {
        int result = (int) (permId ^ (permId >>> 32));
        result = 31 * result + (int) (licId ^ (licId >>> 32));
        return result;
    }
}
