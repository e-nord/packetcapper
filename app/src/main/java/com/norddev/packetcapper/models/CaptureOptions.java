package com.norddev.packetcapper.models;

import android.content.Context;
import android.os.Environment;

import com.norddev.packetcapper.Compat;
import com.norddev.packetcapper.helpers.CaptureInterfacesHelper;
import com.norddev.packetcapper.interfaces.ICaptureOptions;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CaptureOptions implements ICaptureOptions {

    private final File mOutputFile;
    private final String mInterfaceName;

    public CaptureOptions(File outputFile, String interfaceName) {
        mOutputFile = outputFile;
        mInterfaceName = interfaceName;
    }

    @Override
    public File getOutputFile() {
        return mOutputFile;
    }

    @Override
    public String getInterface() {
        return mInterfaceName;
    }

    public static class Default {

        private static final String PREF_KEY_OUTPUT_DIRECTORY = "output_dir";
        private static final String PREF_KEY_CAPTURE_INTERFACE = "capture_interface";
        private static final String PREF_KEY_CAPTURE_FILE_NAME_FORMAT = "capture_name_fmt";

        public static CaptureOptions create(Context contxt){
            return new CaptureOptions(getDefaultCaptureFile(), getDefaultInterfaceName(contxt));
        }

        public static String getDefaultCaptureFileName() {
            String nameFmt = Hawk.get(PREF_KEY_CAPTURE_FILE_NAME_FORMAT);
            Date now = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
            return nameFmt.replaceAll("%T", String.valueOf(now.getTime()))
                    .replaceAll("%D", dateFormat.format(now));
        }

        public static String getDefaultInterfaceName(Context context){
            return Hawk.get(PREF_KEY_CAPTURE_INTERFACE, CaptureInterfacesHelper.getDefaultInterface(context));
        }

        public static File getDefaultCaptureFile(){
            String outputDirPath = Hawk.get(PREF_KEY_OUTPUT_DIRECTORY);
            return new File(Compat.correctExtPath(outputDirPath), getDefaultCaptureFileName());
        }

        public static String getDefaultCaptureDirectoryPath(){
            return Hawk.get(PREF_KEY_OUTPUT_DIRECTORY);
        }

        public static void setDefaultCaptureDirectoryPath(String path){
            //FIXME hack for Annites
            if (path.contains("/storage/emulated/0")) {
                path = path.replace("/storage/emulated/0", "/storage/emulated/legacy");
            }
            Hawk.put(PREF_KEY_OUTPUT_DIRECTORY, path);
        }

        public static void setDefaultInterfaceName(String name){
            Hawk.put(PREF_KEY_CAPTURE_INTERFACE, name);
        }

        public static void initDefaults() {
            if(!Hawk.contains(PREF_KEY_OUTPUT_DIRECTORY)) {
                Hawk.put(PREF_KEY_OUTPUT_DIRECTORY, getDefaultOutputDirectoryPath());
            }

            if(!Hawk.contains(PREF_KEY_CAPTURE_FILE_NAME_FORMAT)){
                Hawk.put(PREF_KEY_CAPTURE_FILE_NAME_FORMAT, getDefaultCatpureFileNameFormat());
            }
        }

        private static String getDefaultOutputDirectoryPath(){
            File extStorage = Environment.getExternalStorageDirectory();
            File legacyStorage = new File(extStorage.getParentFile(), "legacy");
            if(legacyStorage.exists()){
                extStorage = legacyStorage;
            }
            return extStorage.getAbsolutePath();
        }

        private static String getDefaultCatpureFileNameFormat(){
            return "capture_%D.pcap";
        }

    }


}
