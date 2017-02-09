package com.norddev.packetcapper;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
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

    private static final int PID_UNKNOWN = -1;

    public interface EventListener {
        void onError();

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

    private void notifyError(final EventListener listener) {
        if (listener != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onError();
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

    private Shell.Interactive openRootShell() {
        return new Shell.Builder().
                useSU().
                setWantSTDERR(true).
                open(new Shell.OnCommandResultListener() {
                    // Callback to report whether the shell was successfully started up
                    @Override
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                        if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                            notifyError(mListener);
                        }
                    }
                });
    }

    private int findPID(String executable) {
        List<String> output = Shell.SU.run("ps");
        for (String line : output) {
            if (line.contains(executable)) {
                String[] parts = line.split("\\s+");
                return Integer.parseInt(parts[1]);
            }
        }
        return PID_UNKNOWN;
    }

    private void startCapture(final CaptureOptions options) {
        if (mShell == null) {
            mPid = PID_UNKNOWN;
            mShell = openRootShell();
            if(options.getOutputFile().exists() && !options.getOutputFile().delete()){
                System.out.println("Failed to delete existing output file");
            }
            String cmd = String.format(Locale.getDefault(), "%s -s 0 -w %s", getTCPDumpExecutable(mContext).getAbsolutePath(), options.getOutputFile());
            mShell.addCommand(cmd, 1, new Shell.OnCommandLineListener() {
                @Override
                public void onCommandResult(int commandCode, int exitCode) {
                    if (exitCode < 0) {
                        notifyError(mListener);
                    } else {
                        notifyStop(mListener);
                    }
                }

                @Override
                public void onLine(String line) {
                    System.out.println(line);
                }
            });
            SystemClock.sleep(1000);
            mPid = findPID(getTCPDumpExecutable(mContext).getAbsolutePath());
            if (mPid == PID_UNKNOWN) {
                notifyError(mListener);
            } else {
                Log.d(TAG, "PID = " + mPid);
                notifyStart(mListener, new CaptureFile(options.getOutputFile()));
            }
        }
    }

    private void stopCapture() {
        if (mShell != null) {
            if (mPid != PID_UNKNOWN) {
                Shell.SU.run(String.format(Locale.getDefault(), "kill INT %d", mPid));
            }
            mShell.close();
            mShell = null;
        }
    }

    public static class CaptureFile {
        private final File mOutputFile;

        private CaptureFile(File outputFile) {
            mOutputFile = outputFile;
        }

        public long getCaptureSize(){
            long size = mOutputFile.length();
            return size;
        }
    }
}
