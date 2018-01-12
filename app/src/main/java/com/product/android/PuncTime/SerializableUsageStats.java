package com.product.android.PuncTime;

import java.io.Serializable;
import java.util.List;

/**
 * Created by masanao on 2018/01/12.
 */

public class SerializableUsageStats implements Serializable {

    private static final long serialVersionUID = 6255752248513019027L;

    long startTime;
    long endTime;
    List<Application> applicationList;
}
