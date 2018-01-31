package com.product.android.PuncTime;


import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.CycleInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class MainFragment extends Fragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = MainFragment.class.getSimpleName();

    private static final float BUTTON_ELEVATION_SIZE = 12;

    private Button mButtonStart;
    private Button mButtonStop;
    private FloatingActionButton mFab;
    private Button mButtonToast;
    private TextView mLimitTimeTextView;
    private SharedPreferences mSharedPreferences;
    private ImageView mImg;
    private RotateAnimation mRotateAnim;


    public MainFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "@onCreateVew in Fragment");

        // UIの初期化（Preferenceの設定内容・onSaveInstanceStateの保存内容を表示）
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mButtonStart = (Button) rootView.findViewById(R.id.stats_btn);
        mButtonStop = (Button) rootView.findViewById(R.id.stop_btn);
        mLimitTimeTextView = (TextView) rootView.findViewById(R.id.limit_time);
        mFab = (FloatingActionButton) rootView.findViewById(R.id.fab_settings);
        mButtonToast = (Button)rootView.findViewById(R.id.transparent_toast_button);
        mImg = (ImageView) rootView.findViewById(R.id.animation_img);


        // アニメーションをセット
        mRotateAnim = new RotateAnimation(
                0.0f, 360.0f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        mRotateAnim.setInterpolator(new LinearInterpolator());
        mRotateAnim.setDuration(1000 * 10);
        mRotateAnim.setRepeatCount(Animation.INFINITE);


        // SharedPreferenceのリスナーをセット
        Log.i(TAG, "@setupSharedPreferences");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);


        // clickListenerのセット(Start/Stop/FAB)
        mButtonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // UsageEventsを取得するための設定を確認　
                boolean checkUsageStats = checkForPermission(getContext());
                if (!checkUsageStats) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setMessage(getString(R.string.alert_msg_get_usage_stats_permission))
                            .setPositiveButton(getString(R.string.get_permission_title), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                                }
                            })
                            .show();
                    return;
                }

                // 通知のキャンセル
                NotificationUtils.cancelNotification(getContext());

                // アニメーションを開始する
                mImg.startAnimation(mRotateAnim);

                // Serviceに処理を投げる
                Log.i(TAG, "Start Button is pressed");
                getActivity().startService(new Intent(getActivity(), CountTimeService.class));
                changeButtonUsability(false);
            }
        });

        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "Stop Button is pressed");

                // アニメーションを停止する
                mRotateAnim.cancel();

                // Serviceを停止する
                getActivity().stopService(new Intent(getActivity(), CountTimeService.class));
                changeButtonUsability(true);
            }
        });

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
            }
        });

        mButtonToast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), R.string.setting_fab_disabled, Toast.LENGTH_SHORT)
                        .show();
            }
        });

        return rootView;
    }


    @Override
    public void onResume() {
        super.onResume();

        if (isMyServiceRunning(CountTimeService.class)) {
            Log.i(TAG, "@isMyServiceRunning: True");
            changeButtonUsability(false);
            mImg.startAnimation(mRotateAnim);
        } else {
            Log.i(TAG, "@isMyServiceRunning: false");
            changeButtonUsability(true);
        }

        String limitTime = mSharedPreferences.getString(
                getString(R.string.pref_limit_time_key),
                getResources().getString(R.string.pref_limit_time_15_value));
        String limitValue = limitTime.split("分")[0];
        mLimitTimeTextView.setText(limitValue);
    }


    @Override
    public void onStop() {
        super.onStop();
        mRotateAnim.cancel();
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "@onDestroy");

        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }


    /*
    *  【HelperMethod】
    *  UsageStatsのパーミッションを確認するため* */
    public boolean checkForPermission(Context context) {
        Log.i(TAG, "@checkForPermission");

        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getActivity().getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }


    /*
    *  【HelperMethod】
    *  Serviceの起動状態にUIのボタン表示を同調させる* */
    private boolean isMyServiceRunning(Class<CountTimeService> serviceClass) {
        Log.i(TAG, "@isMyServiceRunning");

        ActivityManager manager = (ActivityManager) getActivity()
                .getSystemService(Context.ACTIVITY_SERVICE);
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
    * Preferenceが変更されたときにトリガーされる処理* */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.i(TAG, "@onSharedPreferenceChanged");

        if (key.equals(getString(R.string.pref_limit_time_key))) {
            mLimitTimeTextView.setText(sharedPreferences.getString(key, ""));
        }
    }


    /*
    * 【HelperMethod】
    * Start・StopボタンのService起動状態に応じた有効・無効化* */
    private void changeButtonUsability(boolean bool) {
        Log.i(TAG, "@changeButtonUsability");

        if (bool) {
            mButtonStart.setEnabled(true);
            mButtonStart.setTextColor(0xff4200b7);
            mButtonStart.setElevation(
                    convertDpToPixel(BUTTON_ELEVATION_SIZE, getContext()));
            mButtonStop.setEnabled(false);
            mButtonStop.setTextColor(0xffaaaaaa);
            mButtonStop.setElevation(0);
            mFab.setEnabled(true);
            mButtonToast.setEnabled(false);
        } else {
            mButtonStart.setEnabled(false);
            mButtonStart.setTextColor(0xffaaaaaa);
            mButtonStart.setElevation(0);
            mButtonStop.setEnabled(true);
            mButtonStop.setTextColor(0xff4200b7);
            mButtonStop.setElevation(
                    convertDpToPixel(BUTTON_ELEVATION_SIZE, getContext()));
            mFab.setEnabled(false);
            mButtonToast.setEnabled(true);
        }
    }


    /*
    * 【HelperMethod】
    * diを指定したらpixel値に変換するメソッド* */
    public static float convertDpToPixel(float dp, Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return dp * metrics.density;
    }
}
