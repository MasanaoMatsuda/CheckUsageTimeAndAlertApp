package com.product.android.PuncTime;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;


public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();

    private FinishServiceReceiver mFinishServiceReceiver;
    private static final String RESET_UI_CALL_STOP_SERVICE = "UPDATE_ACTION";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "@onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager viewPager = (ViewPager)findViewById(R.id.viewpager);
        SimpleFragmentPagerAdapter adapter =
                new SimpleFragmentPagerAdapter(this, getSupportFragmentManager());
        viewPager.setAdapter(adapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);

        // Receiverの登録（Service終了後にMessageを受け取るため）
        mFinishServiceReceiver = new FinishServiceReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RESET_UI_CALL_STOP_SERVICE);
        registerReceiver(mFinishServiceReceiver, intentFilter);
        mFinishServiceReceiver.registerHandler(updateHandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mFinishServiceReceiver);
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
            stopService(new Intent(MainActivity.this, CountTimeService.class));
            NotificationUtils.remindUserFinishedService(MainActivity.this);
        }
    };
}

