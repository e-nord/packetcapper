package com.norddev.packetcapper;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import androidx.appcompat.app.AlertDialog;

public class AssetExtractorTask extends AsyncTask<File,Void,Exception> {

    private final Context mContext;
    private final String mAssetPath;
    private AlertDialog mDialog;
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
                ProcessBuilder builder = new ProcessBuilder("chmod", "755", params[0].getAbsolutePath());
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
        mDialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.setting_up)
                .setMessage(R.string.please_wait)
                .show();
    }

    @Override
    protected void onPostExecute(Exception e) {
        if(e != null){
            e.printStackTrace();
            Toast.makeText(mContext, "Failed to extract tcpdump executable: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        mDialog.dismiss();
    }
}
