package com.norddev.packetcapper;

import android.app.Application;
import android.os.Environment;

import com.orhanobut.hawk.Hawk;
import com.orhanobut.hawk.NoEncryption;

public class PacketCapperApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Hawk.init(this).setEncryption(new NoEncryption()).build();
        Hawk.put(PacketCapperActivity.PREF_KEY_OUTPUT_DIRECTORY, Environment.getExternalStorageDirectory().getAbsolutePath());
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                PacketCapper.killAll(PacketCapperApplication.this);
            }
        }));
    }
}
