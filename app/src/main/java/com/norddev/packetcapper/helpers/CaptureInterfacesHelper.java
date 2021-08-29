package com.norddev.packetcapper.helpers;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.topjohnwu.superuser.Shell;

import java.util.LinkedList;
import java.util.List;

public class CaptureInterfacesHelper {

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
        List<String> output = Shell.sh(NETCFG_COMMAND).exec().getOut();
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
        List<String> output = Shell.sh(IFCONFIG_COMMAND).exec().getOut();
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
