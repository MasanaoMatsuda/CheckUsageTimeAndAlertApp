package com.example.android.PuncTime;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.preference.PreferenceManager;
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

    private SharedPreferences mSharedPreferences;
    private long mPreferenceTime = 1000 * 5; // ToDo: 1000 * 60 に戻す（１分）
    private long mThreadSleepTime;
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

    // LauncherアプリとSystemUIをextractUsage()にてカウント対象外とするための変数
    private String mLauncherPackageName;
    private final String SYSTEM_UI = "com.android.systemui";



    @Override
    public void onCreate() {
        Log.i(TAG, "@onCreate: 変数の初期化処理");

        // 変数の初期化処理
        mHandler = new Handler();
        dateFormat = new SimpleDateFormat("yyyy/M/d H:mm:ss.SS", Locale.JAPAN);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferenceTime = readPreferenceTime(mSharedPreferences);
        mThreadSleepTime = mPreferenceTime;
        mStartTime = System.currentTimeMillis();
        findLauncherApp();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "@onStartCommand: バックグラウンド処理");

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

        startForeground(1, NotificationUtils.remindUserBecauseCounting(this));
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "@onDestroy");

        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    // MainActivityへBroadcastを飛ばす処理をTrigger
        String message = "CountTimeServiceタスク終了 -> UI更新どうぞ";
        NotificationUtils.clearAllNotifications(CountTimeService.this);
        sendBroadCast(message);
    }


    /*
    * Loop処理のコントローラー
    * */
    private void executeTask() {
        Log.i(TAG, "@executeTask: ループ処理開始");

        extractUsage();
        calculateUsingTime();
        if (mThreadSleepTime > 10) {
            setTimerAgain();
        } else {
            Log.d(TAG, "@Youtube");

            String value = mSharedPreferences.getString(
                    getString(R.string.pref_youtube_key),
                    getResources().getString(R.string.pref_youtube_default));

            Intent intent = new Intent(Intent.ACTION_SEARCH)
                    .setPackage("com.google.android.youtube")
                    .putExtra("query", value)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            onDestroy();
            Log.d(TAG, "@タスク終了");
        }
    }


    /*
    * アプリがForegroundまたはBackgroundになったときのTimeStampを足し合わせるメソッド。
    * Type1(Foreground)とType2(Background)の【SumOf】を算出する処理
    * */
    private void extractUsage() {
        Log.d(TAG, "@extractUsage: usageEventsの仕分けを開始");

        resetField();

        UsageStatsManager stats = (UsageStatsManager) getSystemService("usagestats");
        mEndTime = System.currentTimeMillis();
        UsageEvents usageEvents = stats.queryEvents(mStartTime, mEndTime);

        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            usageEvents.getNextEvent(event);
            long timestamp = event.getTimeStamp();
            int type = event.getEventType();
            String packageName = event.getPackageName();
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
                    Log.d(TAG, "Added.[" + type + "]" +
                            "\nPackageName is :" + packageName +
                            "\nTimeStamp is :" + dateFormat.format(timeStamp));
                } else {
                    Log.d(TAG, "Skipped because of continuous. " + "[" + type + "]" +
                            "\nPackageName is :" + packageName);
                }
            } else if (type == MOVE_TO_BACKGROUND) {
                // 【バグ対策】本アプリが最初にバックグラウンドに入った時間を記録
                if (mTheFirstType2) {
                    mTheFirstType2 = false;
                    MyAppGoBackgroundTime = timeStamp;
                }

                if (mCheckContinuousType2) {
                    mCheckContinuousType2 = false;
                    mCheckContinuousType1 = true;
                    mSumOfType2 += timestamp;
                    mNumOfType2 += 1;
                    Log.d(TAG, "Added.[" + type + "]" +
                            "\nPackageName is :" + packageName +
                            "\nTimeStamp is :" + dateFormat.format(timeStamp));
                } else if (!mCheckContinuousType2) {
                    Log.d("Check", "skipped because continuous. This is: " + type +
                            "\nPackageName is :" + packageName);
                }
            } else {
                Log.d(TAG, "Skipped because of continuous. " + "[" + type + "]" +
                        "\nPackageName is :" + packageName);
            }
        }
    }


    /*
    * 端末の使用時間（mUsingTime）の算出をするメソッド。
    * */
    private void calculateUsingTime() {
        Log.d(TAG, "@calculateUsingTime: 使用時間の算出");

        if (mNumOfType1 == 0) {
            Log.d(TAG, "未使用のため後続処理を省略、setTimerAgain()へ遷ります。");
            return;
        }

        Log.d(TAG, "checkUsageメソッドに入りました" +
                "\n→ mSumOfType1:" + mSumOfType1 + " mSumOfType2:" + mSumOfType2 +
                "\n→ mNumOfType1:" + mNumOfType1 + " mNumOfType2:" + mNumOfType2);

        if (mNumOfType1 < mNumOfType2) {
            mWaitingTime = (mSumOfType1 + mEndTime) - mSumOfType2
                    + (MyAppGoBackgroundTime - mStartTime);
            Log.d(TAG, "補正します");
        } else {
            mWaitingTime = mSumOfType1 - mSumOfType2
                    + (MyAppGoBackgroundTime - mStartTime);
            Log.d(TAG, "補正しません");
        }

        mTotalTime = mEndTime - mStartTime;
        mUsingTime = mTotalTime - mWaitingTime;

        mThreadSleepTime = (mPreferenceTime - mUsingTime);

        Log.d(TAG, "開始時間: " + dateFormat.format(mStartTime) +
                "\n終了時間" + dateFormat.format(mEndTime) +
                "\n総集計時間: " + mTotalTime / 1000 +
                "\n使用時間: " + mUsingTime / 1000 +
                "\n未使用時間: " + mWaitingTime / 1000 +
                "\nmSumOfType1" + mSumOfType1 +
                "\nmSumOfType2" + mSumOfType2);


        if (mThreadSleepTime < MIN_LOOP_INTERVAL) {
            Log.d(TAG, "待機時間: " + mThreadSleepTime / 1000 +
                    "\nループ間隔が" + MIN_LOOP_INTERVAL / 1000 + "秒以下のため停止します。");
            mThreadSleepTime = 0;
        } else {
            Log.d(TAG, "待機時間: " + mThreadSleepTime / 1000 + " setTimerAgain()へ遷移");
        }
    }



    /*
* Timer.schedule()を使うことでService処理をStopボタンから中止できる。
* */
    private void setTimerAgain() {
        Log.d(TAG, "@setTimerAgain: 待機開始: " + mThreadSleepTime / 1000 + "秒");

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
    }

    /*
    * 【HelperMethod】
    * */
    private void resetField() {
        mSumOfType1 = 0;
        mSumOfType2 = 0;
        mNumOfType1 = 0;
        mNumOfType2 = 0;
        mCheckContinuousType1 = false;
        mCheckContinuousType2 = true;
    }

    /*
    *  【HelperMethod】
    *  Launcherアプリのパッケージ名を取得
    *  （UsageStatsの使用状況判定でLauncherAppをはじかないと、バグが発生するため）
    * */
    public void findLauncherApp() {
        // パッケージマネージャーの作成
        PackageManager packageManager = getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        ResolveInfo resolveInfo = packageManager.resolveActivity(intent, 0);
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        mLauncherPackageName = activityInfo.packageName; // パッケージ名
        Log.d(TAG, "@findLauncherApp: " + mLauncherPackageName);
    }


    /*
    * 【HelperMethod】
    * 使用制限時間をセットするため、Preferenceから分数を読み取る処理
    * */
    private long readPreferenceTime(SharedPreferences sharedPreferences) {
        String originValue = sharedPreferences.getString(
                getString(R.string.pref_limit_time_key),
                getResources().getString(R.string.pref_limit_time_30_value));
        String limitValue = originValue.split("分")[0];
        long limitTime = Long.parseLong(limitValue);
        return mPreferenceTime * limitTime;
    }


    /*
    * ServiceからMainActivityへのBroadcast（UIの更新を依頼）
    * */
    protected void sendBroadCast(String message) {

        Intent broadcastIntent = new Intent();
        broadcastIntent.putExtra("message", message);
        broadcastIntent.setAction("UPDATE_ACTION");
        getBaseContext().sendBroadcast(broadcastIntent);
    }
}
