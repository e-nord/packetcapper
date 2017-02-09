package com.norddev.packetcapper;

import android.content.Context;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetExtractor {

    private final Context mContext;

    public AssetExtractor(Context context) {
        mContext = context;
    }

    public void extract(String filePath, File file) throws IOException {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Failed to create parent directory: " + file.getParent());
        }
        FileOutputStream out = new FileOutputStream(file);
        InputStream in = mContext.getAssets().open(filePath);
        IOUtils.copy(in, out);
        out.flush();
        IOUtils.closeQuietly(out);
        IOUtils.closeQuietly(in);
    }
}
