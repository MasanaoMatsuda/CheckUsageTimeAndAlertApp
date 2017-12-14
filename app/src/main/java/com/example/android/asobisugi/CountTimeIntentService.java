package com.example.android.asobisugi;

import android.app.IntentService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Locale;

import static android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND;
import static android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND;
import static com.example.android.asobisugi.CountTimeTask.ACTION_COUNT_TIME;

/**
 * Created by masanao on 2017/12/09.
 */

public class CountTimeIntentService extends IntentService {


    public CountTimeIntentService() {
        super("CountTimeIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String action = intent.getAction();
        String launcherPackName = intent.getPackage();
        CountTimeTask.executeTask(this, action, launcherPackName, intent);
    }
}
