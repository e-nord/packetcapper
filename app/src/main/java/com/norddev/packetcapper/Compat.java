package com.norddev.packetcapper;

import android.os.Build;

import java.io.File;
import java.util.Locale;

public class Compat {

    private static boolean IS_SHITTY_SAMSUNG = Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1;

    public static String getSystemRootShell() {
        if(new File("/system/xbin/suhandy").exists()){
            return "suhandy";
        }
        return "su";
    }

    public static String correctExtPath(String outputDirPath){
        if(IS_SHITTY_SAMSUNG){
            return outputDirPath;
        }
        return outputDirPath.replace("legacy", "0");
    }

    public static String getPSCommand(){
        if(IS_SHITTY_SAMSUNG){
            return "ps";
        }
        return "ps -A";
    }

    public static String getKillCommand(int pid){
        if(IS_SHITTY_SAMSUNG){
            return String.format(Locale.getDefault(), "kill INT %d", pid);
        }
        return String.format(Locale.getDefault(), "kill %d", pid);
    }

}
