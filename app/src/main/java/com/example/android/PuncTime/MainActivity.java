package com.example.android.PuncTime;

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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    private Button mButtonStart;
    private Button mButtonStop;
    private TextView mLimitTimeTextView;
    private TextView mYoutubeSourceTextView;
    private UpdateUiReceiver mUpdateUiReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UsageEventsを取得するための設定を確認　
        // ToDo:場所を変える（インストール後突如出てくるためユーザーにとっては謎）
        boolean checkUsageStats = checkForPermission(this);
        if (checkUsageStats == false) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }

        // UIの初期化（Preferenceの設定内容を表示）
        mLimitTimeTextView = (TextView) findViewById(R.id.limit_time);
        mYoutubeSourceTextView = (TextView) findViewById(R.id.youtube_source);
        setupSharedPreferences();


        // SatrtボタンにClickListenerのセット（バックグラウンド処理開始）
        mButtonStart = (Button) findViewById(R.id.stats_btn);
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
        mButtonStop = (Button) findViewById(R.id.stop_btn);
        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(
                        MainActivity.this, CountTimeService.class));
                buttonShowStartHideStop(true);
                Log.i(TAG, "Stop Button is pressed");
            }
        });

        buttonShowStartHideStop(true);


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

        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        unregisterReceiver(mUpdateUiReceiver);
/*
        stopService(new Intent(
                MainActivity.this, CountTimeService.class));
*/
    }



    /*
    *  【HelperMethod】
    *  UsageStatsのパーミッションを確認するため
    * */
    public boolean checkForPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }


    /*
    * 【SharedPreference関連】
    * ①onCreate()にてPreferenceをUIに反映する処理
    * ②OnSharedPreferenceChangeListenerをレジスト（アンレジストはonDestroy()にて）
    * */
    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);

        mLimitTimeTextView.setText(sharedPreferences.getString(
                getString(R.string.pref_limit_time_key),
                getResources().getString(R.string.pref_limit_time_30_value)));
        mYoutubeSourceTextView.setText(sharedPreferences.getString(
                getString(R.string.pref_youtube_key),
                getResources().getString(R.string.pref_youtube_default)));

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    /*
    * 【SharedPreference関連】
    * Preferenceが変更されたときにトリガーされる処理
    * */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_limit_time_key))) {
            mLimitTimeTextView.setText(sharedPreferences.getString(
                    key, getResources().getString(R.string.pref_youtube_default)));
        } else if (key.equals(getString(R.string.pref_youtube_key))) {
            mYoutubeSourceTextView.setText(sharedPreferences.getString(
                    key, getResources().getString(R.string.pref_youtube_default)));
        }
    }


    /*
    * 【オプションメニュー関連】
    * 設定を設置
    * */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void buttonShowStartHideStop(boolean bool) {
        if (bool) {
            mButtonStop.setVisibility(View.GONE);
            mButtonStart.setVisibility(View.VISIBLE);
        } else {
            mButtonStart.setVisibility(View.GONE);
            mButtonStop.setVisibility(View.VISIBLE);
        }
    }


    // サービスから値を受け取ったら動かしたい内容を書く
    private Handler updateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            Bundle bundle = msg.getData();
            String message = bundle.getString("message");

            Log.d(TAG, "@updateHandler " + message);
            buttonShowStartHideStop(true);
        }
    };
}

