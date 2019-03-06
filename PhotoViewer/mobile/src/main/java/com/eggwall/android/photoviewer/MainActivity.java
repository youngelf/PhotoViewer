package com.eggwall.android.photoviewer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import static com.eggwall.android.photoviewer.AndroidRoutines.logDuringDev;

/*
 * TODO: Delete oldest file: LRU cache.
 * TODO: Periodically poll the RSS feed for new content.
 * TODO:    GCM cloud messaging to avoid polling.
 * TODO: pinch-zoom on an image.
 * TODO: Show the download date of the album alongside the name in the drawer.
 * TODO: Show a demo/intro activity in ImportActivity
 * TODO: Allow multiple links to be imported in ImportActivity.
 * TODO: Background alarm for housekeeping: removing old content, purging and pruning the database.
 * TODO: Background process for keeping disk size within bounds.
 * TODO: Read values from settings rather than hardcoded values.
 * TODO: Better icons than the small and flat set:
 *      http://www.iconarchive.com/show/oxygen-icons-by-oxygen-icons.org.9.html
 *
 * DONE: Settings activity to change slideshow duration, auto-start newest, download frequency, etc
 * DONE: Diagnostics in the app to find what's wrong.
 * DONE: A UI to show all the albums (today only one is shown)
 * DONE: Remember offset in the album when rotating.
 * DONE: Remember if autoplay was on when rotating.
 * DONE: Showing slideshow state, and allowing slideshow to stop.
 * DONE: Desktop application to create these image files.
 * DONE: Read keys and RSS-like locations from a bar code.
 * DONE: Store keys and associated information in the database.
 * DONE: Some unique ID to separate two feeds from one another.
 * DONE: The common_google_play_services stuff needs to be removed and real wording introduced.
 */

/**
 * Create a Photo viewer.  This screen should show an image by default, and allow the
 * user to change them using the navigation bar.
 */
public class MainActivity extends AppCompatActivity {
    boolean keepScreenOn = true;
    private static final String TAG = "MainActivity";

    // TODO: Need to write the onRequestPermissionResult work.
    /**
     * Unique code given to the Write External Storage permission request to match the result that
     * we'll get in onRequestPermissionResult.
     */
    public static final int REQUEST_WRITE_EXTERNAL_STORAGE = 80;

    /**
     * Unique code given to the Write External Storage permission request to match the result that
     * we'll get in onRequestPermissionResult.
     */
    public final int REQUEST_READ_EXTERNAL_STORAGE = 81;

    // One side benefit of calling it Main Controller that the object itself is the MC.
    /** The object that orchestrates the other controllers. */
    private MainController mc;


