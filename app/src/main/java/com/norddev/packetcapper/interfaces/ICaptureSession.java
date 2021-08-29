package com.norddev.packetcapper.interfaces;

public interface ICaptureSession {
    ICaptureOptions getOptions();

    long getCaptureSize();
}
