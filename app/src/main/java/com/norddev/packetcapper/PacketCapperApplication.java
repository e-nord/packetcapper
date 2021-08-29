package com.norddev.packetcapper;

import android.app.Application;
import android.util.Log;

import com.norddev.packetcapper.helpers.ProcessHelper;
import com.norddev.packetcapper.models.CaptureOptions;
import com.norddev.packetcapper.models.TCPDump;
import com.orhanobut.hawk.Hawk;
import com.orhanobut.hawk.NoEncryption;

import java.io.File;

public class PacketCapperApplication extends Application {

    private static final String TAG = "PacketCapperApplication";
    private TCPDump mTCPDump;

    @Override
    public void onCreate() {
        super.onCreate();

        Hawk.init(this).setEncryption(new NoEncryption()).build();

        mTCPDump = new TCPDump(new File(getFilesDir(), "tcpdump"));

        CaptureOptions.Default.initDefaults();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.d(TAG, "Killing all tcpdump instances...");
            ProcessHelper.killAll(mTCPDump.getExecutable().getName());
        }));
    }

    public TCPDump getTCPDump() {
        return mTCPDump;
    }

}
