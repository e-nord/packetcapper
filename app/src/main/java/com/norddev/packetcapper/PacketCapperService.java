package com.norddev.packetcapper;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;

import static android.app.PendingIntent.getActivity;

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
        mBuilder = new NotificationCompat.Builder(this);
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
        mBuilder.setSmallIcon(android.R.drawable.ic_dialog_info);
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mBuilder.setOngoing(true);
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setContentTitle("Capture in progress...");
        mBuilder.setContentText("Tap to return to app");
        Intent intent = PacketCapperActivity.getIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mBuilder.setContentIntent(getActivity(this, 0, intent, 0));
        startForeground(1337, mBuilder.build());
    }

    private void dismissNotification(){
        stopForeground(true);
    }
}