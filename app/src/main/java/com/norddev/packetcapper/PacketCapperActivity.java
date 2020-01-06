package com.norddev.packetcapper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;

import com.codekidlabs.storagechooser.StorageChooser;
import com.dd.PacketCaptureButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import de.psdev.licensesdialog.LicensesDialog;
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20;
import de.psdev.licensesdialog.licenses.GnuLesserGeneralPublicLicense21;
import de.psdev.licensesdialog.model.Notice;
import de.psdev.licensesdialog.model.Notices;
import eu.chainfire.libsuperuser.Shell;
import fr.nicolaspomepuy.discreetapprate.AppRate;

public class PacketCapperActivity extends AppCompatActivity implements PermissionListener {

    private static final String TAG = "PacketCapperActivity";

    public static final String PREF_KEY_OUTPUT_DIRECTORY = "output_dir";
    public static final String PREF_KEY_CAPTURE_INTERFACE = "capture_interface";
    public static final String PREF_KEY_CAPTURE_FILE_NAME_FORMAT = "capture_name_fmt";

    private PacketCaptureButton mCaptureButton;
    private Chronometer mElapsedTimer;
    private TextView mCaptureInfo;
    private PixelsView mPixelsView;
    private TextView mInterfaceInfo;

    private PacketCapper mPacketCapper;
    private PacketCapper.CaptureSession mCaptureSession;
    private FirebaseAnalytics mFirebaseAnalytics;
    private Snackbar mSnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packet_capper);

        mCaptureButton = findViewById(R.id.capture_button);
        mElapsedTimer = findViewById(R.id.time_elapsed);
        mCaptureInfo = findViewById(R.id.capture_file_info);
        mPixelsView = findViewById(R.id.pixels_view);
        mInterfaceInfo = findViewById(R.id.interface_info);

        checkForSU();
        extractTCPDump();
        init();
        AppRate.with(this)
                .text(getString(R.string.rate_it))
                .fromTop(true)
                .checkAndShow();

        Dexter.withActivity(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(this)
                .check();
    }

    @Override
    public void onPermissionGranted(PermissionGrantedResponse response) {
        mCaptureButton.setEnabled(true);
    }

    @Override
    public void onPermissionDenied(PermissionDeniedResponse response) {
        mCaptureButton.setEnabled(false);
        mCaptureButton.setProgress(PacketCaptureButton.ERROR_STATE_PROGRESS);
    }

    @Override
    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    private void showAbout() {
        final Notices notices = new Notices();
        notices.addNotice(new Notice("Test 1", "http://example.org", "Example Person", new ApacheSoftwareLicense20()));
        notices.addNotice(new Notice("Test 2", "http://example.org", "Example Person 2", new GnuLesserGeneralPublicLicense21()));
        new LicensesDialog.Builder(this)
                .setTitle("Licenses")
                .setNotices(notices)
                .build().show();
    }

    private void showAppRating() {
        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("market://details?id=" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_set_output_directory) {
            showDirectoryChooser();
            return true;
        } else if (item.getItemId() == R.id.menu_set_capture_args) {
            showCaptureArgsEditor();
            return true;
        } else if (item.getItemId() == R.id.menu_rate_app) {
            showAppRating();
            return true;
        } else if (item.getItemId() == R.id.menu_about) {
            showAbout();
            return true;
        } else if (item.getItemId() == R.id.menu_set_capture_interface) {
            showCaptureInterfaceChooser();
            return true;
        } else if(item.getItemId() == R.id.menu_browse_captures){
            showCaptureBrowser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCaptureBrowser() {

    }

    private void showCaptureInterfaceChooser() {
        final List<String> interfaces = CaptureInterfaces.getInterfaces();
        String currentIface = Hawk.get(PREF_KEY_CAPTURE_INTERFACE, CaptureInterfaces.getDefaultInterface(this));
        int defaultIndex = interfaces.indexOf(currentIface);

        CharSequence[] items = new String[interfaces.size()];
        interfaces.toArray(items);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.set_capture_interface);
        builder.setSingleChoiceItems(items, defaultIndex, (dialogInterface, i) -> {
            Hawk.put(PREF_KEY_CAPTURE_INTERFACE, interfaces.get(i));
            dialogInterface.dismiss();
        });

        builder.create().show();
    }

    private void showCaptureArgsEditor() {

    }

    private void dismissSnackbar() {
        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }
    }

    private void showDirectoryChooser() {
        StorageChooser chooser = new StorageChooser.Builder()
                .withActivity(this)
                .withFragmentManager(getFragmentManager())
                .withMemoryBar(true)
                .allowCustomPath(true)
                .allowAddFolder(true)
                .setType(StorageChooser.DIRECTORY_CHOOSER)
                .withPredefinedPath(Hawk.get(PREF_KEY_OUTPUT_DIRECTORY))
                .build();
        chooser.show();
        chooser.setOnSelectListener(path -> {
            //FIXME hack for Annites
            if(path.contains("/storage/emulated/0")){
                path = path.replace("/storage/emulated/0", "/storage/emulated/legacy");
            }
            Hawk.put(PREF_KEY_OUTPUT_DIRECTORY, path);
        });
    }

    private void cleanup() {
        mPixelsView.destroy();
    }

    public static Intent getIntent(Context context) {
        return new Intent(context, PacketCapperActivity.class);
    }

    private String getCaptureFileName() {
        String nameFmt = Hawk.get(PREF_KEY_CAPTURE_FILE_NAME_FORMAT);
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
        return nameFmt.replaceAll("%T", String.valueOf(now.getTime()))
                .replaceAll("%D", dateFormat.format(now));
    }

    public void startCapture() {
        Log.i(TAG, "Starting capture");
        //start the timer now instead of waiting for the capture to boot up
        mElapsedTimer.setBase(SystemClock.elapsedRealtime());
        String ifaceName = Hawk.get(PREF_KEY_CAPTURE_INTERFACE, CaptureInterfaces.getDefaultInterface(this));
        String outputDirPath = Hawk.get(PREF_KEY_OUTPUT_DIRECTORY);
        File outputFile = new File(correctExtPath(outputDirPath), getCaptureFileName());
        PacketCapper.CaptureOptions options = new PacketCapper.CaptureOptions(outputFile, ifaceName);
        mPacketCapper.capture(options);
    }

    private String correctExtPath(String outputDirPath){
        return outputDirPath.replace("legacy", "0");
    }

    private void stopCapture() {
        Log.i(TAG, "Stopping capture");
        mPacketCapper.stop();
    }

    private void onCaptureButtonClick() {
        int progress = mCaptureButton.getProgress();
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

    private void init() {
        mInterfaceInfo.setVisibility(View.INVISIBLE);
        mElapsedTimer.setVisibility(View.INVISIBLE);
        mCaptureInfo.setVisibility(View.INVISIBLE);

        mCaptureButton.setIndeterminateProgressMode(true);
        mCaptureButton.setOnClickListener(v -> onCaptureButtonClick());

        mElapsedTimer.setOnChronometerTickListener(chronometer -> {
            if (mCaptureSession != null) {
                mCaptureInfo.setText(String.format(Locale.getDefault(),
                        "%s captured on %s", Formatter.formatShortFileSize(PacketCapperActivity.this,
                                mCaptureSession.getCaptureSize()), mCaptureSession.getInterfaceName()));
            } else {
                mCaptureInfo.setText("");
            }
        });

        final Map<Integer, Float> rateToFrequency = Util.generateFrequencyRange(0, 5000, 10, 0.0f, 0.50f);
        final TrafficMeter trafficMeter = new TrafficMeter(kbps -> {
            int rate = Util.getNearestKey(rateToFrequency, kbps);
            Float frequency = rateToFrequency.get(rate);
            if(frequency != null) {
                mPixelsView.setFrequency(frequency);
                mInterfaceInfo.setText(TrafficMeter.formatNetworkRate(kbps));
            }
        });

        mPacketCapper = new PacketCapper(this);
        mPacketCapper.setListener(new PacketCapper.EventListener() {
            @Override
            public void onError(String msg) {
                Log.i(TAG, "Capture error");
                mFirebaseAnalytics.logEvent("capture_error", null);
                if (msg != null) {
                    mSnackbar = Snackbar.make(mPixelsView, msg, Snackbar.LENGTH_INDEFINITE);
                    mSnackbar.show();
                }
                mCaptureButton.setProgress(PacketCaptureButton.ERROR_STATE_PROGRESS);
                mElapsedTimer.stop();
                trafficMeter.stop();
                mCaptureSession = null;
                PacketCapperService.stop(getApplicationContext());
                mPixelsView.stopAnimation();
            }

            @Override
            public void onStart(PacketCapper.CaptureSession captureSession) {
                Log.i(TAG, "Capture started");
                mFirebaseAnalytics.logEvent("capture_started", null);
                mCaptureSession = captureSession;
                mCaptureButton.setProgress(PacketCaptureButton.SUCCESS_STATE_PROGRESS);
                mElapsedTimer.setBase(SystemClock.elapsedRealtime());
                mElapsedTimer.start();
                trafficMeter.start();
                mElapsedTimer.setVisibility(View.VISIBLE);
                mInterfaceInfo.setVisibility(View.VISIBLE);
                mCaptureInfo.setVisibility(View.VISIBLE);
                PacketCapperService.start(getApplicationContext());
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
                PacketCapperService.stop(getApplicationContext());
                mPixelsView.stopAnimation();
            }
        });

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    private void extractTCPDump() {
        File tcpdump = PacketCapper.getTCPDumpExecutable(this);
        if (!tcpdump.exists()) {
            AssetExtractorTask task = new AssetExtractorTask(this, "tcpdump");
            task.setMakeExecutable(true);
            task.execute(tcpdump);
        }
    }

    private void checkForSU() {
        boolean suAvailable = Shell.SU.available();
        if (!suAvailable) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.notice)
                    .setMessage(R.string.su_not_available)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

}
