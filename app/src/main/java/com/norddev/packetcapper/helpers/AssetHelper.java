package com.norddev.packetcapper.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.norddev.packetcapper.R;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

public class AssetHelper {

    private final WeakReference<Context> mContext;
    private final String mAssetPath;
    private final Handler mMainHandler;

    private boolean mMakeExecutable;

    public AssetHelper(Context context, String assetPath) {
        mContext = new WeakReference<>(context);
        mAssetPath = assetPath;
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static void extract(Context context, String filePath, File file) throws IOException {
        if (file.getParentFile() == null || (!file.getParentFile().exists() && !file.getParentFile().mkdirs())) {
            throw new IOException("Failed to create parent directory: " + file.getParent());
        }
        try(FileOutputStream out = new FileOutputStream(file)) {
            try(InputStream in = context.getAssets().open(filePath)){
                IOUtils.copy(in, out);
                out.flush();
            }
        }
    }

    private class AssertExtractorRunnable implements Runnable {
        private AlertDialog mDialog;
        private final File mOutputFile;

        public AssertExtractorRunnable(File outputFile) {
            mOutputFile = outputFile;
        }

        private void showDialog() {
            mDialog = new AlertDialog.Builder(mContext.get())
                    .setTitle(R.string.setting_up)
                    .setMessage(R.string.please_wait)
                    .show();
        }

        private void dismissDialog() {
            if(mDialog != null){
                mDialog.dismiss();
            }
        }

        @Override
        public void run() {
            mMainHandler.post(this::showDialog);

            try {
                AssetHelper.extract(mContext.get(), mAssetPath, mOutputFile);
                if(mMakeExecutable) {
                    ProcessBuilder builder = new ProcessBuilder("chmod", "755", mOutputFile.getAbsolutePath());
                    Process process = builder.start();
                    process.waitFor();
                    if (process.exitValue() != 0) {
                        throw new IOException("Failed to make extracted asset executable");
                    }
                }

                //Anti-pattern but in case this is jarringly fast...
                Thread.sleep(2000);

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                mMainHandler.post(() -> Toast.makeText(mContext.get(), "Failed to extract asset: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            mMainHandler.post(this::dismissDialog);
        }
    }

    public void execute(File outputFile){
        new Thread(new AssertExtractorRunnable(outputFile)).start();
    }

    public void setMakeExecutable(boolean executable){
        mMakeExecutable = executable;
    }

}
