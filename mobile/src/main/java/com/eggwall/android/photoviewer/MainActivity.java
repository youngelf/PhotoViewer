package com.eggwall.android.photoviewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.INVISIBLE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.VISIBLE;

/**
 * TODO: Put more than one image in.
 * TODO: Implement a broadcast receiver when a zip is downloaded.
 * TODO: Unzip a file.
 * TODO: Allow traversing existing file structure.
 * TODO: Delete oldest file: LRU cache.
 * TODO: Taps on different parts of the screen lead to different actions: top for showing nav again,
 * TODO: Onscreen buttons, remove with alpha transparency
 *
 * TODO: Fit and finish: animations all over the place.
 */

/**
 * Create a Photo viewer by default.  This screen should show an image by default, and allow the
 * user to change them using the navigation bar.
 */


public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        View.OnSystemUiVisibilityChangeListener {

    GestureDetectorCompat mDetector;

    // All the images we will display
    int[] drawables = {
            R.drawable.ic_menu_camera,
            R.drawable.ic_menu_gallery,
            R.drawable.ic_menu_manage,
            R.drawable.ic_menu_send,
            R.drawable.ic_menu_share,
            R.drawable.ic_menu_slideshow};
    int currentDrawable = 0;

    long mRequestId;

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            //check if the broadcast message is for our enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (referenceId == mRequestId) {
                Toast toast = Toast.makeText(MainActivity.this, "Image Download Complete", Toast
                        .LENGTH_LONG);
                Log.d("MainActivity", "Image download complete");
                toast.show();
            }
        }
    };

    private class FlingDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.d("MainActivity", "Scroll (" + distanceX + ", " + distanceY + ")");
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
            if (v < -2000) {
                Log.d("MainActivity", "PREV image");
                updateImage(PREV);
            } else if (v > 2000) {
                Log.d("MainActivity", "NEXT image");
                updateImage(NEXT);
            }
            Log.d("MainActivity", "Listener detected fling (" + v + " , " + v1 + ").");
            return true;

        }
    }

    String location = "http://gallery.eggwall.com/gallery_23_Sept_Just_Home/_DSC8193.jpg";
    private GestureDetector.OnGestureListener mGestureListener = new FlingDetector();

    int mLastSystemUiVis = 0;
    DrawerLayout mDrawer;
    Toolbar mToolbar;
    int mBaseSystemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private View.OnTouchListener mDelegate = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mDetector.onTouchEvent(motionEvent);
            // Consume the events.  This is required to detect flings, otherwise they get
            // detected as long press events.
            return true;
        }
    };

    public static final int NEXT=1;
    public static final int PREV=-1;
    /**
     * Update the image by providing an offset
     * @param offset is either {@link #NEXT} or {@link #PREV}
     */
    private void updateImage(int offset) {
        if (offset != NEXT && offset != PREV) {
            Log.e(TAG, "updateImage: Incorrect offset provided: " + offset);
            System.exit(-1);
            return;
        }
        currentDrawable += offset;
        if (currentDrawable < 0) {
            currentDrawable = drawables.length - 1;
        }
        if (currentDrawable >= drawables.length) {
            currentDrawable = 0;
        }
        image.setImageResource(drawables[currentDrawable]);

        // Show the correct FAB, and hide it after a while
        if (offset == NEXT) {
            showFab(fNext);
        }
        if (offset == PREV) {
            showFab(fPrev);
        }
    }
    FloatingActionButton fNext;
    FloatingActionButton fPrev;
    AppCompatImageView image;

    boolean keepScreenOn = true;
    Handler h = new Handler();

    public static final String TAG = "MainActivity";

    /** The actual directory that corresponds to the external SD card. */
    private File mPicturesDir;

    /**
     * Returns the names of all the galleries available to the user.
     * @return list of all the galleries in the pictures directory.
     */
    private String[] getPicturesList() {
        if (mPicturesDir == null) {
            mPicturesDir = getPicturesDir();
        }
        // What we return when we don't find anything. It is safer to return a zero length array than null.
        final String[] foundNothing = new String[0];

        // Still nothing? We don't have a valid pictures directory.
        if (mPicturesDir == null) {
            return foundNothing;
        }

        final String[] filenames = mPicturesDir.list();
        Log.e(TAG, "All directories: " + Arrays.toString(filenames));
        if (filenames.length <= 0) {
            Log.e(TAG, "Gallery directory has no files." + mPicturesDir);
            return foundNothing;
        }
        return filenames;
    }


    /** Name of the subdirectory in the main folder containing photos */
    private final static String PICTURES_DIR = "eggwall";

    /**
     * Returns the location of the music directory which is
     * [sdcard]/pictures.
     * @return the file representing the music directory.
     */
    private static File getPicturesDir() {
        final String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // If we don't have an SD card, cannot do anything here.
            Log.e(TAG, "SD card root directory is not available");
            return null;
        }

        final File rootSdLocation;
        if (SDK_INT >= 8) {
            rootSdLocation = getPictureDirAfterV8();
        } else {
            rootSdLocation = getPicturesDirTillV7();
        }
        if (rootSdLocation == null) {
            // Not a directory? Completely unexpected.
            Log.e(TAG, "SD card root directory is NOT a directory: " + rootSdLocation);
            return null;
        }
        // Navigate over to the gallery directory.
        final File galleryDir = new File(rootSdLocation, PICTURES_DIR);
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so try creating one.
            Log.e(TAG, "Gallery directory does not exist." + rootSdLocation);
            boolean result;
            try {
                result = galleryDir.mkdir();
            } catch (Exception e) {
                Log.e(TAG, "Could not create a directory " + e);
                return null;
            }
            if (result) {
                Log.d(TAG, "Created a directory at " + galleryDir.getAbsolutePath());
            } else {
                Log.d(TAG, "FAILED to make a directory at " + galleryDir.getAbsolutePath());
                return null;
            }
        }
        // At this point, we must have a directory, but let's check again to be sure.
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so fail now.
            Log.d(TAG, "I thought I made a directory at " + galleryDir.getAbsolutePath() + " but " +
                    "I couldn't");
            return null;
        }

        return galleryDir;
    }

    /**
     * [sdcard]/music in SDK >= 8
     * @return the [sdcard]/music path in sdk version >= 8
     */
    private static File getPictureDirAfterV8() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    }

    /**
     * [sdcard]/music in SDK < 8
     * @return the [sdcard]/pictures path in sdk version < 8
     */
    private static File getPicturesDirTillV7() {
        return new File(Environment.getExternalStorageDirectory(), "pictures");
    }

    // View animation methods

    /**
     * Shows a Floating Action Button (FAB) immediately, and then fades it out in a few seconds.
     * @param fab
     */
    private void showFab(final View fab){
        // Show it NOW
        fab.animate().alpha(255).setDuration(150).start();

        Runnable doFade = new Runnable() {
            @Override
            public void run() {
                fab.animate().alpha(0).setDuration(500).start();
            }
        };
        // Hide in in seven seconds from now.
        h.postDelayed(doFade, 7000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Make the main view full screen, and listen for System UI visibility changes
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawer.setOnSystemUiVisibilityChangeListener(this);

        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        final AppBarLayout bar = (AppBarLayout) findViewById(R.id.app_bar);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bar.setVisibility(INVISIBLE);
            }
        };


        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bar.getVisibility() == VISIBLE) {
                    bar.setVisibility(INVISIBLE);
                } else {
                    bar.setVisibility(VISIBLE);
                }
            }
        });
        showFab(fab);

        fNext = (FloatingActionButton) findViewById(R.id.next);
        fNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImage(NEXT);
            }
        });
        showFab(fNext);
        fPrev = (FloatingActionButton) findViewById(R.id.prev);
        fPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImage(PREV);
            }
        });
        showFab(fPrev);

        // Hide the navigation after 7 seconds
        h.postDelayed(r, 7000);
