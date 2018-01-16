package com.product.android.PuncTime;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;


public class UsageReportFragment extends Fragment {

    private TextView mDate;
    private TextView mStartTime;
    private TextView mEndTime;
    private TextView mTotalTime;
    private ListView mUsageList;
    private PackageManager mPackageManager;
    private SimpleDateFormat mFormatDate;
    private SimpleDateFormat mFormatTime;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_report, container, false);
        mDate = (TextView) rootView.findViewById(R.id.report_page_date);
        mStartTime = (TextView) rootView.findViewById(R.id.report_page_time_start);
        mEndTime = (TextView) rootView.findViewById(R.id.report_page_time_end);
        mTotalTime = (TextView) rootView.findViewById(R.id.report_page_time_total);
        mUsageList = (ListView) rootView.findViewById(R.id.report_page_usage_list);
        mPackageManager = getActivity().getPackageManager();
        mFormatTime = new SimpleDateFormat("H:mm", Locale.JAPAN);
        mFormatDate = new SimpleDateFormat("YYYY/ M/ d", Locale.JAPAN);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUsageReport();
    }

    private void updateUsageReport() {
        try {
            SerializableUsageStats deSerializableData = deSerialize();
            if (deSerializableData == null) {
                return;
            }

            long startTime = deSerializableData.getStartTime();
            long endTime = deSerializableData.getEndTime();
            List<Application> applications = deSerializableData.getApplicationList();
            long totalMin = (endTime - startTime) / 1000 / 60;

            mDate.setText(String.valueOf(mFormatDate.format(startTime)));
            mStartTime.setText(String.valueOf(mFormatTime.format(startTime)));
            mEndTime.setText(String.valueOf(mFormatTime.format(endTime)));
            mTotalTime.setText("("
                    + getString(R.string.report_page_label_about) + " "
                    + String.valueOf(totalMin) + " "
                    + getString(R.string.report_page_label_min) + ")");
            for (Application app : applications) {
                Drawable icon = mPackageManager.getApplicationIcon(app.getAppPackageName());
                app.setAppIcon(icon);
            }
            ApplicationAdapter arrayAdapter =
                    new ApplicationAdapter(getContext(), applications);
            mUsageList.setAdapter(arrayAdapter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * デシリアライズ
     */
    private SerializableUsageStats deSerialize() throws ClassNotFoundException, IOException {

        File file = getActivity().getFileStreamPath("SaveData.dat");
        if (file.exists()) {
            FileInputStream fis = getActivity().openFileInput("SaveData.dat");
            ObjectInputStream ois = new ObjectInputStream(fis);
            SerializableUsageStats data = (SerializableUsageStats) ois.readObject();
            ois.close();
            return data;
        } else {
            return null;
        }
    }
}
