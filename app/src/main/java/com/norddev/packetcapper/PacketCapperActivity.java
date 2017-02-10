package com.norddev.packetcapper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.androidadvance.topsnackbar.TSnackbar;
import com.dd.PacketCaptureButton;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.marcoscg.easylicensesdialog.EasyLicensesDialogCompat;
import com.norddev.packetcapper.iap.IabHelper;
import com.norddev.packetcapper.iap.IabResult;
import com.norddev.packetcapper.iap.Inventory;
import com.norddev.packetcapper.iap.Purchase;
import com.orhanobut.hawk.Hawk;

import net.rdrei.android.dirchooser.DirectoryChooserConfig;
import net.rdrei.android.dirchooser.DirectoryChooserFragment;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.chainfire.libsuperuser.Shell;
import fr.nicolaspomepuy.discreetapprate.AppRate;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class PacketCapperActivity extends AppCompatActivity implements
        DirectoryChooserFragment.OnFragmentInteractionListener {

    private static final String TAG = "PacketCapperActivity";
    public static final String PREF_KEY_OUTPUT_DIRECTORY = "output_dir";
    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgKPmaK3l3/zjg8Gi76joeQmwY" +
            "RioqAxQ0kGG9jHuQjWTo94aCESyVR/bRWpgtDOzKNEWRoCOAtZJGt2odunf6SpWP91tcQ1l8n8LLAoYNDgEa8qXEOO9w9CGwcqEOMj" +
            "7eyC6xZAzjSKY2JfTHuKsWc3ChyV6IBXgyj40cxbFu9QKFQrzatCidJF6wnTQQUSAwQjMRPbxQ2F89fTQRGL3iZbEOpxxGp+gqj50+" +
            "w2QOJQOQl/uit5MUy8wLCxrWkW/VHEgRQEXtTxnnTi5NCaKq15M4WvJQlmGJwzL73sd1hkl8GULveeivF7zHw3v7HzCJ/b5Bs1XqJV" +
            "XaCBnMpYRAwIDAQAB"; //FIXME encode
    private static final String SKU_AD_FREE = "feature.ad_free";

    @BindView(R.id.capture_button)
    PacketCaptureButton mCaptureButton;
    @BindView(R.id.time_elapsed)
    Chronometer mElapsedTimer;
    @BindView(R.id.capture_size)
    TextView mCapturedBytesCount;
    @BindView(R.id.pixels_view)
    PixelsView mPixelsView;
    @BindView(R.id.network_rate)
    TextView mNetworkRate;
    @BindView(R.id.banner_ad)
    AdView mAdView;

    private PacketCapper mPacketCapper;
    private PacketCapper.CaptureFile mCaptureFile;
    private FirebaseAnalytics mFirebaseAnalytics;
    private IabHelper mHelper;
    private Map<Integer, Float> mRateToFrequency;
    private boolean mRemoveAds;
    private DirectoryChooserFragment mDialog;
    private TSnackbar mSnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packet_capper);
        ButterKnife.bind(this);
        checkForSU();
        extractTCPDump();
        init();
        AppRate.with(this)
                .text(getString(R.string.rate_it))
                .fromTop(true)
                .checkAndShow();
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

    private void showAbout(){
        new EasyLicensesDialogCompat(this)
                .setTitle("Licenses")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_remove_ads).setVisible(!mRemoveAds);
        return super.onPrepareOptionsMenu(menu);
    }

    private void showAppRating(){
        Intent intent = new Intent("android.intent.action.VIEW", Uri.parse("market://details?id=" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_remove_ads) {
            purchaseRemoveAds();
            return true;
        } else if(item.getItemId() == R.id.menu_set_output_directory){
            showDirectoryChooser();
            return true;
        } else if(item.getItemId() == R.id.menu_set_capture_args){
            showCaptureArgsEditor();
            return true;
        } else if(item.getItemId() == R.id.menu_rate_app){
            showAppRating();
            return true;
        } else if(item.getItemId() == R.id.menu_about){
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCaptureArgsEditor() {

    }

    @Override
    public void onSelectDirectory(@NonNull String path) {
        Log.i(TAG, "Selected output directory: " + path);
        Hawk.put(PREF_KEY_OUTPUT_DIRECTORY, path);
        mDialog.dismiss();
    }

    private void dismissSnackbar(){
        if(mSnackbar != null){
            mSnackbar.dismiss();
        }
    }

    @Override
    public void onCancelChooser() {
        mDialog.dismiss();
    }

    private void showDirectoryChooser() {
        final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                .newDirectoryName("packetcapper")
                .allowNewDirectoryNameModification(true)
                .initialDirectory((String)Hawk.get(PREF_KEY_OUTPUT_DIRECTORY))
                .build();
        mDialog = DirectoryChooserFragment.newInstance(config);
        mDialog.show(getFragmentManager(), null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mHelper == null) {
            return;
        }
        mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PacketCapperActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    private void cleanup() {
        mPixelsView.destroy();
        mAdView.destroy();
        if (mHelper != null) {
            mHelper.disposeWhenFinished();
            mHelper = null;
        }
        mHelper = null;
    }

    private void purchaseRemoveAds() {
        IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
            public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                if (result.isFailure()) {
                    Log.d(TAG, "Error purchasing: " + result);
                } else if (purchase.getSku().equals(SKU_AD_FREE)) {
                    removeAd();
                }
            }
        };
        try {
            mHelper.launchPurchaseFlow(this, SKU_AD_FREE, 10001, purchaseFinishedListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        }
    }

    public static Intent getIntent(Context context) {
        return new Intent(context, PacketCapperActivity.class);
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void startCapture() {
        Log.i(TAG, "Starting capture");
        //start the timer now instead of waiting for the capture to boot up
        mElapsedTimer.setBase(SystemClock.elapsedRealtime());
        String outputDirPath = Hawk.get(PREF_KEY_OUTPUT_DIRECTORY);
        File outputFile = new File(outputDirPath, PacketCapper.getCaptureFileName());
        PacketCapper.CaptureOptions options = new PacketCapper.CaptureOptions(outputFile);
        mPacketCapper.capture(options);
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void onPermissionsDenied(){
        mCaptureButton.setProgress(PacketCaptureButton.ERROR_STATE_PROGRESS);
    }

    private void stopCapture() {
        Log.i(TAG, "Stopping capture");
        mPacketCapper.stop();
    }

    private void onCaptureButtonClick() {
        int progress = mCaptureButton.getProgress();
        if (progress == PacketCaptureButton.ERROR_STATE_PROGRESS) { //error to idle
            mCaptureButton.setProgress(PacketCaptureButton.INDETERMINATE_STATE_PROGRESS);
            dismissSnackbar();
            stopCapture();
        } else if (progress == PacketCaptureButton.IDLE_STATE_PROGRESS) { //idle to running
            mCaptureButton.setProgress(PacketCaptureButton.INDETERMINATE_STATE_PROGRESS);
            PacketCapperActivityPermissionsDispatcher.startCaptureWithCheck(this);
        } else if (progress == PacketCaptureButton.SUCCESS_STATE_PROGRESS) { //running to idle
            mCaptureButton.setProgress(PacketCaptureButton.INDETERMINATE_STATE_PROGRESS);
            stopCapture();
        }
    }

    private void init() {
        mNetworkRate.setVisibility(View.INVISIBLE);
        mElapsedTimer.setVisibility(View.INVISIBLE);
        mCapturedBytesCount.setVisibility(View.INVISIBLE);

        mCaptureButton.setIndeterminateProgressMode(true);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCaptureButtonClick();
            }
        });

        mElapsedTimer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                if (mCaptureFile != null) {
                    mCapturedBytesCount.setText(String.format(Locale.getDefault(),
                            "%s captured", Formatter.formatShortFileSize(PacketCapperActivity.this, mCaptureFile.getCaptureSize())));
                } else {
                    mCapturedBytesCount.setText("");
                }
            }
        });

        mRateToFrequency = generateFrequencyRange(0, 5000, 10, 0.0f, 0.50f);

        final TrafficMeter trafficMeter = new TrafficMeter(new TrafficMeter.Listener() {
            @Override
            public void onTrafficRateSampled(int kbps) {
                int rate = getNearestKey(mRateToFrequency, kbps);
                float frequency = mRateToFrequency.get(rate);
                mPixelsView.setFrequency(frequency);
                mNetworkRate.setText(TrafficMeter.formatNetworkRate(kbps));
            }
        });

        mPacketCapper = new PacketCapper(this);
        mPacketCapper.setListener(new PacketCapper.EventListener() {
            @Override
            public void onError(String msg) {
                Log.i(TAG, "Capture error");
                mFirebaseAnalytics.logEvent("capture_error", null);
                if(msg != null) {
                    mSnackbar = TSnackbar.make(mPixelsView, msg, TSnackbar.LENGTH_INDEFINITE);
                    mSnackbar.show();
                }
                mCaptureButton.setProgress(PacketCaptureButton.ERROR_STATE_PROGRESS);
                mElapsedTimer.stop();
                trafficMeter.stop();
                mCaptureFile = null;
                PacketCapperService.stop(getApplicationContext());
                mPixelsView.stopAnimation();
            }

            @Override
            public void onStart(PacketCapper.CaptureFile captureFile) {
                Log.i(TAG, "Capture started");
                mFirebaseAnalytics.logEvent("capture_started", null);
                mCaptureFile = captureFile;
                mCaptureButton.setProgress(PacketCaptureButton.SUCCESS_STATE_PROGRESS);
                mElapsedTimer.setBase(SystemClock.elapsedRealtime());
                mElapsedTimer.start();
                trafficMeter.start();
                mElapsedTimer.setVisibility(View.VISIBLE);
                mNetworkRate.setVisibility(View.VISIBLE);
                mCapturedBytesCount.setVisibility(View.VISIBLE);
                PacketCapperService.start(getApplicationContext());
                mPixelsView.startAnimation();
            }

            @Override
            public void onStop() {
                Log.i(TAG, "Capture stopped");
                mCaptureButton.setProgress(PacketCaptureButton.IDLE_STATE_PROGRESS);
                mElapsedTimer.stop();
                trafficMeter.stop();
                mNetworkRate.setVisibility(View.INVISIBLE);
                mCaptureFile = null;
                PacketCapperService.stop(getApplicationContext());
                mPixelsView.stopAnimation();
            }
        });

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mHelper = new IabHelper(this, PUBLIC_KEY);
        mHelper.enableDebugLogging(BuildConfig.DEBUG);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.d(TAG, "Problem setting up In-app Billing: " + result);
                } else {
                    IabHelper.QueryInventoryFinishedListener inventoryFinishedListener = new IabHelper.QueryInventoryFinishedListener() {
                        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                            if (result.isFailure()) {
                                loadAd();
                            } else {
                                mRemoveAds = inventory.getPurchase(SKU_AD_FREE) != null;
                                if (mRemoveAds) {
                                    removeAd();
                                } else {
                                    loadAd();
                                }
                            }
                        }
                    };
                    try {
                        mHelper.queryInventoryAsync(inventoryFinishedListener);
                    } catch (IabHelper.IabAsyncInProgressException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private Map<Integer, Float> generateFrequencyRange(int startRate, int endRate, int step, float startFrequency, float endFrequency) {
        Map<Integer, Float> map = new TreeMap<>();
        float frequency = startFrequency;
        int numSteps = (endRate - startRate) / step;
        float frequencyStep = (endFrequency - startFrequency) / numSteps;
        for (int rate = startRate; rate <= endRate; rate += step) {
            map.put(rate, frequency);
            frequency += frequencyStep;
        }
        return map;
    }

    public static Integer getNearestKey(Map<Integer, Float> map, long target) {
        double minDiff = Double.MAX_VALUE;
        Integer nearest = null;
        for (Integer key : map.keySet()) {
            double diff = Math.abs(target - key);
            if (diff < minDiff) {
                nearest = key;
                minDiff = diff;
            }
        }
        return nearest;
    }

    private void extractTCPDump() {
        File tcpdump = PacketCapper.getTCPDumpExecutable(this);
        if (!tcpdump.exists()) {
            AssetExtractorTask task = new AssetExtractorTask(this, "tcpdump");
            task.setMakeExecutable(true);
            task.execute(tcpdump);
        }
    }

    private void loadAd() {
        //AdRequest adRequest = new AdRequest.Builder().build();
        //mAdView.loadAd(adRequest);
    }

    private void removeAd() {
        ViewGroup parent = (ViewGroup) mAdView.getParent();
        parent.removeView(mAdView);
    }

    private void checkForSU() {
        boolean suAvailable = Shell.SU.available();
        if (!suAvailable) {
            new MaterialDialog.Builder(this)
                    .title(R.string.notice)
                    .cancelable(false)
                    .content(R.string.su_not_available)
                    .positiveText(android.R.string.ok)
                    .show();
        }
    }

}