//        h.removeCallbacks(r);


        DownloadManager dMan = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(location));

        mRequestId = dMan.enqueue(request);
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        registerReceiver(downloadReceiver, filter);

        image = (AppCompatImageView) findViewById(R.id.photoview);
        image.setOnTouchListener(mDelegate);
        updateImage(NEXT);

        mDetector = new GestureDetectorCompat(this, mGestureListener);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawer, mToolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.setDrawerListener(toggle);
        toggle.syncState();

        getPicturesList();
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }


    public void onSystemUiVisibilityChange(int visibility) {
        // Detect when we go out of low-profile mode, to also go out
        // of full screen.  We only do this when the low profile mode
        // is changing from its last state, and turning off.
        int diff = mLastSystemUiVis ^ visibility;
        mLastSystemUiVis = visibility;
        if ((diff & SYSTEM_UI_FLAG_LOW_PROFILE) != 0
                && (visibility & SYSTEM_UI_FLAG_LOW_PROFILE) == 0) {
            setNavVisibility(true);
        }
    }


    Runnable mNavHider = new Runnable() {
        @Override
        public void run() {
            setNavVisibility(false);
        }
    };

    void setBaseSystemUiVisibility(int visibility) {
        mBaseSystemUiVisibility = visibility;
    }

    void setNavVisibility(boolean visible) {
        int newVis = mBaseSystemUiVisibility;
        if (!visible) {
            newVis |= SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_FULLSCREEN;
        }
        final boolean changed = newVis == mDrawer.getSystemUiVisibility();

        // Unschedule any pending event to hide navigation if we are
        // changing the visibility, or making the UI visible.
        if (changed || visible) {
            Handler h = mDrawer.getHandler();
            if (h != null) {
                h.removeCallbacks(mNavHider);
            }
        }

        // Set the new desired visibility.
        mDrawer.setSystemUiVisibility(newVis);
        mToolbar.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }
}
