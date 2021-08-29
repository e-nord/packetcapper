package com.norddev.packetcapper.interfaces;

import com.norddev.packetcapper.models.CaptureError;

public interface IPacketCapper {

    interface Listener {
        void onError(CaptureError error);

        void onStart(ICaptureSession session);

        void onStop();
    }

    void startCapture(ICaptureOptions options);

    void stopCapture();
}
