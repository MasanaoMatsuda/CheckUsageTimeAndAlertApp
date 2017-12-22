package com.example.android.asobisugi;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND;
import static android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND;

/**
 * Created by masanao on 2017/12/15.
 */

public class CountTimeService extends Service {

    public static final String TAG = CountTimeService.class.getSimpleName();

    private Timer mTimer = null;
    public Handler mHandler;

    private long mPreferenceTime = 1000 * 20;
    private long mThreadSleepTime = mPreferenceTime;
    private long mTotalTime;
    private long mWaitingTime;
    private long mUsingTime;
    private long mStartTime;
    private long mEndTime;
    private SimpleDateFormat dateFormat;

    // 開始後の自動スリープに移行するまでの時間をストックする
    private boolean mTheFirstType2 = true;
    private long MyAppGoBackgroundTime;

    // 間隔が短すぎるループを停止させる処理に使う定数(10秒に設定）
    private static final long MIN_LOOP_INTERVAL = 1000 * 10;

    private boolean mCheckContinuousType1 = false;
    private boolean mCheckContinuousType2 = true;
    private long mNumOfType1 = 0;
    private long mNumOfType2 = 0;
    private long mSumOfType1 = 0;
    private long mSumOfType2 = 0;

    private String mLauncherPackageName;
    private final String SYSTEM_UI = "com.android.systemui";



