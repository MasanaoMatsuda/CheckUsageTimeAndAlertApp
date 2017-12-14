package com.example.android.asobisugi;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Locale;

import static android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND;
import static android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND;

/**
 * Created by masanao on 2017/12/10.
 */

public class CountTimeTask {

    public static final String ACTION_COUNT_TIME = "count-time";
    public static final String ACTION_CANCEL_COUNT_TIME = "cancel-count-time";

    // 間隔が短すぎるループを停止させる処理に使う定数(10秒に設定）
    private static final long MIN_LOOP_INTERVAL = 1000 * 10;
    private static String mLauncherPackageName;
    private static final String SYSTEM_UI = "com.android.systemui";

    private static long mPreferenceTime = 1000 * 60;
    private static long mThreadSleepTime = mPreferenceTime;
    private static long mTotalTime;
    private static long mUsingTime;
    private static long mStartTime;
    private static long mEndTime;

    private static boolean mCheckContinuousType1 = false;
    private static boolean mCheckContinuousType2 = true;
    private static long mNumOfType1 = 0;
    private static long mNumOfType2 = 0;
    private static long mSumOfType1 = 0;
    private static long mSumOfType2 = 0;

    private static SimpleDateFormat dateFormat;

    // 通知から停止が押されたとき
    public static boolean mShouldContinue = true;


