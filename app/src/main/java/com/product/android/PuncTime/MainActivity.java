package com.product.android.PuncTime;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    private static final float BUTTON_ELEVATION_SIZE = 12;
    private static final String CHECK_SETTING_IS_NOT_DEFAULT = "お気に入りの曲名を登録！";
    private static final String ALERT_MESSAGE_SET_MUSIC =
            "「設定」ページにて、好きな曲名を登録してください。\n\n" +
                    "スマホ使用時間が設定タイムに達すると指定された楽曲を流してお知らせします。";
    private static final String ALERT_MESSAGE_GET_USAGE_STATS_PERMISSION =
            "「使用履歴へのアクセス」を許可してください。\n\n時間計測に必要です。";
    private static final String RESET_UI_CALL_STOP_SERVICE = "UPDATE_ACTION";

    private Button mButtonStart;
    private Button mButtonStop;
    private TextView mLimitTimeTextView;
    private FinishServiceReceiver mFinishServiceReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "@onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // UIの初期化（Preferenceの設定内容・onSaveInstanceStateの保存内容を表示）
        mButtonStart = (Button) findViewById(R.id.stats_btn);
        mButtonStop = (Button) findViewById(R.id.stop_btn);

        if (isMyServiceRunning(CountTimeService.class)) {
            Log.i(TAG, "@isMyServiceRunning: True");
            changeButtonUsability(false);
        } else {
            Log.i(TAG, "@isMyServiceRunning: false");
            changeButtonUsability(true);
        }

        mLimitTimeTextView = (TextView) findViewById(R.id.limit_time);
        FloatingActionButton fabSettings = (FloatingActionButton) findViewById(R.id.fab_settings);
        setupSharedPreferences();


        /*
        * clickListenerのセット(for Start, Stop and Floating Action Button)
        * */
        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // UsageEventsを取得するための設定を確認　
                boolean checkUsageStats = checkForPermission(MainActivity.this);
                if (!checkUsageStats) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage(ALERT_MESSAGE_GET_USAGE_STATS_PERMISSION)
                            .setPositiveButton(getString(R.string.get_permission_title), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                                }
                            })
                            .show();
                    return;
                }


                /*
                * 設定時間経過時に流すYoutubeの楽曲が指定されているかをチェックする
                * （指定されていない場合はAlertDialogを出して設定画面に誘導）
                * */
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                String value = sharedPreferences.getString(
                        getString(R.string.pref_youtube_key),
                        getResources().getString(R.string.pref_youtube_default));
                if (value.equals(CHECK_SETTING_IS_NOT_DEFAULT) | value.equals("")) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage(ALERT_MESSAGE_SET_MUSIC)
                            .setPositiveButton(getString(R.string.settings_title), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                                }
                            })
                            .show();
                    return;
                }

                /*
                * Youtubeの設定が確認できたらServiceに処理を投げる
                * */
                Log.i(TAG, "Start Button is pressed");
                startService(new Intent(
                        MainActivity.this, CountTimeService.class));
                changeButtonUsability(false);
            }
        });

        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(
                        MainActivity.this, CountTimeService.class));
                changeButtonUsability(true);
                Log.i(TAG, "Stop Button is pressed");
            }
        });

        fabSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        SettingsActivity.class));
            }
        });

        // Receiverの登録（Service終了後にMessageを受け取るため）
        mFinishServiceReceiver = new FinishServiceReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RESET_UI_CALL_STOP_SERVICE);
        registerReceiver(mFinishServiceReceiver, intentFilter);
        mFinishServiceReceiver.registerHandler(updateHandler);
    }


    /*
    * SharedPreferenceChangeListenerをアンレジストする
    * */
    @Override
    protected void onDestroy() {
        Log.i(TAG, "@onDestroy");

        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mFinishServiceReceiver);
        super.onDestroy();
    }


    /*
    *  【HelperMethod】
    *  UsageStatsのパーミッションを確認するため
    * */
    public boolean checkForPermission(Context context) {
        Log.i(TAG, "@checkForPermission");

        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }


    /*
    *  【HelperMethod】
    *  Serviceの起動状態にUIのボタン表示を同調させる* */
    private boolean isMyServiceRunning(Class<CountTimeService> serviceClass) {
        Log.i(TAG, "@isMyServiceRunning");

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    /*
    * 【SharedPreference関連】
    * ①onCreate()にてPreferenceをUIに反映する処理
    * ②OnSharedPreferenceChangeListenerをレジスト（アンレジストはonDestroy()にて）
    * */
    private void setupSharedPreferences() {
        Log.i(TAG, "@setupSharedPreferences");

        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);

        mLimitTimeTextView.setText(sharedPreferences.getString(
                getString(R.string.pref_limit_time_key),
                getResources().getString(R.string.pref_limit_time_30_value)));
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    /*
    * 【SharedPreference関連】
    * Preferenceが変更されたときにトリガーされる処理
    * */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.i(TAG, "@onSharedPreferenceChanged");

        if (key.equals(getString(R.string.pref_limit_time_key))) {
            mLimitTimeTextView.setText(sharedPreferences.getString(
                    key, getResources().getString(R.string.pref_youtube_default)));
        }
    }


    /*
    * 【HelperMethod】
    * Start・StopボタンのService起動状態に応じた有効・無効化
    * */
    private void changeButtonUsability(boolean bool) {
        Log.i(TAG, "@changeButtonUsability");

        if (bool) {
            mButtonStart.setEnabled(true);
            mButtonStart.setTextColor(0xff4200b7);
            mButtonStart.setElevation(
                    convertDpToPixel(BUTTON_ELEVATION_SIZE, this));
            mButtonStop.setEnabled(false);
            mButtonStop.setTextColor(0xffaaaaaa);
            mButtonStop.setElevation(0);
        } else {
            mButtonStart.setEnabled(false);
            mButtonStart.setTextColor(0xffaaaaaa);
            mButtonStart.setElevation(0);
            mButtonStop.setEnabled(true);
            mButtonStop.setTextColor(0xff4200b7);
            mButtonStop.setElevation(
                    convertDpToPixel(BUTTON_ELEVATION_SIZE, this));
        }
    }

    /*
    * 【HelperMethod】
    * diを指定したらpixel値に変換するメソッド
    * */
    public static float convertDpToPixel(float dp, Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * metrics.density;
    }


    /*
    * Service終了を知らせるMessageのHandler
    * Messageを受けてstopService()とボタンの状態を更新する。
    * */
    private Handler updateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle = msg.getData();
            String message = bundle.getString("message");

            Log.d(TAG, "@updateHandler " + message);
            stopService(new Intent(
                    MainActivity.this, CountTimeService.class));
            changeButtonUsability(true);
        }
    };
}

