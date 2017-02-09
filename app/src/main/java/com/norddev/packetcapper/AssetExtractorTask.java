package com.norddev.packetcapper;

import android.content.Context;
import android.os.AsyncTask;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.IOException;

public class AssetExtractorTask extends AsyncTask<File,Void,Exception> {

    private final Context mContext;
    private final String mAssetPath;
    private MaterialDialog mDialog;
    private boolean mMakeExecutable;

    public AssetExtractorTask(Context context, String assetPath) {
        mContext = context;
        mAssetPath = assetPath;
    }

    public void setMakeExecutable(boolean executable){
        mMakeExecutable = executable;
    }

    @Override
    protected Exception doInBackground(File... params) {
        try {
            AssetExtractor extractor = new AssetExtractor(mContext);
            extractor.extract(mAssetPath, params[0]);
            if(mMakeExecutable) {
                ProcessBuilder builder = new ProcessBuilder("chmod", "+x", params[0].getAbsolutePath());
                Process process = builder.start();
                process.waitFor();
                if (process.exitValue() != 0) {
                    throw new IOException("Failed to make extracted asset executable");
                }
            }
            Thread.sleep(2000);
        } catch (IOException | InterruptedException e) {
            return e;
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        mDialog = new MaterialDialog.Builder(mContext)
                .title(R.string.setting_up)
                .content(R.string.please_wait)
                .progress(true, 0)
                .show();
    }

    @Override
    protected void onPostExecute(Exception e) {
        mDialog.dismiss();
    }
}
