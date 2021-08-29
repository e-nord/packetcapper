package com.norddev.packetcapper.helpers;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.norddev.packetcapper.R;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetHelper extends AsyncTask<File,Void,Exception> {

    private final Context mContext;
    private final String mAssetPath;
    private AlertDialog mDialog;
    private boolean mMakeExecutable;

    public AssetHelper(Context context, String assetPath) {
        mContext = context;
        mAssetPath = assetPath;
    }

    public static void extract(Context context, String filePath, File file) throws IOException {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Failed to create parent directory: " + file.getParent());
        }
        FileOutputStream out = new FileOutputStream(file);
        InputStream in = context.getAssets().open(filePath);
        IOUtils.copy(in, out);
        out.flush();
        IOUtils.closeQuietly(out);
        IOUtils.closeQuietly(in);
    }

    public void setMakeExecutable(boolean executable){
        mMakeExecutable = executable;
    }

    @Override
    protected Exception doInBackground(File... params) {
        try {
            AssetHelper.extract(mContext, mAssetPath, params[0]);
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
