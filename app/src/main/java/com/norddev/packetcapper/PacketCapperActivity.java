package com.norddev.packetcapper;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
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
import com.dd.CircularProgressButton;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.norddev.packetcapper.iap.IabHelper;
import com.norddev.packetcapper.iap.IabResult;
import com.norddev.packetcapper.iap.Inventory;
import com.norddev.packetcapper.iap.Purchase;
import com.orhanobut.hawk.Hawk;
import com.orhanobut.hawk.NoEncryption;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.chainfire.libsuperuser.Shell;
import ir.sohreco.androidfilechooser.ExternalStorageNotAvailableException;
import ir.sohreco.androidfilechooser.FileChooserDialog;

public class PacketCapperActivity extends AppCompatActivity {

    private static final String TAG = "PacketCapperActivity";
    private static final String PREF_KEY_OUTPUT_DIRECTORY = "output_dir";
    private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgKPmaK3l3/zjg8Gi76joeQmwY" +
            "RioqAxQ0kGG9jHuQjWTo94aCESyVR/bRWpgtDOzKNEWRoCOAtZJGt2odunf6SpWP91tcQ1l8n8LLAoYNDgEa8qXEOO9w9CGwcqEOMj" +
            "7eyC6xZAzjSKY2JfTHuKsWc3ChyV6IBXgyj40cxbFu9QKFQrzatCidJF6wnTQQUSAwQjMRPbxQ2F89fTQRGL3iZbEOpxxGp+gqj50+" +
            "w2QOJQOQl/uit5MUy8wLCxrWkW/VHEgRQEXtTxnnTi5NCaKq15M4WvJQlmGJwzL73sd1hkl8GULveeivF7zHw3v7HzCJ/b5Bs1XqJV" +
            "XaCBnMpYRAwIDAQAB"; //FIXME encode
    private static final String SKU_AD_FREE = "feature.ad_free";

    @BindView(R.id.capture_button)
    CircularProgressButton mCaptureButton;
    @BindView(R.id.time_elapsed)
    Chronometer mChronometer;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Hawk.init(this).setEncryption(new NoEncryption()).build();
        setContentView(R.layout.activity_packet_capper);
        ButterKnife.bind(this);
        checkForSU();
        extractTCPDump();
        init();
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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_remove_ads).setVisible(!mRemoveAds);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_remove_ads) {
            purchaseRemoveAds();
        } else {
            try {
                showDirectoryChooser();
            } catch (ExternalStorageNotAvailableException e) {
                e.printStackTrace();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDirectoryChooser() throws ExternalStorageNotAvailableException {
        FileChooserDialog.ChooserListener listener = new FileChooserDialog.ChooserListener() {
            @Override
            public void onSelect(String path) {
                System.out.println("Selected " + path);
                Hawk.put(PREF_KEY_OUTPUT_DIRECTORY, path);
            }
        };
        FileChooserDialog.Builder builder =
                new FileChooserDialog.Builder(FileChooserDialog.ChooserType.DIRECTORY_CHOOSER, listener)
                        .setTitle("Select a directory:")
                        .setInitialDirectory(Environment.getExternalStorageDirectory())
                        .setSelectDirectoryButtonText("ENTEKHAB")
                        .setSelectDirectoryButtonTextSize(25)
                        .setFileIcon(R.drawable.ic_file)
                        .setDirectoryIcon(R.drawable.ic_directory)
                        .setPreviousDirectoryButtonIcon(R.drawable.ic_prev_dir);
        builder.build().show(getSupportFragmentManager(), null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mHelper == null) {
            return;
        }
        mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    private void cleanup() {
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

    private void startCapture() {
        String outputDirPath = Hawk.get(PREF_KEY_OUTPUT_DIRECTORY);
        if(outputDirPath != null) {
            File outputFile = new File(outputDirPath, "test.pcap");
            PacketCapper.CaptureOptions options = new PacketCapper.CaptureOptions(outputFile);
            mPacketCapper.capture(options);
        } else {
            //TODO
        }
    }

    private void stopCapture() {
        mPacketCapper.stop();
    }

    private void onCaptureButtonClick() {
        if (mCaptureButton.getProgress() == -1) { //error to idle
            mCaptureButton.setProgress(0);
        } else if (mCaptureButton.getProgress() == 0) { //idle to running
            mCaptureButton.setProgress(1);
            mChronometer.setBase(SystemClock.elapsedRealtime());
            startCapture();
        } else if (mCaptureButton.getProgress() == 100) { //running to idle
            mCaptureButton.setProgress(0);
            stopCapture();
        }
    }

    private void init() {
        mNetworkRate.setVisibility(View.INVISIBLE);
        mChronometer.setVisibility(View.INVISIBLE);
        mCapturedBytesCount.setVisibility(View.INVISIBLE);

        if(Hawk.get(PREF_KEY_OUTPUT_DIRECTORY) == null){
            File extDir = getExternalFilesDir(null);
            if(extDir != null) {
                Hawk.put(PREF_KEY_OUTPUT_DIRECTORY, extDir.getAbsolutePath());
            } else {
                //TODO
            }
        }

        mCaptureButton.setIndeterminateProgressMode(true);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCaptureButtonClick();
            }
        });

        mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
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
                System.out.println("KBPS = " + kbps + " FREQ = " + frequency);
            }
        });

        mPacketCapper = new PacketCapper(this);
        mPacketCapper.setListener(new PacketCapper.EventListener() {
            @Override
            public void onError() {
                Log.i(TAG, "Capture error");
                mCaptureButton.setProgress(-1);
                mChronometer.stop();
                trafficMeter.stop();
                mCaptureFile = null;
                PacketCapperService.stop(getApplicationContext());
                mPixelsView.stopAnimation();
            }

            @Override
            public void onStart(PacketCapper.CaptureFile captureFile) {
                mFirebaseAnalytics.logEvent("capture_started", null);
                Log.i(TAG, "Capture started");
                mCaptureFile = captureFile;
                mCaptureButton.setProgress(100);
                mChronometer.setBase(SystemClock.elapsedRealtime());
                mChronometer.start();
                trafficMeter.start();
                mChronometer.setVisibility(View.VISIBLE);
                mNetworkRate.setVisibility(View.VISIBLE);
                mCapturedBytesCount.setVisibility(View.VISIBLE);
                PacketCapperService.start(getApplicationContext());
                mPixelsView.startAnimation();
            }

            @Override
            public void onStop() {
                Log.i(TAG, "Capture stopped");
                mCaptureButton.setProgress(0);
                mChronometer.stop();
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
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
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
