package com.norddev.packetcapper.models;

import com.norddev.packetcapper.interfaces.ICaptureError;

public class CaptureError implements ICaptureError {

    private final String mMessage;

    public CaptureError(String message) {
        this.mMessage = message;
    }

    @Override
    public String getMessage() {
        return mMessage;
    }
}
