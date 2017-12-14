package com.example.android.asobisugi;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;

/**
 * Created by masanao on 2017/12/13.
 */

public class CancelCountIntentService extends IntentService {
    public CancelCountIntentService() {
        super("CancelCountIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String action = intent.getAction();
        String launcherPackName = intent.getPackage();
        CountTimeTask.executeTask(this, action, launcherPackName, intent);
    }

}
