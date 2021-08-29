package com.norddev.packetcapper.fragments;

import android.Manifest;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.dd.PacketCaptureButton;
import com.google.android.material.snackbar.Snackbar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.norddev.packetcapper.PacketCapper;
import com.norddev.packetcapper.PacketCapperApplication;
import com.norddev.packetcapper.R;
import com.norddev.packetcapper.TrafficMeter;
import com.norddev.packetcapper.Util;
import com.norddev.packetcapper.activities.PacketCapperActivity;
import com.norddev.packetcapper.helpers.AssetHelper;
import com.norddev.packetcapper.interfaces.ICaptureOptions;
import com.norddev.packetcapper.interfaces.ICaptureSession;
import com.norddev.packetcapper.interfaces.IPacketCapper;
import com.norddev.packetcapper.models.CaptureError;
import com.norddev.packetcapper.models.CaptureOptions;
import com.norddev.packetcapper.models.TCPDump;
import com.norddev.packetcapper.services.PacketCapperService;
import com.norddev.packetcapper.view.PixelsView;
import com.topjohnwu.superuser.Shell;

import java.util.Locale;
import java.util.Map;


public class PacketCapperFragment extends Fragment implements PermissionListener {

    private static final String TAG = "PacketCapperFragment";

    private PacketCaptureButton mCaptureButton;
    private Chronometer mElapsedTimer;
    private TextView mCaptureInfo;
    private PixelsView mPixelsView;
    private TextView mInterfaceInfo;
    private Snackbar mSnackbar;

    private IPacketCapper mPacketCapper;
    private ICaptureSession mCaptureSession;

    private TCPDump mTCPDump;

    private PacketCapperActivity mActivity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_packet_capper, container, false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (PacketCapperActivity) getActivity();
        mTCPDump = ((PacketCapperApplication)mActivity.getApplication()).getTCPDump();

        checkForSU(getContext());
        extractTCPDump();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mCaptureButton = view.findViewById(R.id.capture_button);
        mElapsedTimer = view.findViewById(R.id.time_elapsed);
        mCaptureInfo = view.findViewById(R.id.capture_file_info);
        mPixelsView = view.findViewById(R.id.pixels_view);
        mInterfaceInfo = view.findViewById(R.id.interface_info);

        init();

