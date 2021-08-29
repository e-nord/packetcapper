package com.norddev.packetcapper.interfaces;

public interface ITrafficMeter {

    interface Listener {
        void onTrafficRateSampled(int kbps);
    }

    void start();

    void stop();
}
