package com.eggwall.android.photoviewer;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
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

import com.eggwall.android.photoviewer.data.Album;
import com.eggwall.android.photoviewer.data.AlbumDao;
import com.eggwall.android.photoviewer.data.AlbumDatabase;

import java.util.Set;

/*
 * TODO: Delete oldest file: LRU cache.
 * TODO: Read keys and RSS-like locations from a bar code.
 * TODO: Store keys and associated information in the database.
 * TODO: Periodically poll the RSS feed for new content.
 * TODO:    GCM cloud messaging to avoid polling.
 * TODO: Some unique ID to separate two feeds from one another.
 * TODO: A UI to show all the albums (today only one is shown)
 * TODO: Showing slideshow state, and allowing slideshow to stop.
 * TODO: Settings activity to change slideshow duration, auto-start newest, download frequency, etc
 * TODO: Desktop application to create these image files.
 * TODO: pinch-zoom on an image.
 * TODO: Diagnostics in the app to find what's wrong.
 */

/**
 * Create a Photo viewer.  This screen should show an image by default, and allow the
 * user to change them using the navigation bar.
 */
public class MainActivity extends AppCompatActivity {
    boolean keepScreenOn = true;
    public static final String TAG = "MainActivity";

    // One side benefit of calling it Main Controller that the object itself is the MC.
    MainController mc;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        requestReadExternalStoragePermission();
        requestWriteExternalStoragePermission();

        mc = new MainController();
        mc.create(this);

        mc.showInitial();

        // Let's try out the database code
        DbTester s = new DbTester(this);
        s.execute();

        Uri toDownload = getDownloadInfo(getIntent());
        if (toDownload != null) {
            Log.d(TAG, "I'm going to download this URL now: " + toDownload);
            // Now download that URL and switch over to that screen.
        }

        CryptoRoutines.decryptTextTest();
        CryptoRoutines.decryptFileTest();
    }

    /**
     * Get the URL to download from the intent this application was started from.
     * @param i
     * @return
     */
    Uri getDownloadInfo(Intent i) {
        String action = i.getAction();
        Log.d(TAG, "Action = " + action);
        // Data = photoviewer://eggwall/test?q=this
        Log.d(TAG, "Data = " + i.getData());

        String KEY_NAME = "src";
        Uri toReturn = null;
        // Unpack the actual URL from that data string
        Uri uri = i.getData();
        if (uri == null) {
            return toReturn;
        }

        String scheme = uri.getScheme();
        Log.d(TAG, "Scheme = " + scheme);
        String path = uri.getPath();
        Log.d(TAG, "Path = " + path);
        String PHOTOVIEWER = "photoviewer";
        if (action.equals(Intent.ACTION_VIEW)
                && scheme != null
                && scheme.equals(PHOTOVIEWER)
                && path != null) {
            // Something we can act on
            Set<String> names = uri.getQueryParameterNames();
            if (names.contains(KEY_NAME)) {
                String encoded = uri.getQueryParameter(KEY_NAME);
                toReturn = Uri.parse(Uri.decode(encoded));
            }
        }
        return toReturn;
    }

    static class DbTester extends AsyncTask<Void, Void, Void> {
        private final Context context;

        DbTester(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            AlbumDatabase db = AlbumDatabase.getDatabase(context);
            db.clearAllTables();
            AlbumDao d = db.albumDao();
            Album s = new Album();
//            s.setId(1);
//            s.setName("Pismo");
//            s.setRemoteLocation("http://nothing.com");
//            List<Album> p = d.getAll();
//            s = p.get(0);
//            Log.d(TAG, "Test album from previous insert = " + s);
            return null;
        }
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
