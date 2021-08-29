package com.norddev.packetcapper.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.codekidlabs.storagechooser.StorageChooser;
import com.norddev.packetcapper.R;
import com.norddev.packetcapper.helpers.CaptureInterfacesHelper;
import com.norddev.packetcapper.models.CaptureOptions;

import java.util.List;

import de.psdev.licensesdialog.LicensesDialog;
import de.psdev.licensesdialog.licenses.ApacheSoftwareLicense20;
import de.psdev.licensesdialog.licenses.GnuLesserGeneralPublicLicense21;
import de.psdev.licensesdialog.model.Notice;
import de.psdev.licensesdialog.model.Notices;
import fr.nicolaspomepuy.discreetapprate.AppRate;

public class PacketCapperActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packet_capper);

        AppRate.with(this)
                .text(getString(R.string.rate_it))
                .fromTop(true)
                .checkAndShow();
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
        } else if (item.getItemId() == R.id.menu_browse_captures) {
            showCaptureBrowser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCaptureBrowser() {

    }

    private void showCaptureInterfaceChooser() {
        final List<String> interfaces = CaptureInterfacesHelper.getInterfaces();
        String currentIface = CaptureOptions.Default.getDefaultInterfaceName(this);
        int defaultIndex = interfaces.indexOf(currentIface);

        CharSequence[] items = new String[interfaces.size()];
        interfaces.toArray(items);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.set_capture_interface);
        builder.setSingleChoiceItems(items, defaultIndex, (dialogInterface, i) -> {
            CaptureOptions.Default.setDefaultInterfaceName(interfaces.get(i));
            dialogInterface.dismiss();
        });

        builder.create().show();
    }

    private void showCaptureArgsEditor() {

    }

    private void showDirectoryChooser() {
        StorageChooser chooser = new StorageChooser.Builder()
                .withActivity(this)
                .withFragmentManager(getFragmentManager())
                .withMemoryBar(true)
                .allowCustomPath(true)
                .allowAddFolder(true)
                .setType(StorageChooser.DIRECTORY_CHOOSER)
                .withPredefinedPath(CaptureOptions.Default.getDefaultCaptureDirectoryPath())
                .build();
        chooser.show();
        chooser.setOnSelectListener(CaptureOptions.Default::setDefaultCaptureDirectoryPath);
    }

    public static Intent getIntent(Context context) {
        return new Intent(context, PacketCapperActivity.class);
    }
}
