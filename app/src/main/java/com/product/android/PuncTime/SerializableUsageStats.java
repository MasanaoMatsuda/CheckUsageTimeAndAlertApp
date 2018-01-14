package com.product.android.PuncTime;

import java.io.Serializable;
import java.util.List;

/**
 * Created by masanao on 2018/01/12.
 */

public class SerializableUsageStats implements Serializable {

    private static final long serialVersionUID = 6255752248513019027L;

    private long startTime;
    private long endTime;
    private List<Application> applicationList;


    public SerializableUsageStats(long startTime, long endTime, List<Application> applicationList) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.applicationList = applicationList;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<Application> getApplicationList() {
        return applicationList;
    }
}
