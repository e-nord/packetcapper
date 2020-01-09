package com.norddev.packetcapper;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import eu.chainfire.libsuperuser.Shell;
import eu.chainfire.libsuperuser.StreamGobbler;

public class PacketCapper implements StreamGobbler.OnLineListener {

    private static final String TAG = "PacketCapper";
    private final Context mContext;
    private final Handler mMainHandler;
    private final Handler mShellHandler;
    private Shell.Interactive mShell;
    private EventListener mListener;
    private int mPid;
    private boolean mIsStopped;

    private static final int PID_UNKNOWN = -1;

    public interface EventListener {
        void onError(String msg);

        void onStart(CaptureSession captureSession);

        void onStop();
    }

    public static class CaptureOptions {
        private final File mOutputFile;
        private final String mInterface;

        public CaptureOptions(File outputFile, String iface) {
            mOutputFile = outputFile;
            mInterface = iface;
        }

        public File getOutputFile() {
            return mOutputFile;
        }

        public String getInterface() {
            return mInterface;
        }
    }

    public PacketCapper(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread handlerThread = new HandlerThread("PacketCapper");
        handlerThread.start();
        mShellHandler = new Handler(handlerThread.getLooper());
    }

    public static File getTCPDumpExecutable(Context context) {
        return new File(context.getFilesDir(), TCPDUMP_EXECUTABLE_NAME);
    }

    public static final String TCPDUMP_EXECUTABLE_NAME = "tcpdump";

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), "log.txt");
    }

    public void setListener(EventListener listener) {
        mListener = listener;
    }

    public void capture(final CaptureOptions options) {
        mShellHandler.post(() -> startCapture(options));
    }

    public void stop() {
        mShellHandler.post(this::stopCapture);
    }

    private void notifyStart(final EventListener listener, final CaptureSession captureSession) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onStart(captureSession));
        }
    }

    private void notifyError(final EventListener listener, final String msg) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onError(msg));
        }
    }

    private void notifyStop(final EventListener listener) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onStop());
        }
    }

    @Override
    public void onLine(String line) {
        Log.e(TAG, line);
    }

    private Shell.Interactive openRootShell(String shell) {
        return new Shell.Builder()
                .setShell(shell)
                .setOnSTDERRLineListener(this)
                .setOnSTDOUTLineListener(this)
                .open((success, reason) -> {
                    if (!success) {
                        notifyError(mListener, "Failed to open root shell: reason=" + reason);
                    }
                });
    }

    private static List<Integer> findPIDs(String executable) throws Shell.ShellDiedException {
        List<Integer> pids = new LinkedList<>();
        List<String> stdout = new LinkedList<>();
        List<String> stderr = new LinkedList<>();

        System.out.println("finding PID for executable: " + executable);

        Shell.Pool.SU.run(Compat.getPSCommand(), stdout, stderr, true);

        for (String line : stdout) {
            System.out.println(line);
            if (line.contains(executable)) {
                String[] parts = line.split("\\s+");
                pids.add(Integer.parseInt(parts[1]));
            }
        }
        return pids;
    }

    private static int findPID() {
        List<Integer> pids;
        try {
            pids = findPIDs(PacketCapper.TCPDUMP_EXECUTABLE_NAME);
            if (!pids.isEmpty()) {
                return pids.get(0);
            }
        } catch (Shell.ShellDiedException e) {
            e.printStackTrace();
        }
        return PID_UNKNOWN;
    }

    private void startCapture(final CaptureOptions options) {
        if (mShell == null) {
            mPid = PID_UNKNOWN;
            mIsStopped = false;
            mShell = openRootShell(Compat.getSystemRootShell());

            if (options.getOutputFile().exists() && !options.getOutputFile().delete()) {
                Log.e(TAG, "Failed to delete existing output file");
            }

            String args = String.format(Locale.US, "-i %s -s 0 -U -w %s", options.getInterface(), options.getOutputFile());
            String cmd = String.format(Locale.US, "%s %s", getTCPDumpExecutable(mContext).getAbsolutePath(), args);

            mShell.addCommand(cmd, 1, new Shell.OnCommandLineListener(){

                @Override
                public void onCommandResult(int commandCode, int exitCode) {
                    switch (exitCode){
                        case 0: //Success
                        case -2: //Killed?
                            break;
                        default:
                            notifyError(mListener, "Failed to start packet capture: exitcode=" + exitCode);
                            break;
                    }
                }

                @Override
                public void onSTDOUT(@NonNull String line) {

                }

                @Override
                public void onSTDERR(@NonNull String line) {
                    if(line.contains("tcpdump: listening")){
                        mPid = findPID();

                        Log.d(TAG, "Found PID = " + mPid);

                        if (mPid == PID_UNKNOWN) {
                            notifyError(mListener, "Failed to find spawned process PID");
                        } else {
                            notifyStart(mListener, new CaptureSession(options));
                        }
                    }
                }
            });
        }
    }

    private void stopCapture() {
        Log.d(TAG, "Stopping capture");
        if (mShell != null) {
            if (mPid != PID_UNKNOWN) {
                try {
                    int exitCode = kill(mPid);
                    Log.d(TAG, "Kill exit code: " + exitCode);
                } catch (Shell.ShellDiedException e) {
                    e.printStackTrace();
                }
            }
            mIsStopped = true;
            mShell.kill();
            mShell = null;
            SystemClock.sleep(1000);
        }
        notifyStop(mListener);
    }

    public static int kill(int pid) throws Shell.ShellDiedException {
        return Shell.Pool.SU.run(Compat.getKillCommand(pid), new Shell.OnSyncCommandLineListener() {
            @Override
            public void onSTDERR(@NonNull String line) {
                Log.d(TAG, line);
            }

            @Override
            public void onSTDOUT(@NonNull String line) {
                Log.d(TAG, line);
            }
        });
    }

    public static void killAll() {
        List<Integer> pids;
        try {
            pids = findPIDs(PacketCapper.TCPDUMP_EXECUTABLE_NAME);
            for (int pid : pids) {
                kill(pid);
            }
        } catch (Shell.ShellDiedException e) {
            e.printStackTrace();
        }
    }

    public static class CaptureSession {
        private final CaptureOptions mOptions;

        private CaptureSession(CaptureOptions captureOptions) {
            mOptions = captureOptions;
        }

        public String getInterfaceName() {
            return mOptions.getInterface();
        }

        public File getOutputFile() {
            return mOptions.getOutputFile();
        }

        public long getCaptureSize() {
            return mOptions.getOutputFile().length();
        }
    }
}
