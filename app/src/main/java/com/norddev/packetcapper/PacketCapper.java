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

import eu.chainfire.libsuperuser.Shell;

public class PacketCapper {

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

        void onStart(CaptureFile captureFile);

        void onStop();
    }

    public static class CaptureOptions {
        private final File mOutputFile;

        public CaptureOptions(File outputFile) {
            mOutputFile = outputFile;
        }

        public File getOutputFile() {
            return mOutputFile;
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
        return new File(context.getFilesDir(), "tcpdump");
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), "log.txt");
    }

    public void setListener(EventListener listener) {
        mListener = listener;
    }

    public void capture(final CaptureOptions options) {
        mShellHandler.post(new Runnable() {
            @Override
            public void run() {
                startCapture(options);
            }
        });
    }

    public void stop() {
        mShellHandler.post(new Runnable() {
            @Override
            public void run() {
                stopCapture();
            }
        });
    }

    private void notifyStart(final EventListener listener, final CaptureFile captureFile) {
        if (listener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onStart(captureFile);
                }
            });
        }
    }

    private void notifyError(final EventListener listener, final String msg) {
        if (listener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onError(msg);
                }
            });
        }
    }

    private void notifyStop(final EventListener listener) {
        if (listener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onStop();
                }
            });
        }
    }

    private String getOutput(List<String> output){
        String result = null;
        if(output != null) {
            StringBuilder builder = new StringBuilder();
            for (String line : output) {
                builder.append(line);
            }
            result = builder.toString().trim();
            if(result.isEmpty()){
                result = null;
            }
        }
        return result;
    }

    private Shell.Interactive openRootShell() {
        return new Shell.Builder().
                useSU().
                setWantSTDERR(true).
                open(new Shell.OnCommandResultListener() {
                    // Callback to report whether the shell was successfully started up
                    @Override
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                        if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                            String msg = getOutput(output);
                            if(msg != null){
                                msg = "Shell: " + msg;
                                Log.e(TAG, msg);
                            }
                            if(!mIsStopped) {
                                notifyError(mListener, msg);
                            }
                        }
                    }
                });
    }

    private static List<Integer> findPIDs(String executable) {
        List<Integer> pids = new LinkedList<>();
        List<String> output = Shell.SU.run("ps");
        if(output != null) {
            for (String line : output) {
                if (line.contains(executable)) {
                    String[] parts = line.split("\\s+");
                    pids.add(Integer.parseInt(parts[1]));
                }
            }
        }
        return pids;
    }

    private static int findPID(String executable){
        List<Integer> pids = findPIDs(executable);
        if(pids.isEmpty()){
            return PID_UNKNOWN;
        } else {
            return pids.get(0);
        }
    }

    private void startCapture(final CaptureOptions options) {
        if (mShell == null) {
            mPid = PID_UNKNOWN;
            mIsStopped = false;
            mShell = openRootShell();
            if(options.getOutputFile().exists() && !options.getOutputFile().delete()){
                Log.e(TAG, "Failed to delete existing output file");
            }
            String args = String.format(Locale.US, "-s 0 -U -w %s", options.getOutputFile());
            String cmd = String.format(Locale.US, "%s %s", getTCPDumpExecutable(mContext).getAbsolutePath(), args);
            mShell.addCommand(cmd, 1, new Shell.OnCommandResultListener() {
                @Override
                public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                    if (exitCode != 0) {
                        String msg = getOutput(output);
                        if(msg != null){
                            msg = "Capture: " + msg;
                            Log.e(TAG, msg);
                        }
                        if(!mIsStopped) {
                            notifyError(mListener, msg);
                        }
                    }
                }
            });
            SystemClock.sleep(1000);
            mPid = findPID(getTCPDumpExecutable(mContext).getAbsolutePath());
            if (mPid == PID_UNKNOWN) {
                notifyError(mListener, "Failed to find spawned process PID");
            } else {
                Log.d(TAG, "PID = " + mPid);
                notifyStart(mListener, new CaptureFile(options.getOutputFile()));
            }
        }
    }

    private void stopCapture() {
        if (mShell != null) {
            if (mPid != PID_UNKNOWN) {
                kill(mPid);
            }
            mIsStopped = true;
            mShell.close();
            mShell = null;
            SystemClock.sleep(1000);
        }
        notifyStop(mListener);
    }

    public static void kill(int pid){
        Shell.SU.run(String.format(Locale.getDefault(), "kill INT %d", pid));
    }

    public static void killAll(Context context){
        List<Integer> pids = findPIDs(getTCPDumpExecutable(context).getAbsolutePath());
        for(int pid : pids){
            kill(pid);
        }
    }

    public static class CaptureFile {
        private final File mOutputFile;

        private CaptureFile(File outputFile) {
            mOutputFile = outputFile;
        }

        public long getCaptureSize(){
            return mOutputFile.length();
        }
    }
}