        super.onViewCreated(view, savedInstanceState);
    }

    private void init() {
        mInterfaceInfo.setVisibility(View.INVISIBLE);
        mElapsedTimer.setVisibility(View.INVISIBLE);
        mCaptureInfo.setVisibility(View.INVISIBLE);

        mCaptureButton.setIndeterminateProgressMode(true);
        mCaptureButton.setOnClickListener(v -> Dexter.withActivity(getActivity())
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(PacketCapperFragment.this)
                .check());

        mElapsedTimer.setOnChronometerTickListener(chronometer -> {
            if (mCaptureSession != null) {
                mCaptureInfo.setText(String.format(Locale.getDefault(),
                        "%s captured on %s", Formatter.formatShortFileSize(getContext(),
                                mCaptureSession.getCaptureSize()), mCaptureSession.getOptions().getInterface()));
            } else {
                mCaptureInfo.setText("");
            }
        });

        final Map<Integer, Float> rateToFrequency = Util.generateFrequencyRange(0, 5000, 10, 0.0f, 0.50f);
        final TrafficMeter trafficMeter = new TrafficMeter(kbps -> {
            int rate = Util.getNearestKey(rateToFrequency, kbps);
            Float frequency = rateToFrequency.get(rate);
            if (frequency != null) {
                mPixelsView.setFrequency(frequency);
                mInterfaceInfo.setText(TrafficMeter.formatNetworkRate(kbps));
            }
        });

        IPacketCapper.Listener listener = new IPacketCapper.Listener() {

            @Override
            public void onError(CaptureError error) {
                Log.i(TAG, "Capture error");

                //mFirebaseAnalytics.logEvent("capture_error", null);

                if (error != null) {
                    mSnackbar = Snackbar.make(mPixelsView, error.getMessage(), Snackbar.LENGTH_INDEFINITE);
                    mSnackbar.show();
                }
                mCaptureButton.setProgress(PacketCaptureButton.ERROR_STATE_PROGRESS);
                mElapsedTimer.stop();
                trafficMeter.stop();
                mCaptureSession = null;
                PacketCapperService.stop(mActivity);
                mPixelsView.stopAnimation();
            }

            @Override
            public void onStart(ICaptureSession captureSession) {
                Log.i(TAG, "Capture started");

                //mFirebaseAnalytics.logEvent("capture_started", null);

                mCaptureSession = captureSession;
                mCaptureButton.setProgress(PacketCaptureButton.SUCCESS_STATE_PROGRESS);
                mElapsedTimer.setBase(SystemClock.elapsedRealtime());
                mElapsedTimer.start();
                trafficMeter.start();
                mElapsedTimer.setVisibility(View.VISIBLE);
                mInterfaceInfo.setVisibility(View.VISIBLE);
                mCaptureInfo.setVisibility(View.VISIBLE);
                PacketCapperService.start(mActivity);
                mPixelsView.startAnimation();
            }

            @Override
            public void onStop() {
                Log.i(TAG, "Capture stopped");
                mCaptureButton.setProgress(PacketCaptureButton.IDLE_STATE_PROGRESS);
                mElapsedTimer.stop();
                trafficMeter.stop();
                mInterfaceInfo.setVisibility(View.INVISIBLE);
                mCaptureSession = null;
                PacketCapperService.stop(mActivity);
                mPixelsView.stopAnimation();
            }
        };

        mPacketCapper = new PacketCapper(mTCPDump, listener);
    }

    @Override
    public void onPermissionGranted(PermissionGrantedResponse response) {
        onCaptureButtonClicked(mCaptureButton.getProgress());
    }

    @Override
    public void onPermissionDenied(PermissionDeniedResponse response) {
        mCaptureButton.setProgress(PacketCaptureButton.ERROR_STATE_PROGRESS);
    }

    @Override
    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

    }

    private void startCapture() {
        Log.i(TAG, "Starting capture");
        //start the timer now instead of waiting for the capture to boot up
        mElapsedTimer.setBase(SystemClock.elapsedRealtime());
        ICaptureOptions options = CaptureOptions.Default.create(getContext());
        mPacketCapper.startCapture(options);
    }

    private void stopCapture() {
        Log.i(TAG, "Stopping capture");
        mPacketCapper.stopCapture();
    }

    private void onCaptureButtonClicked(int progress) {
        if (progress == PacketCaptureButton.ERROR_STATE_PROGRESS) { //hard reset from error
            mCaptureButton.setProgress(PacketCaptureButton.IDLE_STATE_PROGRESS);
            dismissSnackbar();
            stopCapture();
        } else if (progress == PacketCaptureButton.IDLE_STATE_PROGRESS) { //start gracefully
            mCaptureButton.setProgress(PacketCaptureButton.INDETERMINATE_STATE_PROGRESS);
            startCapture();
        } else if (progress == PacketCaptureButton.SUCCESS_STATE_PROGRESS) { //stop gracefully
            mCaptureButton.setProgress(PacketCaptureButton.INDETERMINATE_STATE_PROGRESS);
            stopCapture();
        }
    }

    private void dismissSnackbar() {
        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }
    }

    @Override
    public void onDestroyView() {
        mPixelsView.destroy();
        super.onDestroyView();
    }

    private void extractTCPDump() {
        if (!mTCPDump.getExecutable().exists()) {
            AssetHelper task = new AssetHelper(getContext(), mTCPDump.getExecutable().getName());
            task.setMakeExecutable(true);
            task.execute(mTCPDump.getExecutable());
        }
    }

    private void checkForSU(Context context) {
        boolean suAvailable = Shell.rootAccess();
        if (!suAvailable) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.notice)
                    .setMessage(R.string.su_not_available)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

}