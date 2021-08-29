package com.norddev.packetcapper;

import android.net.TrafficStats;
import android.os.Handler;

import com.norddev.packetcapper.interfaces.ITrafficMeter;

import java.util.Locale;

public class TrafficMeter implements ITrafficMeter {

    private final Handler mHandler;
    private final Listener mListener;
    private boolean mIsRunning;
    private long mLastTotalBytes;

    public TrafficMeter(Listener listener){
        mListener = listener;
        mHandler = new Handler();
        mIsRunning = false;
    }

    public static String formatNetworkRate(int kbps){
        String suffix;
        if(kbps >= 1000){
            suffix = "Mbps";
            kbps /= 1000;
        } else {
            suffix = "Kbps";
        }
        return String.format(Locale.US, "%d %s", kbps, suffix);
    }

    private long getTotalIOBytes(){
        return TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if(mIsRunning) {
                long currentTotalBytes = getTotalIOBytes();
                int kbps = (int) (((currentTotalBytes - mLastTotalBytes) * 8.0) / 1000);
                mLastTotalBytes = currentTotalBytes;
                mListener.onTrafficRateSampled(kbps);
                mHandler.postDelayed(mRunnable, 1000);
            }
        }
    };

    public void start(){
        if(!mIsRunning) {
            mIsRunning = true;
            mLastTotalBytes = getTotalIOBytes();
            mHandler.postDelayed(mRunnable, 1000);
        }
    }

    public void stop(){
        if(mIsRunning) {
            mIsRunning = false;
            mHandler.removeCallbacks(mRunnable);
        }
    }
}
