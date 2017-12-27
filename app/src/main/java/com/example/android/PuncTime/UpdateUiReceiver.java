package com.example.android.PuncTime;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Created by masanao on 2017/12/27.
 */

public class UpdateUiReceiver extends BroadcastReceiver {

    public static Handler mHandler;


    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle bundle = intent.getExtras();
        String message = bundle.getString("message");

        if (mHandler != null) {
            Message msg = new Message();

            Bundle data = new Bundle();
            data.putString("message", message);
            msg.setData(data);
            mHandler.sendMessage(msg);
        }
    }

    /**
     * メイン画面の表示を更新
     */
    public void registerHandler(Handler uiUpdateHandler) {
        mHandler = uiUpdateHandler;
    }
}
