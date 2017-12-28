package com.example.android.PuncTime;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    private static final float BUTTON_ELEVATION_SIZE = 12;

    private Button mButtonStart;
    private Button mButtonStop;
    private FloatingActionButton mFabSettings;
    private TextView mLimitTimeTextView;
    private UpdateUiReceiver mUpdateUiReceiver;
    private boolean mCheckUsageStats;

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
            buttonShowStartHideStop(false);
        } else {
            Log.i(TAG, "@isMyServiceRunning: false");
            buttonShowStartHideStop(true);
        }

        mLimitTimeTextView = (TextView) findViewById(R.id.limit_time);
        mFabSettings = (FloatingActionButton) findViewById(R.id.fab_settings);
        setupSharedPreferences();


        // UsageEventsを取得するための設定を確認　
        // ToDo:場所を変える（インストール後突如出てくるためユーザーにとっては謎）
        mCheckUsageStats = checkForPermission(MainActivity.this);
        if (!mCheckUsageStats) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }


        // SatrtボタンにClickListenerのセット（バックグラウンド処理開始）
        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 念のためstopServiceを先に呼出してリセット
                stopService(new Intent(
                        MainActivity.this, CountTimeService.class));

                Log.i(TAG, "Start Button is pressed");
                startService(new Intent(
                        MainActivity.this, CountTimeService.class));

                buttonShowStartHideStop(false);
            }
        });

        // StopボタンにClickListenerのセット（バックグラウンド処理停止）
        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(
                        MainActivity.this, CountTimeService.class));
                buttonShowStartHideStop(true);
                Log.i(TAG, "Stop Button is pressed");
            }
        });

        mFabSettings.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this,
                        SettingsActivity.class));
            }
        });

        // Service終了後にMessageを受け取るための前処理
        mUpdateUiReceiver = new UpdateUiReceiver();
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction("UPDATE_ACTION");
        registerReceiver(mUpdateUiReceiver, mIntentFilter);
        mUpdateUiReceiver.registerHandler(updateHandler);
    }


    /*
    * SharedPreferenceChangeListenerをアンレジストする
    * */
    @Override
    protected void onDestroy() {
        Log.i(TAG, "@onDestroy");

        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mUpdateUiReceiver);
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
    */
/*
    * 【オプションメニュー関連】
    * 設定を設置
    * *//*

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "@onCreateOptionsMenu");

        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "@onOptionsItemSelected");

        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
*/


    private void buttonShowStartHideStop(boolean bool) {
        Log.i(TAG, "@buttonShowStartHideStop");

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
    public static float convertDpToPixel(float dp, Context context){
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * metrics.density;
    }


    // サービスから値を受け取ったら動かしたい内容を書く
    private Handler updateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle = msg.getData();
            String message = bundle.getString("message");

            Log.d(TAG, "@updateHandler " + message);
            buttonShowStartHideStop(true);
            stopService(new Intent(
                    MainActivity.this, CountTimeService.class));
            buttonShowStartHideStop(true);
        }
    };
}