    // Temporarily, all permissions on creation
    private void requestWriteExternalStoragePermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Write to disk")
                    .setMessage("This program needs to download images and write them to disk.")
                    .setPositiveButton(R.string.app_name,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    REQUEST_WRITE_EXTERNAL_STORAGE);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    // Temporarily, all permissions on creation
    private void requestReadExternalStoragePermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Read from disk")
                    .setMessage("This program needs to read images to display, from disk.")
                    .setPositiveButton(R.string.app_name,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                    REQUEST_READ_EXTERNAL_STORAGE);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mc.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.activity_main);
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // TODO: Fix the errors this is causing in the logs. I think I am only permitted to show
        // one of these screens at a time.
        requestWriteExternalStoragePermission();
        requestReadExternalStoragePermission();

        // Create the primary controller that orchestrates everything.
        mc = new MainController();
        if (!mc.create(this)) {
            // Nothing is going to work without a MainController.
            AndroidRoutines.crashHard("Could not construct a Main Controller");
        }

        // See if the program was asked to do something specific or was just started from Launcher
        Intent startIntent = getIntent();
        int actionType = NetworkRoutines.getIntentType(startIntent);
        switch (actionType) {
            case NetworkRoutines.TYPE_IGNORE:
                // Launched from launcher or without any specific request. Try to resume showing
                // the previous album or show the most recently downloaded album.
                final MainController mainController = mc;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (!mainController.showInitial(icicle)) {
                            mainController.toast("Could not show the first screen");
                        }
                    }
                }).start();
                break;
            default:
                // Should never happen since getIntentType only gives known values.
                Log.wtf(TAG, "Unknown actionType: " + actionType);
                break;
            case NetworkRoutines.TYPE_DEV_CONTROL:
                // Fall through!
            case NetworkRoutines.TYPE_DOWNLOAD:
                // Fall through!
            case NetworkRoutines.TYPE_SECRET_KEY:
                // Get the URI that corresponds to these actions
                handleUri(NetworkRoutines.getUri(startIntent));
                break;
        }
    }

    /**
     * For a given URI, either as a custom URI or as input to {@link ImportActivity}, go through
     * the URI and handle the {@link NetworkRoutines#TYPE_DOWNLOAD} or
     * {@link NetworkRoutines#TYPE_DEV_CONTROL}, {@link NetworkRoutines#TYPE_DEV_CONTROL} actions.
     * @param in the URL to act upon, received either by clicking on a custom URI in a browser, or
     *           as a text input by the user in {@link ImportActivity}
     */
    private void handleUri(@NonNull Uri in) {
        if (in == Uri.EMPTY) {
            return;
        }

        // Examine what we got.
        int type = NetworkRoutines.getUriType(in);

        switch (type) {
            case NetworkRoutines.TYPE_DOWNLOAD:
                NetworkRoutines.DownloadInfo album = NetworkRoutines.getDownloadInfo(in);
                logDuringDev(TAG, "Download Request = " + album.debugString());
                if (album != NetworkRoutines.EMPTY) {
                    Log.d(TAG, "I'm going to download this URL now: " + album);
                    // Now download that URL and switch over to that screen.
                    mc.download(album);
                }
                break;
            case NetworkRoutines.TYPE_SECRET_KEY:
                NetworkRoutines.KeyImportInfo key = NetworkRoutines.getKeyInfo(in);
                if (key != NetworkRoutines.EMPTY_KEY) {
                    Log.d(TAG, "I'm going to import this key now: " + key);
                    // Now download that URL and switch over to that screen.
                    mc.importKey(key);
                }
                break;
            case NetworkRoutines.TYPE_MONITOR:
                // Get the URL, then write it to Settings.
                String beacon = NetworkRoutines.getMonitorUri(in);
                if (beacon.length() > 0) {
                    // Some URL needs to be monitored, let's remember it.
                    Pref.modify(this, Pref.Name.BEACON, beacon);
                }
                break;
            case NetworkRoutines.TYPE_DEV_CONTROL:
                NetworkRoutines.callControl(in, mc);
                break;
            default:
                // Should never happen since getIntentType only gives known values.
                Log.wtf(TAG, "Unknown URI: " + in);
                break;
        }
    }

    /**
     * Called when any sub-Activity is started with {@link #startActivityForResult(Intent, int)}
     * and they {@link #finish()}. Their result is picked up here.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Read the parameters and see if we receive them.
        AndroidRoutines.logDuringDev(TAG, "Requestcode = " + requestCode);

        switch (requestCode) {
            case ImportActivity.REQUEST_DOWNLOAD:
                // Can't do anything if the return value was poor. In theory, this will always be non-null
                // as we will provide an empty Uri if nothing else.
                if (data == null) {
                    return;
                }

                // Get the parsed URI, it is a parcelable.
                Uri in = data.getParcelableExtra(ImportActivity.KEY_URI);
                handleUri(in);
                break;
            case SettingActivity.REQUEST_SETTINGS:
                // Do nothing.
                break;

            default:
                AndroidRoutines.crashDuringDev("Not expecting this request code");
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Ask the controllers to give up their dependencies!
        // This does not help with the Activities being retained, and I cannot imagine why the
        // system keeps six Activity objects around.
        mc.destroy();
        mc = null;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        // Here, try to inflate another menu.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mc.onWindowFocusChanged(hasFocus);
    }

}
