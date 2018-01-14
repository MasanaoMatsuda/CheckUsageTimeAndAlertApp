package com.product.android.PuncTime;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.DropBoxManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Serializable;

/**
 * Created by masanao on 2018/01/12.
 */

public class Application implements Serializable{

    private Drawable appIcon;
    private String appPackageName;
    private String appName;
    private Long foregroundTime;

    public Application (String packageName, String name, Long time) {
        appPackageName = packageName;
        appName = name;
        foregroundTime = time;
    }

    public void setAppIcon(Drawable appIcon) {
        this.appIcon = appIcon;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public String getAppPackageName() {
        return appPackageName;
    }

    public String getAppName() {
        return appName;
    }

    public Long getForegroundTime() {
        return foregroundTime;
    }
}

