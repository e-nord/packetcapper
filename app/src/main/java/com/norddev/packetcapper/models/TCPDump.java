package com.norddev.packetcapper.models;

import com.norddev.packetcapper.interfaces.ITCPDump;

import java.io.File;

public class TCPDump implements ITCPDump {

    private final File mExecutableFile;

    public TCPDump(File file) {
        mExecutableFile = file;
    }

    @Override
    public File getExecutable() {
        return mExecutableFile;
    }
}
