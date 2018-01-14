package com.product.android.PuncTime;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND;
import static android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND;


public class CountTimeService extends Service {

    public static final String TAG = CountTimeService.class.getSimpleName();
    private static final int FOREGROUND_SERVICE_ID = 1234;
    private static final String YOUTUBE_PACKAGE_NAME = "com.google.android.youtube";

    private Timer mTimer = null;
    public Handler mHandler;

    private SharedPreferences mSharedPreferences;
    private long mPreferenceTime = 1000 * 60;
    private long mThreadSleepTime;
    private long mStartTime;
    private long mEndTime;
    private SimpleDateFormat mDateFormat;

    // 開始後の自動スリープに移行するまでの時間をストックする
    private boolean mTheFirstType2 = true;
    private long MyAppGoBackgroundTime;

    // 間隔が短すぎるループを停止させる処理に使う定数(10秒に設定）
    private static final long MIN_LOOP_INTERVAL = 1000 * 10;

    private boolean mCheckContinuousType1 = true;
    private boolean mCheckContinuousType2 = true;
    private long mNumOfType1 = 0;
    private long mNumOfType2 = 0;
    private long mSumOfType1 = 0;
    private long mSumOfType2 = 0;

    // LauncherアプリとSystemUIをextractUsage()にてカウント対象外とするための変数
    private String mLauncherPackageName;
    private final String SYSTEM_UI = "com.android.systemui";
    private final String PUNC_TIME = "com.product.android.PuncTime";

    private Map<String, Long> mUsageHashMap;
    private PackageManager mPackageManager;

    @Override
    public void onCreate() {
        Log.i(TAG, "@onCreate: 変数の初期化処理");

        // 変数の初期化処理
        mHandler = new Handler();
        mDateFormat = new SimpleDateFormat("yyyy/M/d H:mm:ss.SS", Locale.JAPAN);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferenceTime = readPreferenceTime(mSharedPreferences);
        mThreadSleepTime = mPreferenceTime;
        mStartTime = System.currentTimeMillis();
        findLauncherApp();
        mPackageManager = getPackageManager();
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

        startForeground(FOREGROUND_SERVICE_ID, NotificationUtils.remindUserCounting(this));
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
        stopForeground(true);
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
            stopForeground(true);

            String value = mSharedPreferences.getString(
                    getString(R.string.pref_youtube_key),
                    getResources().getString(R.string.pref_youtube_default));

            Intent intent = new Intent(Intent.ACTION_SEARCH)
                    .setPackage(YOUTUBE_PACKAGE_NAME)
                    .putExtra("query", value)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);


            List<ApplicationInfo> appInfoList = mPackageManager.getInstalledApplications(0);
            HashMap<String, String> allAppsHashMap = new HashMap<>();
            for (ApplicationInfo appInfo : appInfoList) {
                allAppsHashMap.put(appInfo.packageName, appInfo.loadLabel(mPackageManager).toString());
            }

            reflectUsageToHashMap();
            Set<String> keySets = mUsageHashMap.keySet();
            List<Application> applicationsList = new ArrayList<>();
            for (String key: keySets) {
                String appName = allAppsHashMap.get(key);
                Long foregroundTime = mUsageHashMap.get(key);
                if (foregroundTime < 0) {
                    foregroundTime += mEndTime;
                }
                applicationsList.add(new Application(key, appName, foregroundTime));
            }

            SerializableUsageStats serializableData =
                    new SerializableUsageStats(mStartTime, mEndTime, applicationsList);

