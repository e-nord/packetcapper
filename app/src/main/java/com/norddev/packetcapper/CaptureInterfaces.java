package com.norddev.packetcapper;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.util.LinkedList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class CaptureInterfaces {

    private static final String IFCONFIG_COMMAND = "ifconfig";
    private static final String NETCFG_COMMAND = "netcfg";

    public static List<String> getInterfaces(){
        List<String> interfaces = getIfConfigInterfaces();
        if(interfaces.isEmpty()){
            interfaces = getNetcfgInterfaces();
        }
        return interfaces;
    }

    private static List<String> getNetcfgInterfaces(){
        List<String> interfaces = new LinkedList<>();
        List<String> output = Shell.SH.run(NETCFG_COMMAND);
        for(String line : output){
            if(line.contains("UP")){
                String[] parts = line.split("\\s");
                interfaces.add(parts[0]);
            }
        }
        return interfaces;
    }

    private static List<String> getIfConfigInterfaces(){
        List<String> interfaces = new LinkedList<>();
        List<String> output = Shell.SH.run(IFCONFIG_COMMAND);
        for(String line : output){
            if(line.contains("Link encap")){
                String[] parts = line.split("\\s");
                interfaces.add(parts[0]);
            }
        }
        return interfaces;
    }

    private static boolean isWiFiConnected(Context context){
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager.getConnectionInfo() != null;
    }

    public static String getDefaultInterface(Context context){
        String defaultIface = null;
        List<String> interfaces = getInterfaces();
        boolean isWiFiConnected = isWiFiConnected(context);
        for(String iface : interfaces){
            if(iface.equals("lo") || iface.equals("p2p0") || iface.startsWith("dummy")){
                continue;
            }
            if(iface.startsWith("wlan")){
                if(isWiFiConnected){
                    defaultIface = iface;
                    break;
                }
                continue;
            }
            defaultIface = iface;
        }
        return defaultIface;
    }
}