    @Override
    public void onCreate() {
        mHandler = new Handler();
        dateFormat = new SimpleDateFormat("yyyy/M/d H:mm:ss.SS", Locale.JAPAN);
        mStartTime = System.currentTimeMillis();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mLauncherPackageName = intent.getPackage();
        mTimer = new Timer(true);

        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        executeTask();
                    }
                });
            }
        }, mThreadSleepTime);
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        Toast.makeText(this, TAG + " onDestroy", Toast.LENGTH_SHORT).show();
    }

    /*
* Loop処理のコントローラー
* */
    private void executeTask() {
        Log.i(TAG, "executeTask" +
                "\nThread ID: " + Thread.currentThread().getId());

        extractUsage();
        calculateUsingTime();
        if (mThreadSleepTime > 10) {
            setTimerAgain();
        } else {
            Intent intent = new Intent(Intent.ACTION_SEARCH)
                    .setPackage("com.google.android.youtube")
                    .putExtra("query",
                            "https://www.youtube.com/watch?v=g9hwjQBQFIo")
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        Log.d("Check", "@Loop@を抜けました。Serviceタスクが終了します。" +
                "\nThread ID: " + Thread.currentThread().getId());
    }


    /*
* アプリがForegroundまたはBackgroundになったときのTimeStampを足し合わせるメソッド。
* Type1(Foreground)とType2(Background)の【SumOf】を算出する処理
* */
    private void extractUsage() {
        Log.d("Check", "extractUsageメソッドに入りました" +
                "\n→ mSumOfType1:" + mSumOfType1 + " mSumOfType2:" + mSumOfType2 +
                "\n→ mNumOfType1:" + mNumOfType1 + " mNumOfType2:" + mNumOfType2 +
                "\nThread ID: " + Thread.currentThread().getId());

        resetField();

        UsageStatsManager stats = (UsageStatsManager) getSystemService("usagestats");
        mEndTime = System.currentTimeMillis();
        UsageEvents usageEvents = stats.queryEvents(mStartTime, mEndTime);

        Log.d("Check",
                "\nstartTime: " + dateFormat.format(mStartTime)
                        + "\nendTime: " + dateFormat.format(mEndTime));

        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            usageEvents.getNextEvent(event);
            long timestamp = event.getTimeStamp();
            int type = event.getEventType();
            String packageName = event.getPackageName();
            String className = event.getClassName();
            long timeStamp = event.getTimeStamp();

            // LauncherSystemとSystemUIは、挙動が機種によって異なるため記録を除外。
            // ロジックが崩壊し、エラー発生元になる可能性があるため。
            if (packageName.equals(mLauncherPackageName) | packageName.equals(SYSTEM_UI)) {
                Log.d("Check", "Skipped.[mLauncherPackageName] or [SYSTEM_UI]");
                continue;
            }

            if (type == MOVE_TO_FOREGROUND) {
                if (mCheckContinuousType1) {
                    mCheckContinuousType1 = false;
                    mCheckContinuousType2 = true;
                    mSumOfType1 += timestamp;
                    mNumOfType1 += 1;
                    Log.d("Check", "added time because type is: " + type +
                            "\nPackageName is :" + packageName +
                            "\nclassName is :" + className +
                            "\nTimeStamp is :" + dateFormat.format(timeStamp));
                } else {
                    Log.d("Check", "Skipped because of continuous. " +
                            "This is: " + type +
                            "\nPackageName is :" + packageName);
                }
            } else if (type == MOVE_TO_BACKGROUND) {
                if (mTheFirstType2) {
                    Log.d("Check", "本アプリが開始後初めてBackgroundに入った時間");
                    mTheFirstType2 = false;
                    MyAppGoBackgroundTime = timeStamp;
                }

                if (mCheckContinuousType2) {
                    mCheckContinuousType2 = false;
                    mCheckContinuousType1 = true;
                    mSumOfType2 += timestamp;
                    mNumOfType2 += 1;
                    Log.d("Check", "added time because type is: " + type +
                            "\nPackageName is :" + packageName +
                            "\nTimeStamp is :" + dateFormat.format(timeStamp));
                } else if (!mCheckContinuousType2) {
                    Log.d("Check", "skipped because continuous. This is: " + type +
                            "\nPackageName is :" + packageName);
                }
            } else {
                Log.d("Check", "skipped because type is: " + type +
                        "\nPackageName is :" + packageName);
            }
        }
    }


    /*
    * 端末の使用時間（mUsingTime）の算出をするメソッド。
    * */
    private void calculateUsingTime() {

        if (mNumOfType1 == 0) {
            Log.d("Check", "未使用です。もう一周待ちます。" +
                    "\nThread ID: " + Thread.currentThread().getId());
            return;
        }

        Log.d("Check",
                "checkUsageメソッドに入りました" +
                        "\n→ mSumOfType1:" + mSumOfType1 + " mSumOfType2:" + mSumOfType2 +
                        "\n→ mNumOfType1:" + mNumOfType1 + " mNumOfType2:" + mNumOfType2);

        if (mNumOfType1 < mNumOfType2) {
            mWaitingTime = (mSumOfType1 + mEndTime) - mSumOfType2
                    + (MyAppGoBackgroundTime - mStartTime);
            Log.d("Check", "補正します");
        } else {
            mWaitingTime = mSumOfType1 - mSumOfType2
                    + (MyAppGoBackgroundTime - mStartTime);
            Log.d("Check", "補正しません");
        }

        mTotalTime = mEndTime - mStartTime;
        mUsingTime = mTotalTime - mWaitingTime;

        mThreadSleepTime = (mPreferenceTime - mUsingTime);

        Log.d("Check", "TotalTime: " + mTotalTime / 1000 +
                "\nWaiting time: " + mWaitingTime / 1000 +
                "\nmSumOfType1" + mSumOfType1 +
                "\nmSumOfType2" + mSumOfType2 +
                "\nmEndTime" + mEndTime + "[" + dateFormat.format(mEndTime) + "]");

        if (mThreadSleepTime < MIN_LOOP_INTERVAL) {
            Log.d("Check",
                    "mThreadSleepTimeは" + mThreadSleepTime / 1000 + "です。" +
                            "\nループ間隔が" + MIN_LOOP_INTERVAL / 1000 + "秒以下です。");
            mThreadSleepTime = 0;
            Log.d("Check",
                    "mThreadSleepTimeを" + mThreadSleepTime + "にし停止します。");
        } else {
            Log.d("Check",
                    "使用時間です: 使用時間は" + mUsingTime / 1000 + "秒" +
                            "\n待機時間を " + mThreadSleepTime / 1000 + "秒に設定します");
        }
    }



    /*
* Timer.schedule()を使うことでService処理をStopボタンから中止できる。
* */
    private void setTimerAgain() {
        mTimer = new Timer(true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Timer run" +
                                "\nThread ID: " + Thread.currentThread().getId());

                        executeTask();
                    }
                });
            }
        }, mThreadSleepTime);
    }


    private void resetField() {
        mSumOfType1 = 0;
        mSumOfType2 = 0;
        mNumOfType1 = 0;
        mNumOfType2 = 0;
        mCheckContinuousType1 = false;
        mCheckContinuousType2 = true;
    }

}
