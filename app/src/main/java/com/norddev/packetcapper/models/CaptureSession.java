package com.norddev.packetcapper.models;

import com.norddev.packetcapper.interfaces.ICaptureOptions;
import com.norddev.packetcapper.interfaces.ICaptureSession;

public class CaptureSession implements ICaptureSession {
    private final ICaptureOptions mOptions;

    public CaptureSession(ICaptureOptions captureOptions) {
        mOptions = captureOptions;
    }

    public ICaptureOptions getOptions() {
        return mOptions;
    }

    public long getCaptureSize() {
        return mOptions.getOutputFile().length();
    }
}