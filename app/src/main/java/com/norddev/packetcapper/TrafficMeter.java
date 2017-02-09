package com.norddev.packetcapper;

import android.net.TrafficStats;
import android.os.Handler;

import java.util.Locale;

public class TrafficMeter {

    private final Handler mHandler;
    private final Listener mListener;
    private boolean mIsRunning;

    public interface Listener {
        void onTrafficRateSampled(int kbps);
    }

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

    private final Runnable mRunnable = new Runnable() {
        private long mLastTotalBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        @Override
        public void run() {
            if(mIsRunning) {
                long currentTotalBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
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
