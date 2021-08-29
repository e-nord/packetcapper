package com.norddev.packetcapper.helpers;

import android.util.Log;

import com.norddev.packetcapper.Compat;
import com.topjohnwu.superuser.Shell;

import java.util.LinkedList;
import java.util.List;

public class ProcessHelper {

    private static final String TAG = "ProcessHelper";

    private static List<Integer> findPIDs(String executable) {
        List<Integer> pids = new LinkedList<>();
        List<String> stdout;

        stdout = Shell.su(Compat.getPSCommand()).exec().getOut();

        for (String line : stdout) {
            if (line.contains(executable)) {
                String[] parts = line.split("\\s+");
                pids.add(Integer.parseInt(parts[1]));
            }
        }
        return pids;
    }

    public static Integer findPID(String executableName) {
        List<Integer> pids;
        pids = findPIDs(executableName);
        if (!pids.isEmpty()) {
            return pids.get(0);
        }
        return null;
    }

    public static void killAll(String executableName) {
        List<Integer> pids;
        pids = ProcessHelper.findPIDs(executableName);
        for (int pid : pids) {
            kill(pid);
        }
    }

    public static int kill(int pid) {
        Log.d(TAG, String.format("Killing pid=%d", pid));
        Shell.Result result = Shell.su(Compat.getKillCommand(pid)).exec();
        Log.d(TAG, result.getOut().toString());
        return result.getCode();
    }

}
