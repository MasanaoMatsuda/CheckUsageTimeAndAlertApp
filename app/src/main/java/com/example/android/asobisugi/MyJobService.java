package com.example.android.asobisugi;

import android.util.Log;

import com.firebase.jobdispatcher.JobService;

/**
 * Created by masanao on 2017/12/09.
 */

public class MyJobService extends JobService {


    @Override
    public boolean onStartJob(com.firebase.jobdispatcher.JobParameters job) {
        Log.d("Check", "I'm inside onStartJob in MyJobService.class.");
        CountTimeTask.executeTask(this, MainActivity.mLauncherPackageName);
        return true;
    }

    @Override
    public boolean onStopJob(com.firebase.jobdispatcher.JobParameters job) {
        return true;
    }
}
