package com.eggwall.android.photoviewer;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

/*
 * TODO: Delete oldest file: LRU cache.
 * TODO: Periodically poll the RSS feed for new content.
 * TODO:    GCM cloud messaging to avoid polling.
 * TODO: A UI to show all the albums (today only one is shown)
 * TODO: Settings activity to change slideshow duration, auto-start newest, download frequency, etc
 * TODO: pinch-zoom on an image.
 * TODO: Diagnostics in the app to find what's wrong.
 * DO:NE Remember offset in the album when rotating.
 * DONE: Remember if autoplay was on when rotating.
 * DONE: Showing slideshow state, and allowing slideshow to stop.
 * DONE: Desktop application to create these image files.
 * DONE: Read keys and RSS-like locations from a bar code.
 * DONE: Store keys and associated information in the database.
 * DONE: Some unique ID to separate two feeds from one another.
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

    // TODO: The common_google_play_services stuff here is bad, and needs to be removed and real
    // wording introduced.

    // Temporarily, all permissions on creation
    private void requestWriteExternalStoragePermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Inform and request")
                    .setMessage("You need to enable permissions, bla bla bla")
                    .setPositiveButton(R.string.common_google_play_services_enable_text,
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
                    .setTitle("Inform and request")
                    .setMessage("You need to enable permissions, bla bla bla")
                    .setPositiveButton(R.string.common_google_play_services_enable_text,
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
    protected void onSaveInstanceState(Bundle outState) {
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
            case NetworkRoutines.TYPE_DOWNLOAD:
                // Asked to download a package file
                NetworkRoutines.DownloadInfo album = NetworkRoutines.getDownloadInfo(startIntent);
                if (album != NetworkRoutines.EMPTY) {
                    Log.d(TAG, "I'm going to download this URL now: " + album);
                    // Now download that URL and switch over to that screen.
                    mc.download(album);
                }
                break;
            case NetworkRoutines.TYPE_SECRET_KEY:
                // Asked to import a key for future use in decryption.
                NetworkRoutines.KeyImportInfo key = NetworkRoutines.getKeyInfo(startIntent);
                if (key != NetworkRoutines.EMPTY_KEY) {
                    Log.d(TAG, "I'm going to import this key now: " + key);
                    // Now download that URL and switch over to that screen.
                    mc.importKey(key);
                }
                break;
            case NetworkRoutines.TYPE_DEV_CONTROL:
                NetworkRoutines.callControl(startIntent, mc);
            default:
                // Should never happen since getIntentType only gives known values.
                Log.wtf(TAG, "Unknown actionType: " + actionType);
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
