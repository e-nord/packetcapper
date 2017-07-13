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
        if(!Hawk.contains(PacketCapperActivity.PREF_KEY_OUTPUT_DIRECTORY)) {
            Hawk.put(PacketCapperActivity.PREF_KEY_OUTPUT_DIRECTORY, getDefaultOutputDirectoryPath());
        }
        if(!Hawk.contains(PacketCapperActivity.PREF_KEY_CAPTURE_FILE_NAME_FORMAT)){
            Hawk.put(PacketCapperActivity.PREF_KEY_CAPTURE_FILE_NAME_FORMAT, getDefaultCatpureFileNameFormat());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                PacketCapper.killAll(PacketCapperApplication.this);
            }
        }));
    }

    public static String getDefaultOutputDirectoryPath(){
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static String getDefaultCatpureFileNameFormat(){
        return "capture_%D.pcap";
    }
}
