package com.norddev.packetcapper.services;

import static android.app.PendingIntent.getActivity;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.norddev.packetcapper.R;
import com.norddev.packetcapper.activities.PacketCapperActivity;

public class PacketCapperService extends Service {

    private NotificationCompat.Builder mBuilder;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBuilder = new NotificationCompat.Builder(this, "packetcapper");
        showNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissNotification();
    }

    public static Intent getIntent(Context context){
        return new Intent(context, PacketCapperService.class);
    }

    public static void start(Context context){
        context.startService(getIntent(context));
    }

    public static void stop(Context context){
        context.stopService(getIntent(context));
    }

    private void showNotification(){
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mBuilder.setOngoing(true);
        mBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_app_notif));
        //mBuilder.setSmallIcon(android.R.drawable.stat_sys_download_done);
        mBuilder.setContentTitle("Packet capture in progress...");
        mBuilder.setContentText("Tap to return to app");
        Intent intent = PacketCapperActivity.getIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mBuilder.setContentIntent(getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        startForeground(1337, mBuilder.build());
    }

    private void dismissNotification(){
        stopForeground(true);
    }
}
