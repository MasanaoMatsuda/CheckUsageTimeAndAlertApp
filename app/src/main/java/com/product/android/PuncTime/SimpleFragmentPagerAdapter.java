package com.product.android.PuncTime;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;

/**
 * Created by masanao on 2018/01/13.
 */

public class SimpleFragmentPagerAdapter extends FragmentPagerAdapter{

    private static final String TAG = SimpleFragmentPagerAdapter.class.getSimpleName();
    private Context mContext;

    public SimpleFragmentPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        mContext = context;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new MainFragment();
            case 1:
                return new UsageReportFragment();
            default:
                Log.e(TAG, "Error at getItem().Cannot find the position.");
                return null;
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return mContext.getString(R.string.page_title_home);
            case 1:
                return mContext.getString(R.string.page_title_report);
            default:
                Log.e(TAG, "Error at getPageTitle().Cannot find the position.");
                return null;
        }
    }
}
