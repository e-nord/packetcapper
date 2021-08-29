package com.norddev.packetcapper;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.norddev.packetcapper.helpers.ProcessHelper;
import com.norddev.packetcapper.interfaces.ICaptureOptions;
import com.norddev.packetcapper.interfaces.IPacketCapper;
import com.norddev.packetcapper.models.CaptureError;
import com.norddev.packetcapper.models.CaptureSession;
import com.norddev.packetcapper.models.TCPDump;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class PacketCapper implements IPacketCapper {

    private static final String TAG = "PacketCapper";
    private final Handler mMainHandler;
    private final Handler mShellHandler;
    private Shell mShell;
    private IPacketCapper.Listener mListener;
    private Integer mPid;
    private final TCPDump mTcpdump;

    public PacketCapper(TCPDump tcpDump, IPacketCapper.Listener listener) {
        mTcpdump = tcpDump;
        mListener = listener;
        mMainHandler = new Handler(Looper.getMainLooper());
        HandlerThread handlerThread = new HandlerThread("PacketCapper");
        handlerThread.start();
        mShellHandler = new Handler(handlerThread.getLooper());
    }

    public void startCapture(final ICaptureOptions options) {
        mShellHandler.post(() -> start(options));
    }

    public void stopCapture() {
        mShellHandler.post(this::stop);
    }

    private void notifyStart(final Listener listener, final CaptureSession captureSession) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onStart(captureSession));
        }
    }

    private void notifyError(final Listener listener, final String msg) {
        if (listener != null) {
            mMainHandler.post(() -> listener.onError(new CaptureError(msg)));
        }
    }

    private void notifyStop(final Listener listener) {
        if (listener != null) {
            mMainHandler.post(listener::onStop);
        }
    }

    private Shell openRootShell(String shell) {
        return Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).build();
    }

    private void start(ICaptureOptions options) {
        if (mShell == null) {
            mPid = null;
            mShell = openRootShell(Compat.getSystemRootShell());

            if (options.getOutputFile().exists() && !options.getOutputFile().delete()) {
                Log.e(TAG, "Failed to delete existing output file");
            }

            String args = String.format(Locale.US, "-i %s -s 0 -U -w %s", options.getInterface(), options.getOutputFile());
            String cmd = String.format(Locale.US, "%s2 %s", mTcpdump.getExecutable().getAbsolutePath(), args);

            Log.d(TAG, String.format("Executing: %s", cmd));

            List<String> callbackList = new CallbackList<String>() {
                @Override
                public void onAddElement(String s) {
                    if (s.contains("tcpdump: listening")) {
                        mPid = ProcessHelper.findPID(mTcpdump.getExecutable().getName());
                        if (mPid == null) {
                            notifyError(mListener, "Failed to find spawned process PID");
                        } else {
                            Log.d(TAG, "Found tcpdump pid: " + mPid);
                            notifyStart(mListener, new CaptureSession(options));
                        }
                    }
                }
            };
            mShell.newJob().add(cmd).to(callbackList).submit(out -> {
                Log.d(TAG, String.format("Exited with code: %d", out.getCode()));
                Log.d(TAG, Arrays.toString(out.getOut().toArray()));
                Log.d(TAG, Arrays.toString(out.getErr().toArray()));
                if(out.getCode() > 0){
                    notifyError(mListener, "Process exited unexpectedly: code=" + out.getCode());
                }
            });
        }
    }

    private void stop() {
        Log.d(TAG, "Stopping capture");
        if (mShell != null) {
            if (mPid != null) {
                int exitCode = ProcessHelper.kill(mPid);
                Log.d(TAG, "Kill exit code: " + exitCode);
            }
            try {
                mShell.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mShell = null;
            SystemClock.sleep(1000);
        }
        notifyStop(mListener);
    }

}
