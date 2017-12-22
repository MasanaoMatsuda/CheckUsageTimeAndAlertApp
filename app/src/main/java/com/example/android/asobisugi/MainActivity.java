package com.example.android.asobisugi;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

    public String mLauncherPackageName;
    private Button mButtonStart;
    private Button mButtonStop;
    private TextView mLimitTimeTextView;
    private TextView mYoutubeSourceTextView;


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

        // バグ回避のための前処理（Launcherアプリのパッケージ名を取得）
        findLauncherApp();


        // UIの初期化（Preferenceの設定内容を表示）
        mLimitTimeTextView = (TextView) findViewById(R.id.limit_time);
        mYoutubeSourceTextView = (TextView) findViewById(R.id.youtube_source);
        setupSharedPreferences();


        // SatrtボタンにClickListenerのセット（バックグラウンド処理開始）
        mButtonStart = (Button) findViewById(R.id.stats_btn);
        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                stopService(new Intent(
                        MainActivity.this, CountTimeService.class));

                Log.i(TAG, "Start Button was pressed" +
                        "\nThread ID: " + Thread.currentThread().getId());
                Intent intent = new Intent(
                        MainActivity.this, CountTimeService.class);
                intent.setPackage(mLauncherPackageName);
                startService(intent);

                mButtonStart.setVisibility(View.GONE);
                mButtonStop.setVisibility(View.VISIBLE);
            }
        });

        // StopボタンにClickListenerのセット（バックグラウンド処理停止）
        mButtonStop = (Button) findViewById(R.id.stop_btn);
        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(
                        MainActivity.this, CountTimeService.class));
                Log.i(TAG, "Stop Button was pressed" +
                        "\nThread ID: " + Thread.currentThread().getId());
            }
        });

        mButtonStart.setVisibility(View.VISIBLE);
        mButtonStop.setVisibility(View.GONE);
    }

    /*
    * SharedPreferenceChangeListenerをアンレジストする
    * */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
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
        Log.d("Check", "Inside findLauncherApp: " + mLauncherPackageName);
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
}