    public static void executeTask(
            Context context, String action, String launcherPackName, Intent service) {

        if (action == ACTION_COUNT_TIME) {
            mLauncherPackageName = launcherPackName;

            mStartTime = System.currentTimeMillis();

            while (mNumOfType1 == 0) {
                Log.d("Check", "@First while loop@");
                checkCancelButtonPressed(context, service);

                try {
                    delayExecute();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                checkCancelButtonPressed(context, service);
                extractUsage(context);
            }

            Log.d("Check", "@First while loop@を抜けました");
            checkCancelButtonPressed(context, service);

            checkUsage();


            while (mThreadSleepTime > 0) {
                Log.d("Check", "@Second while loop@");
                checkCancelButtonPressed(context, service);

                try {
                    delayExecute();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                checkCancelButtonPressed(context, service);
                extractUsage(context);
                checkUsage();
            }

            Log.d("Check", "@Second while loop@を抜けました" +
                    "\n mTotalTime:" + mTotalTime / 1000);

            mThreadSleepTime = mPreferenceTime;
            resetField();
        } else if (ACTION_CANCEL_COUNT_TIME.equals(action)) {
            mShouldContinue = false;
            Log.d("Check", "キャンセルされました。mShouldContinueをfalseに設定します");
            NotificationUtils.clearAllNotifications(context);
        }
    }

    private static void extractUsage(Context context) {
        resetField();

        Log.d("Check",
                "extractUsageメソッドに入りました" +
                        "\n→ mSumOfType1:" + mSumOfType1 + " mSumOfType2:" + mSumOfType2 +
                        "\n→ mNumOfType1:" + mNumOfType1 + " mNumOfType2:" + mNumOfType2);

        dateFormat =
                new SimpleDateFormat("yyyy/M/d H:mm:ss.SS", Locale.JAPAN);

        UsageStatsManager stats =
                (UsageStatsManager) context.getSystemService("usagestats");
        mEndTime = System.currentTimeMillis();
        //       mTotalTime = mEndTime - mStartTime;

        UsageEvents usageEvents = stats.queryEvents(mStartTime, mEndTime);
        Log.d("Check", "\nstartTime: " + dateFormat.format(mStartTime)
                + "\nendTime: " + dateFormat.format(mEndTime));

        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            usageEvents.getNextEvent(event);
            long timestamp = event.getTimeStamp();
            int type = event.getEventType();
            String packageName = event.getPackageName();
            String className = event.getClassName();
            long timeStamp = event.getTimeStamp();

            if (packageName.equals(mLauncherPackageName) | packageName.equals(SYSTEM_UI)){
                Log.d("Check", "This is mLauncherPackageName or SYSTEM_UI.[skipped]");
                continue;
            }

            if (type == MOVE_TO_FOREGROUND) {
                if (mCheckContinuousType1 == true) {
                    mCheckContinuousType1 = false;
                    mCheckContinuousType2 = true;
                    mSumOfType1 += timestamp;
                    mNumOfType1 += 1;
                    Log.d("Check", "added time because type is: " + type +
                            "\nPackageName is :" + packageName +
                            "\nclassName is :" + className +
                            "\nTimeStamp is :" + dateFormat.format(timeStamp));
                } else {
                    Log.d("Check", "skipped because continuous. This is: " + type +
                            "\nPackageName is :" + packageName);
                }
            } else if (type == MOVE_TO_BACKGROUND) {
                if (mCheckContinuousType2) {
                    mCheckContinuousType2 = false;
                    mCheckContinuousType1 = true;
                    mSumOfType2 += timestamp;
                    mNumOfType2 += 1;
                    Log.d("Check", "added time because type is: " + type +
                            "\nPackageName is :" + packageName +
                            "\nclassName is :" + className +
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

    private static void checkUsage() {
        long waitingTime;
        Log.d("Check",
                "checkUsageメソッドに入りました" +
                        "\n→ mSumOfType1:" + mSumOfType1 + " mSumOfType2:" + mSumOfType2 +
                        "\n→ mNumOfType1:" + mNumOfType1 + " mNumOfType2:" + mNumOfType2);

        if (mNumOfType1 < mNumOfType2) {
            waitingTime = (mSumOfType1 + mEndTime) - mSumOfType2;
            Log.d("Check", "補正します");
        } else {
            waitingTime = mSumOfType1 - mSumOfType2;
            Log.d("Check", "補正しません");
        }

        mTotalTime = mEndTime - mStartTime;
        mUsingTime = mTotalTime - waitingTime; //14 = 120 - 105 ←WaitingTime少ない説
        mThreadSleepTime = (mPreferenceTime - mUsingTime);

        Log.d("Check", "TotalTime: " + mTotalTime / 1000 +
                "\nWaiting time: " + waitingTime / 1000 +
                "\nmSumOfType1" + mSumOfType1 +
                "\nmSumOfType2" + mSumOfType2 +
                "\nmEndTime" + mEndTime + "[" + dateFormat.format(mEndTime) + "]");

        if (mThreadSleepTime < MIN_LOOP_INTERVAL) {
            Log.d("Check", "mThreadSleepTimeは" + mThreadSleepTime / 1000 + "です。" +
                    "\nループ間隔が" + MIN_LOOP_INTERVAL / 1000 + "秒以下です。");
            mThreadSleepTime = -1;
            Log.d("Check", "mThreadSleepTimeを" + mThreadSleepTime + "にし停止します。");
        } else {
            Log.d("Check",
                    "使用時間です: 使用時間は" + mUsingTime / 1000 + "秒" +
                            "\n待機時間を " + mThreadSleepTime / 1000 + "秒に設定します");
        }
    }

    private static void delayExecute() throws InterruptedException {
        Log.d("Check", "待機中です: 待機時間は" + mThreadSleepTime / 1000 + "秒");
        Thread.sleep(mThreadSleepTime);
    }


    private static void resetField() {
        mSumOfType1 = 0;
        mSumOfType2 = 0;
        mNumOfType1 = 0;
        mNumOfType2 = 0;
        mCheckContinuousType1 = false;
        mCheckContinuousType2 = true;
    }

    public static void checkCancelButtonPressed(Context context, Intent service) {
        if (CountTimeTask.mShouldContinue == false) {
            Log.d("Check", "停止ボタンが押されました。タイマーを停止します。");
            return;
        } else {
            Log.d("Check", "mShouldContinueはtrueです。処理を継続します。");
        }
    }

}
