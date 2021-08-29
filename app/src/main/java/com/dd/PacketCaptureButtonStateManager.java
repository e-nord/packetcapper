//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.dd;

class PacketCaptureButtonStateManager {
    private final boolean mIsEnabled;
    private int mProgress;

    public PacketCaptureButtonStateManager(PacketCaptureButton progressButton) {
        this.mIsEnabled = progressButton.isEnabled();
        this.mProgress = progressButton.getProgress();
    }

    public void saveProgress(PacketCaptureButton progressButton) {
        this.mProgress = progressButton.getProgress();
    }

    public boolean isEnabled() {
        return this.mIsEnabled;
    }

    public int getProgress() {
        return this.mProgress;
    }

    public void checkState(PacketCaptureButton progressButton) {
        if(progressButton.getProgress() != this.getProgress()) {
            progressButton.setProgress(progressButton.getProgress());
        } else if(progressButton.isEnabled() != this.isEnabled()) {
            progressButton.setEnabled(progressButton.isEnabled());
        }

    }
}