package com.example.android.asobisugi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by masanao on 2017/12/13.
 */

public class NotificationUtils {

    private static final int ON_GOING_REMINDER_NOTIFICATION_ID = 1138;
    private static final String ON_GOING_REMINDER_NOTIFICATION_CHANNEL_ID =
            "on_going_notification_channel";
    private static final int ON_GOING_REMINDER_PENDING_INTENT_ID = 2482;
    private static final int CANCEL_COUNT_TIME_PENDING_INTENT_ID = 3272;


    public static void remindUserBecauseCharging(Context context) {
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(
                    ON_GOING_REMINDER_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.main_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(mChannel);
        }

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context,
                        ON_GOING_REMINDER_NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .setSmallIcon(R.drawable.ic_timer_black_24dp)
                .setLargeIcon(largeIcon(context))
                .setContentTitle(context.getString(R.string.on_going_reminder_notification_title))
                .setContentText(context.getString(R.string.on_going_reminder_notification_body))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.on_going_reminder_notification_body)))
                .setContentIntent(contentIntent(context))
                .addAction(cancelCountTimeAction(context))
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }
        notificationManager.notify(
                ON_GOING_REMINDER_NOTIFICATION_ID, notificationBuilder.build());
    }

    private static NotificationCompat.Action cancelCountTimeAction(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(CountTimeTask.ACTION_CANCEL_COUNT_TIME);
        PendingIntent cancelPendingIntent = PendingIntent.getService(context,
                CANCEL_COUNT_TIME_PENDING_INTENT_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Action(R.drawable.ic_delete_forever_black_24dp,
                "キャンセル",
                cancelPendingIntent);
    }

    private static PendingIntent contentIntent(Context context) {
        Intent startActivityIntent = new Intent(context, MainActivity.class);

        return PendingIntent.getActivity(
                context,
                ON_GOING_REMINDER_PENDING_INTENT_ID,
                startActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Bitmap largeIcon(Context context) {
        Resources res = context.getResources();
        Bitmap largeIcon = BitmapFactory.decodeResource(res, R.drawable.ic_timer_black_24dp);
        return largeIcon;
    }


    public static void clearAllNotifications(Context context) {
        NotificationManager notificationManager =
                (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

}
