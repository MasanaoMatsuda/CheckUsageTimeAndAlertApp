package com.product.android.PuncTime;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Created by masanao on 2018/01/14.
 */

public class ApplicationAdapter extends ArrayAdapter<Application> {

    public ApplicationAdapter(@NonNull Context context, @NonNull List<Application> objects) {
        super(context, 0, objects);
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItemView = convertView;
        if (listItemView == null) {
            listItemView = LayoutInflater.from(getContext())
                    .inflate(R.layout.report_list_item, parent, false);
        }

        Application currentApp = getItem(position);

        ImageView appIconImageView = (ImageView) listItemView.findViewById(R.id.report_app_icon);
        appIconImageView.setImageDrawable(currentApp.getAppIcon());
        TextView appNameTextView = (TextView) listItemView.findViewById(R.id.report_app_name);
        appNameTextView.setText(currentApp.getAppName());
        TextView timeTextView = (TextView) listItemView.findViewById(R.id.report_total_time);
        Long min = currentApp.getForegroundTime() / 1000 / 60;
        Long sec = currentApp.getForegroundTime() / 1000 % 60;
        timeTextView.setText(min.toString() + "分 " + sec.toString() + "秒");

        return listItemView;
    }
}
