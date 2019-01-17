package com.eggwall.android.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.VISIBLE;

/* While this class is fine, some cleanup can be done here.
 *** Remove the gesture listening code now that I have onscreen buttons.
 *** Make the Action bar play well with the System UI.  Right now they are disconnected.
 *** Hide all the elements (all fabs, and all navigation) on the same runnable.
 *** Show a progress indicator for the gallery.
 *** Read this:
 *** * https://developer.android.com/topic/performance/graphics/load-bitmap
 *** * to ensure that the bitmaps don't take too much RAM.
*/

/**
 * Orchestrates User Interface actions, and drives the display.
 */
class UiController implements NavigationView.OnNavigationItemSelectedListener,
        View.OnSystemUiVisibilityChangeListener {

    private static final String TAG = "UiController";

    /** Magic View. constant that says no system UI at all. */
    private static final int SYSUI_INVISIBLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    /** Magic View. constant that says show the system UI. */
    private static final int SYSUI_VISIBLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    /** Runnable to hide the System UI. */
    private final Runnable hideSysUi = new Runnable() {
        @Override
        public void run() {
            setSystemUiVisibility(SYSUI_INVISIBLE);
        }
    };

    /** Runnable to hide the navigation bar (Action Bar) */
    private final Runnable hideNav = new Runnable() {
        @Override
        public void run() {
            setNavVisibility(false);
        }
    };

    /** The Activity that we are controlling and that created us. */
    private final MainActivity mMainActivity;

    /** The orchestrator that will show next image or previous image based on button presses. */
    private final MainController mainController;

    private final Handler mHandler = new Handler();

    // Required for handling swipe gestures.
    private final GestureDetector.OnGestureListener mGestureListener = new FlingDetector();

    // References to on-screen elements
    private FloatingActionButton mNextFab;
    private FloatingActionButton mPrevFab;
    private AppCompatImageView mImageView;
    private Toolbar mToolbar;
    private DrawerLayout mDrawer;

    /**
     * Current system UI visibility. Stored because we get UI visibility changes in different
     * methods and we need to keep track of the prior visibility.
     */
    private int mSysUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            SYSTEM_UI_FLAG_LAYOUT_STABLE;

    /** True if the slide show is currently on auto-play mode. */
    private boolean mSlideShowStatus = false;

    private int mLastSystemUiVis = 0;
    private GestureDetectorCompat mDetector;

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
                mainController.updateImage(UiConstants.PREV, true);
            } else if (v > 2000) {
                Log.d("MainActivity", "NEXT image");
                mainController.updateImage(UiConstants.NEXT, true);
            }
            Log.d("MainActivity", "Listener detected fling (" + v + " , " + v1 + ").");
            return true;

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
            mSlideShowStatus = setSlideshow(!mSlideShowStatus);

            // Set the state of item to show if the slideshow is playing or paused
            item.setIcon(mSlideShowStatus ? R.drawable.ic_pause : R.drawable.ic_play);
        } else if (id == R.id.nav_manage) {
            // Download a zip file from somewhere and unzip it.
            // TODO: make this pop out a dialog instead.
            // mFileController.addUri("http://192.168.11.122/images.zip");
        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Shows a Floating Action Button (FAB) immediately, and then fades it out in a few seconds.
     *
     * @param fab
     */
    private void showFab(final View fab) {
        // Show it
        fab.animate().alpha((float) 0.5).setDuration(350).start();

        Runnable fadeAway = new Runnable() {
            @Override
            public void run() {
                fab.animate().alpha(0).setDuration(700).start();
            }
        };
        // Hide in in three seconds from now.
        mHandler.postDelayed(fadeAway, 3000);
    }

    /**
     * Update the image by providing an offset
     *
     * @param nextFile The path of the next file to display.
     * @param offset  is either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     * @param showFab True if the Floating Action Bar should be shown, false if it should be hidden.
     */
     void updateImage(String nextFile, int offset, boolean showFab) {
        // Calculate how big the bitmap is
        BitmapFactory.Options opts = new BitmapFactory.Options();
        // Just calculate how big the file is to learn the sizes
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(nextFile, opts);

        ExifInterface exif = null;
        try {
            exif = new ExifInterface(nextFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        if (exif != null) {
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        }

        // Width and height have to get swapped for rotated images.
        final boolean isPortrait =
                    orientation == ExifInterface.ORIENTATION_ROTATE_90
                            || orientation == ExifInterface.ORIENTATION_ROTATE_270;

        // This is how big the image is:
        final int imageViewWidth = mImageView.getWidth();
        final int imageViewHeight = mImageView.getHeight();

        // This calculates the sampling ratio for the image.
        opts.inSampleSize = sampling(opts, imageViewWidth, imageViewHeight, isPortrait);

        opts.inJustDecodeBounds = false;
        Bitmap sourceBitmap = BitmapFactory.decodeFile(nextFile, opts);

        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                // Fall through
            case ExifInterface.ORIENTATION_UNDEFINED:
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                if (sourceBitmap != null) {
                    sourceBitmap = getRotated(sourceBitmap, 90);
                }
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                if (sourceBitmap != null) {
                    sourceBitmap = getRotated(sourceBitmap, 270);
                }
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                if (sourceBitmap != null) {
                    sourceBitmap = getRotated(sourceBitmap, 180);
                }
                break;
            default:
                Log.wtf(TAG, "Exif interface showed unsupported orientation " + orientation);
        }

        mImageView.setImageBitmap(sourceBitmap);
        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (showFab) {
            // Show the correct FAB, and hide it after a while
            if (offset == UiConstants.NEXT) {
                showFab(mNextFab);
            }
            if (offset == UiConstants.PREV) {
                showFab(mPrevFab);
            }
        }
    }

    /**
     * Get a rotated image.
     * @param sourceBitmap The original bitmap to rotate.
     * @param degrees Degrees to rotate the original image
     * @return A rotated bitmap
     */
    private Bitmap getRotated(Bitmap sourceBitmap, int degrees) {
        Matrix matrix = new Matrix();
        // Width and height here pertains to the bitmap
        int height = sourceBitmap.getHeight();
        int width = sourceBitmap.getWidth();
        matrix.postRotate(degrees, width / 2, height / 2);
        Bitmap rotated = Bitmap.createBitmap(
                sourceBitmap, 0, 0, width, height, matrix, true);
        return rotated;
    }

    /**
     * Calculate the sampling rate for the image, since most images have to be downsampled to fit
     * the on-screen view
     * @param options The opts object from a previous call to ExifFactory
     * @param reqWidth the width of the view we will display eventually
     * @param reqHeight the height of the view we will display eventually
     * @param rotate If true, then the image is rotated 90 degrees or 270 degrees
     * @return The sampling rate by which the entire image gets reduced.
     */
    private static int sampling(
            BitmapFactory.Options options, int reqWidth, int reqHeight, boolean rotate) {
        // Raw height and width of image
        final float height;
        final float width;
        // Switch height and width for images that are rotated.
        if (rotate) {
            height = options.outWidth;
            width = options.outHeight;
        } else {
            height = options.outHeight;
            width = options.outWidth;
        }
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            float halfHeight = height / 2;
            float halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    void setBaseSystemUiVisibility(int visibility) {
        mSysUiVisibility = visibility;
    }

    /**
     * Sets the System Ui Visibility.  Only accepts two values: {@link #SYSUI_INVISIBLE} or
     * {@link #SYSUI_VISIBLE}
     *
     * @param visibility
     */
    private void setSystemUiVisibility(int visibility) {
        if (visibility != SYSUI_VISIBLE && visibility != SYSUI_INVISIBLE) {
            Log.wtf(TAG, "setSystemUiVisibility only accepts SYSUI_{INVISIBLE,VISIBLE}. " +
                    "Your value of " + visibility + " was ignored");
            return;
        }
        mMainActivity.getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    /**
     * Hide the system UI.
     */
    private void hideSystemUI() {
        mMainActivity.getWindow().getDecorView().setSystemUiVisibility(SYSUI_INVISIBLE);
    }

    /**
     * Show the system UI.
     */
    private void showSystemUI() {
        mMainActivity.getWindow().getDecorView().setSystemUiVisibility(SYSUI_VISIBLE);
        // And request it to be hidden in five seconds
        mHandler.removeCallbacks(hideSysUi);
        mHandler.postDelayed(hideSysUi, 5000);
    }

    /**
     * Set the navigation bar visibility to the value here.
     * @param visible true if the navigation bar is to be shown.
     */
    private void setNavVisibility(boolean visible) {
        int newVis = mSysUiVisibility;
        if (!visible) {
            newVis |= SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_FULLSCREEN;
        }
        final boolean changed = newVis == mDrawer.getSystemUiVisibility();

        // Unschedule any pending event to hide navigation if we are
        // changing the visibility, or making the UI visible.
        if (changed || visible) {
            Handler h = mDrawer.getHandler();
            if (h != null) {
                h.removeCallbacks(hideNav);
            }
        }

        // Set the new desired visibility.
        mDrawer.setSystemUiVisibility(newVis);
        mToolbar.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    private View.OnTouchListener mDelegate = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mDetector.onTouchEvent(motionEvent);
            // Consume the events.  This is required to detect flings, otherwise they get
            // detected as long press events.
            showSystemUI();

            // If the touch is on the right half, go to the next image, if the touch is on the
            // left half, go to the left image.
            return true;
        }
    };

    UiController(MainActivity mainActivity, MainController mainController) {
        this.mMainActivity = mainActivity;
        this.mainController = mainController;
    }

    /**
     * Handler for window focus changes.
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    void createController() {
        // Make the main view full screen, and listen for System UI visibility changes
        mDrawer = mMainActivity.findViewById(R.id.drawer_layout);
        mDrawer.setOnSystemUiVisibilityChangeListener(this);

        final AppBarLayout bar = mMainActivity.findViewById(R.id.app_bar);
        final Toolbar toolbar = mMainActivity.findViewById(R.id.toolbar);
        Runnable hideBarAndToolbar = new Runnable() {
            @Override
            public void run() {
                bar.setVisibility(View.GONE);
                toolbar.setVisibility(View.GONE);
            }
        };
        final Runnable showFirstImage = new Runnable() {
            @Override
            public void run() {
                mainController.updateImage(UiConstants.NEXT, false);
            }
        };

        final FloatingActionButton fab = mMainActivity.findViewById(R.id.fab);
        View.OnClickListener toolBarListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bar.getVisibility() == VISIBLE) {
                    toolbar.setVisibility(GONE);
                    bar.setVisibility(GONE);
                } else {
                    bar.setVisibility(VISIBLE);
                    toolbar.setVisibility(View.VISIBLE);
                }
            }
        };

        View.OnClickListener drawerToggler = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDrawer.isDrawerVisible(Gravity.LEFT)) {
                    mDrawer.closeDrawers();
                } else {
                    mDrawer.openDrawer(Gravity.LEFT, true);
                    showSystemUI();
                }
            }
        };

        fab.setOnClickListener(drawerToggler);
        mMainActivity.findViewById(R.id.drawer_button_invi).setOnClickListener(drawerToggler);

        showFab(fab);

        mMainActivity.findViewById(R.id.next);

        setClickListener(R.id.next_button_invi, UiConstants.NEXT);
        mNextFab = (FloatingActionButton) setClickListener(R.id.next, UiConstants.NEXT);
        showFab(mNextFab);

        setClickListener(R.id.prev_button_invi, UiConstants.PREV);
        mPrevFab = (FloatingActionButton) setClickListener(R.id.prev, UiConstants.PREV);
        showFab(mPrevFab);

        mToolbar = mMainActivity.findViewById(R.id.toolbar);
        mMainActivity.setSupportActionBar(mToolbar);

        mImageView = mMainActivity.findViewById(R.id.photoview);
        mImageView.setOnTouchListener(mDelegate);

        mDetector = new GestureDetectorCompat(mMainActivity, mGestureListener);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                mMainActivity, mDrawer, mToolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.setDrawerListener(toggle);
        toggle.syncState();

        // Listen to our own Drawer element selection events.
        ((NavigationView) mMainActivity.findViewById(R.id.nav_view))
                .setNavigationItemSelectedListener(this);

        // Hide the navigation after 7 seconds
        mHandler.postDelayed(hideBarAndToolbar, 7000);

        // UGLY hack to show the first image after everything is hooked up.
        mHandler.postDelayed(showFirstImage, 500);
    }

    /**
     * Show the next image after 10,000 milliseconds.
     */
    private final Runnable mShowNext = new Runnable() {
        @Override
        public void run() {
            mainController.updateImage(UiConstants.NEXT, false);
            // New image every 10 seconds.
            mHandler.postDelayed(this, 10000);
        }
    };

    /**
     * Sets the slideshow status, and returns the current status.
     *
     * @param start true to start the show, false to stop
     * @return current status: true if started, false if stopped.
     */
    public boolean setSlideshow(boolean start) {
        if (start) {
            // Start it in 300 ms from now.
            mHandler.postDelayed(mShowNext, 300);
        } else {
            mHandler.removeCallbacks(mShowNext);
        }
        boolean status = start;
        return status;
    }

    /**
     * @param resourceId
     * @param action
     * @return
     */
    private View setClickListener(int resourceId, final int action) {
        if (action != UiConstants.NEXT && action != UiConstants.PREV) {
            Log.w(TAG, "setClickListener called with " + action);
            return null;
        }

        View v = mMainActivity.findViewById(resourceId);
        if (v != null) {
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mainController.updateImage(action, true);
                }
            });
        }
        return v;
    }

    @Override
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

}