            try {
                // シリアライズ
                serialize(serializableData);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // MainActivityへBroadcastを飛ばす処理をTrigger
            String message = "@Message from CountTimeService[タスク終了]";
            sendBroadCast(message);
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

            // LauncherSystemとSystemUIは、挙動が機種によって異なるため記録を除外。
            // ロジックが崩壊し、エラー発生元になる可能性があるため。
            if (packageName.equals(mLauncherPackageName) | packageName.equals(SYSTEM_UI)) {
                Log.d("Check", "Skipped.[mLauncherPackageName] or [SYSTEM_UI]");
                continue;
            }
            // 【バグ対策】本アプリが最初にバックグラウンドに入った時間を記録
            if (packageName.equals(PUNC_TIME)) {
                if (mTheFirstType2) {
                    mTheFirstType2 = false;
                    MyAppGoBackgroundTime = timestamp;
                    Log.d(TAG, "PuncTime went background."
                            + "[" + mDateFormat.format(MyAppGoBackgroundTime) + "]" +
                            "\nPackageName is :" + packageName);
                }
                Log.d(TAG, "skip PuncTime");
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
                            "\nTimeStamp is :" + mDateFormat.format(timestamp));
                } else {
                    Log.d(TAG, "Skipped because of continuous. " + "[" + type + "]" +
                            "\nPackageName is :" + packageName);
                }
            } else if (type == MOVE_TO_BACKGROUND) {
                if (mCheckContinuousType2) {
                    mCheckContinuousType2 = false;
                    mCheckContinuousType1 = true;
                    mSumOfType2 += timestamp;
                    mNumOfType2 += 1;
                    Log.d(TAG, "Added.[" + type + "]" +
                            "\nPackageName is :" + packageName +
                            "\nTimeStamp is :" + mDateFormat.format(timestamp));
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

        long usingTime;
        if (mNumOfType1 > mNumOfType2) {
            usingTime = (mSumOfType2 + mEndTime) - mSumOfType1;
            Log.d(TAG, "補正します");
        } else {
            usingTime = mSumOfType2 - mSumOfType1;
            Log.d(TAG, "補正しません");
        }

        long totalTime = mEndTime - mStartTime;

        mThreadSleepTime = (mPreferenceTime - usingTime);

        Log.d(TAG, "開始時間: " + mDateFormat.format(mStartTime) +
                "\n終了時間" + mDateFormat.format(mEndTime) +
                "\n総集計時間: " + totalTime / 1000 +
                "\n使用時間: " + usingTime / 1000 +
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
        mCheckContinuousType1 = true;
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


    private void reflectUsageToHashMap() {
        Log.d(TAG, "@");
        mUsageHashMap = new HashMap<>();

        mCheckContinuousType1 = true;
        mCheckContinuousType2 = true;

        UsageStatsManager stats = (UsageStatsManager) getSystemService("usagestats");
        UsageEvents usageEvents = stats.queryEvents(mStartTime, mEndTime);

        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            usageEvents.getNextEvent(event);
            long timestamp = event.getTimeStamp();
            int type = event.getEventType();
            String packageName = event.getPackageName();

            if (packageName.equals(mLauncherPackageName)
                    | packageName.equals(SYSTEM_UI)
                    | packageName.equals("com.product.android.PuncTime")) {
                Log.d("Check", "Skipped.[mLauncherPackageName] or [SYSTEM_UI] or [PuncTime]");
                continue;
            }

            if (type == MOVE_TO_FOREGROUND) {
                if (mCheckContinuousType1) {
                    mCheckContinuousType1 = false;
                    mCheckContinuousType2 = true;
                    reflectToHashMap(type, packageName, timestamp);
                    Log.d(TAG, "Added to HashMap.[" + type + "]" +
                            "\nPackageName is :" + packageName +
                            "\nTimeStamp is :" + mDateFormat.format(timestamp));
                }
            } else if (type == MOVE_TO_BACKGROUND) {
                if (mCheckContinuousType2) {
                    mCheckContinuousType2 = false;
                    mCheckContinuousType1 = true;
                    reflectToHashMap(type, packageName, timestamp);
                    Log.d(TAG, "Added to HashMap.[" + type + "]" +
                            "\nPackageName is :" + packageName +
                            "\nTimeStamp is :" + mDateFormat.format(timestamp));
                }
            }
        }
    }

    private void reflectToHashMap(int type, String packageName, long timestamp) {
        if (type == 1) {
            timestamp = timestamp * (-1);
        }
        if (mUsageHashMap.containsKey(packageName)){
            Long newValue = timestamp + mUsageHashMap.get(packageName);
            mUsageHashMap.remove(packageName);
            mUsageHashMap.put(packageName, newValue);
        } else {
            mUsageHashMap.put(packageName, timestamp);
        }
    }

    /**
     * シリアライズする
     */
    private void serialize(SerializableUsageStats data) throws IOException {

        // Byte配列への出力を行うストリーム
        FileOutputStream fos = openFileOutput("SaveData.dat", MODE_PRIVATE);
        // オブジェクトをストリーム（バイト配列）に変換する為のクラス
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        // オブジェクトをストリームに変換（シリアライズ）
        oos.writeObject(data);
        oos.close();
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
