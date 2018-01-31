package com.product.android.PuncTime;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;


/**
 * Created by masanao on 2018/01/19.
 */

public class NotifyEndActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notify_end);
    }

    public void backToMain(View view) {
        startActivity(new Intent(this, MainActivity.class));
    }
}
