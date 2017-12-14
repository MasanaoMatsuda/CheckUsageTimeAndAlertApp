package com.example.android.asobisugi;

import android.app.AppOpsManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.firebase.jobdispatcher.Driver;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;

import static com.firebase.jobdispatcher.Lifetime.UNTIL_NEXT_BOOT;

public class MainActivity extends AppCompatActivity {

    public String mLauncherPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UsageEventsを取得するための設定を確認
        boolean checkUsageStats = checkForPermission(this);
        if (checkUsageStats == false) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }


        findLauncherApp();


        Button statsBtn = (Button) findViewById(R.id.stats_btn);
        statsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent countTimeIntentService = new Intent(
                        MainActivity.this, CountTimeIntentService.class)
                        .setAction(CountTimeTask.ACTION_COUNT_TIME)
                        .setPackage(mLauncherPackageName);
                startService(countTimeIntentService);

                NotificationUtils.remindUserBecauseCharging(MainActivity.this);
            }
        });

        Driver driver = new GooglePlayDriver(this);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);

        Job myJob = dispatcher.newJobBuilder()
                .setService(MyJobService.class) // the JobService that will be called
                .setTag("") // uniquely identifies the job //ToDo: what tag? add constant variable
                .setRecurring(false) // don't recur(one-off job)
                .setLifeTime(UNTIL_NEXT_BOOT) //don't persist past a device reboot
                .setTrigger(900) // start between 0 and 15minutes
                .setReplaceCurrent(true)// replace a current job with the same tag if one exists
                .build();
        dispatcher.schedule(myJob);

        // ToDo: add [dispatcher.cancel(tag);] to trigger cancel button was pressed
    }

    public void findLauncherApp() {
        // パッケージマネージャーの作成
        PackageManager packageManager = getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        ResolveInfo resolveInfo = packageManager.resolveActivity(intent, 0);
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        mLauncherPackageName = activityInfo.packageName; // パッケージ名
        Log.d("Check", "Inside findLauncherApp: " + mLauncherPackageName);
    }


    public boolean checkForPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
