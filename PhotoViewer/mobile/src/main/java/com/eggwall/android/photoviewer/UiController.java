package com.eggwall.android.photoviewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

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

    /**
     * {@link Bundle} key containing whether {@link #slideShowPlaying} is true.
     */
    private static final String SS_AUTOPLAY = "uic-slideshowplaying";

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
    private boolean slideShowPlaying = false;

    private int mLastSystemUiVis = 0;
    private GestureDetectorCompat mDetector;

    /**
     * Show a diagnostic message.
     *
     * Call from any thread.
     * @param message any human readable message (unfortunately not localized!)
     */
    public void MakeText(final String message) {
        if (AndroidRoutines.isMainThread()) {
            // Just create a toast, and be done with it.
            Toast.makeText(mMainActivity, message, Toast.LENGTH_LONG).show();
        } else {
            // Post a runnable on a view in a foreground thread (cannot do UI in background)
            mDrawer.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mMainActivity, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Save any instance state that might be needed later.
     * @param icicle guaranteed non-null
     */
    public void onSaveInstanceState(Bundle icicle) {
        icicle.putBoolean(SS_AUTOPLAY, slideShowPlaying);
    }

    /**
     * Load up any instance state saved earlier.
     * @param icicle perhaps null
     */
    public void loadInitial(Bundle icicle) {
        if (icicle == null) {
            // This method can only work if the icicle exists.
            return;
        }

        slideShowPlaying = icicle.getBoolean(SS_AUTOPLAY, false);
        MenuItem item = mMainActivity.findViewById(R.id.nav_slideshow);
        if (item != null) {
            setSlideshow(slideShowPlaying, item);
        }
    }

    /**
     * Detects left-to-right swipe (next image) and right-to-left swipe (previous image).
     *
     * It is rough, doesn't provide any feedback to the user that the action is going to complete
     * and also doesn't show the next image (as you would with a ViewPager).
     *
     * Barely satisfactory, and it might have to be removed or redone when implementing
     * pinch-to-zoom, which is more useful than swipe next/previous.
     */
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // I should find a way to get the recent albums listed at the start of the list, so the
        // viewer doesn't have to click into Gallery, and then choose one from there. Showing two
        // or three recent ones (including the current one, highlighted) should be good.
        switch (item.getItemId()) {
            case R.id.nav_camera:
                // Handle the camera action
                break;
            case R.id.nav_gallery:
                // Show an activity listing all the albums, or easier, just swap out the layouts
                // and show a Linear list adjacent to the images. Simpler is better.
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setClass(mMainActivity, AlbumListActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mMainActivity.startActivityForResult(i, 2);
                break;

            case R.id.nav_slideshow:
                slideShowPlaying = setSlideshow(!slideShowPlaying, item);
                break;

            case R.id.nav_manage:
                // TODO: Show a Settings Activity instead.
                break;

            case R.id.nav_share:
                // Perhaps scale the image and start a share dialog.
                break;

            case R.id.nav_send:
                // Send the URL to someone else? Maybe the image can be sent?
                // Perhaps make the image really small and fire the email intent.
                break;

        }
        // Since the user clicked on some item, dismiss the drawer (if open)
        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Shows a Floating Action Button (FAB) immediately, and then fades it out in a few seconds.
     *
     * @param fab the view that is the floating action bubble, cannot be null.
     */
    private void showFab(@NonNull final View fab) {
        // Show it
        fab.animate().alpha((float) 0.5).setDuration(350).start();

        Runnable fadeAway = new Runnable() {
            @Override
            public void run() {
                fab.animate().alpha(0).setDuration(700).start();
            }
        };
        // Hide in in six seconds from now.
        mHandler.postDelayed(fadeAway, 6000);
    }

    /**
     * Update the image by providing an offset
     *
     * @param nextFile The path of the next file to display.
     * @param offset  is either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     * @param showFab True if the Floating Action Bar should be shown, false if it should be hidden.
     */
    void updateImage(String nextFile, final int offset, final boolean showFab) {
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
        final int imageViewWidth;
        final int imageViewHeight;

        // Check if Display metrics are a better source
        if (mImageView.getWidth() == 0 || mImageView.getHeight() == 0) {
            DisplayMetrics dm = new DisplayMetrics();
            mMainActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
            Log.d(TAG, "DM height = " + dm.heightPixels + ", width=" + dm.widthPixels);
            // Use those instead since they are better.
            // To be fair, dm is NOT the image view's height. dm might be bigger. In practice,
            // this works great because
            // (1) We need an estimate for sampling.
            // (2) The application is in full-screen mode, so they should be very close.
            // (3) The sampling factor might be identical either way.
            imageViewHeight = dm.heightPixels;
            imageViewWidth = dm.widthPixels;
        } else {
            imageViewWidth = mImageView.getWidth();
            imageViewHeight = mImageView.getHeight();
        }

        // This calculates the sampling ratio for the image.
        int sampleSize = sampling(opts, imageViewWidth, imageViewHeight, isPortrait);
        Log.d(TAG, "updateImage sample size = " + sampleSize);

        // The sampling code is resilient now, but I'll keep this code around just in case.
        // It is better to have this to clamp down the sampling, and never actually call it. The
        // alternative is that the application crashes unexpectedly, which is never acceptable.
        if (sampleSize > 100 || sampleSize < 1) {
            // Something messed up, let's go with a safe, and small sample size for now
            // Ideally I should calculate this on the size of the bitmap and the size of the screen
            // and the available memory. But this will already cut down memory requirement by
            // 16x (1/4 scaling in each dimension, and two dimensions). So a user might rarely see
            // a tiny image, and then I can put more effort into calculating this correctly.
            sampleSize = 4;
        }
        // Sample each dimension by this number. So 4 means 1/4 of the image dimension for height
        // and 1/4 of the image dimension for width, resulting in 1/16 the memory usage.
        opts.inSampleSize = sampleSize;
        // Don't just decode the bounds, actually decode the Bitmap and return it.
        opts.inJustDecodeBounds = false;

        // Create the bitmap. If this line crashes, it might not even be out of memory! Decoding
        // a large Bitmap requires contiguous memory that is allocated by the system, and the system
        // might run out of contiguous memory. It is almost certainly a problem with a large file
        // being read without sampling any dimensions. So the entire Bitmap is being loaded into
        // memory after which the imageView has to do more work to actually fit the larger image
        // into the smaller display.
        Bitmap sourceBitmap = BitmapFactory.decodeFile(nextFile, opts);

        // Depending on the image orientation, rotate the bitmap for human-viewable display.
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                // Fall through
            case ExifInterface.ORIENTATION_UNDEFINED:
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                // Clockwise rotation by 90 degrees.
                if (sourceBitmap != null) {
                    sourceBitmap = getRotated(sourceBitmap, 90);
                }
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                // Clockwise rotation by 270 degrees, or an anticlockwise rotation by 90 degrees.
                if (sourceBitmap != null) {
                    sourceBitmap = getRotated(sourceBitmap, 270);
                }
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                // Clockwise (also anti-clockwise) rotation by 180 degrees.
                if (sourceBitmap != null) {
                    sourceBitmap = getRotated(sourceBitmap, 180);
                }
                break;
            default:
                Log.wtf(TAG, "Exif interface showed unsupported orientation " + orientation);
        }

        // This Bitmap has been rotated now, if required. We can just display it in the imageview
        // which will letterbox the sides or tops if required.
        final Bitmap bMap = sourceBitmap;

        // UI changes happen here, so post a runnable on a view to switch to the correct thread.
        mImageView.post(new Runnable() {
            @Override
            public void run() {
                mImageView.setImageBitmap(bMap);
                // Letterbox and put the image bang in the center. Scale to fit, if required.
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
        });
    }

    /**
     * Get a rotated image.
     * @param sourceBitmap The original bitmap to rotate.
     * @param degrees Degrees to rotate the original image
     * @return A rotated bitmap
     */
    private Bitmap getRotated(Bitmap sourceBitmap, int degrees) {
        // Width and height here pertains to the bitmap, not the view that holds it.
        int height = sourceBitmap.getHeight();
        int width = sourceBitmap.getWidth();

        // Create a matrix that will carry out the rotation operation.
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees, width / 2, height / 2);

        // Return the rotated bitmap.
        return Bitmap.createBitmap(sourceBitmap, 0, 0, width, height, matrix, true);
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

        if (rotate) {
            // Switch height and width for images that ARE rotated.
            height = options.outWidth;
            width = options.outHeight;
        } else {
            // Keep height and width as they are.
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

        if (inSampleSize < 1 || inSampleSize > 256) {
            // Something went wrong, let's print out how we got here.
            Log.d(TAG, "reqWidth = " + reqWidth + ", reqHeight = " + reqHeight
                    + ", opts.height=" + options.outHeight
                    + ", opts.width=" + options.outWidth
                    + ", inSampleSize = " + inSampleSize);
        }
        return inSampleSize;
    }

    /**
     * Sets the System Ui Visibility.  Only accepts two values: {@link #SYSUI_INVISIBLE} or
     *      * {@link #SYSUI_VISIBLE}
     *
     * @param visibility either {@link #SYSUI_INVISIBLE} or {@link #SYSUI_VISIBLE}
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

        // Unschedule any pending event to hide navigation if we are changing the visibility,
        // or making the UI visible.
        Handler h = mDrawer.getHandler();
        if (h != null) {
            if (changed || visible) {
                h.removeCallbacks(hideNav);
            } else {
                h.postDelayed(hideNav, 5000);
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

    /**
     * Create a new {@link UiController} but does not initialize it. To set the object for use,
     * call {@link #createController()} before calling any other methods.
     * @param mainActivity the activity this was created with
     * @param mainController the main orchestrator for all actions
     */
    UiController(MainActivity mainActivity, MainController mainController) {
        this.mMainActivity = mainActivity;
        this.mainController = mainController;
    }

    /**
     * Handler for window focus changes.
     * @param hasFocus true if this window has focus, false if it loses focus.
     */
    void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    /**
     * Creates a UI controller. All other method calls can only be made after calling this method.
     */
    void createController() {
        // Make the main view full screen, and listen for System UI visibility changes
        mDrawer = mMainActivity.findViewById(R.id.drawer_layout);
        mDrawer.setOnSystemUiVisibilityChangeListener(this);
        View.OnClickListener drawerToggler = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDrawer.isDrawerVisible(Gravity.START)) {
                    // Some item was clicked, so close the drawer.
                    mDrawer.closeDrawers();
                } else {
                    // The drawer isn't open, so this click is a request to open the drawer.
                    // This is the case when it is called from the FAB or the invisible button.
                    mDrawer.openDrawer(Gravity.START, true);
                    // While we are opening the drawer, show the system UI to allow the user to
                    // situate themselves.
                    showSystemUI();
                }
            }
        };

        // What follows are the fab and their corresponding invisible buttons (that are considerably
        // larger), and the handlers for both being tied to the same action.

        // A sizable rectangle along the top to toggle the drawer
        final FloatingActionButton fab = mMainActivity.findViewById(R.id.fab);
        fab.setOnClickListener(drawerToggler);
        mMainActivity.findViewById(R.id.drawer_button_invi).setOnClickListener(drawerToggler);
        showFab(fab);

        // Half the screen (the right half) for advancing in one direction, arbitrarily called
        // next.
        setClickListener(R.id.next_button_invi, UiConstants.NEXT);
        mNextFab = (FloatingActionButton) setClickListener(R.id.next, UiConstants.NEXT);
        if (mNextFab != null) {
            showFab(mNextFab);
        }

        // Half the screen (the left half) for advancing in one direction, arbitrarily called
        // previous.
        setClickListener(R.id.prev_button_invi, UiConstants.PREV);
        mPrevFab = (FloatingActionButton) setClickListener(R.id.prev, UiConstants.PREV);
        if (mPrevFab != null) {
            showFab(mPrevFab);
        }

        mToolbar = mMainActivity.findViewById(R.id.toolbar);
        mMainActivity.setSupportActionBar(mToolbar);

        // Hide the navigation after 7 seconds
        final Toolbar toolbar = mToolbar;
        final AppBarLayout bar = mMainActivity.findViewById(R.id.app_bar);
        Runnable hideBarAndToolbar = new Runnable() {
            @Override
            public void run() {
                bar.setVisibility(View.GONE);
                toolbar.setVisibility(View.GONE);
            }
        };
        mHandler.postDelayed(hideBarAndToolbar, 7000);

        mImageView = mMainActivity.findViewById(R.id.photoview);
        mImageView.setOnTouchListener(mDelegate);

        mDetector = new GestureDetectorCompat(mMainActivity, mGestureListener);

        // setDrawerListener() is deprecated, so let's throw this out, temporarily.
//        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
//                mMainActivity, mDrawer, mToolbar, R.string.navigation_drawer_open,
//                R.string.navigation_drawer_close);
//        mDrawer.setDrawerListener(toggle);
//        toggle.syncState();

        // Listen to our own Drawer element selection events.
        NavigationView navView = mMainActivity.findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this);
    }

    /**
     * Show the next image after a delay.
     */
    private final Runnable mShowNext = new Runnable() {
        @Override
        public void run() {
            mainController.updateImage(UiConstants.NEXT, false);
            // New image every 10 seconds.
            // TODO: Make this duration configurable.
            mHandler.postDelayed(this, 10000);
        }
    };

    /**
     * Sets the slideshow status, and returns the current status.
     *
     * This does not modify {@link #slideShowPlaying} so if the value is to be toggled, read
     * {@link #slideShowPlaying}, invert it, and write it at the end.
     *
     * @param slideShow true to start the show, false to stop
     * @param item possibly null, the MenuItem whose icon is to change to show current status
     * @return current status: true if started, false if stopped.
     */
    public boolean setSlideshow(boolean slideShow, MenuItem item) {
        if (slideShow) {
            // Start it in 300 ms from now.
            mHandler.postDelayed(mShowNext, 300);
        } else {
            mHandler.removeCallbacks(mShowNext);
        }
        if (item == null) {
            item = mMainActivity.findViewById(R.id.nav_slideshow);
        }
        item.setIcon(slideShowPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        return slideShow;
    }

    /**
     * Utility method to assign the click listener to a resource ID: R.id.something.
     * @param resourceId a valid R.id.X that we can expect non-null
     *      {@link android.app.Activity#findViewById(int)}
     * @param action either {@link UiConstants#NEXT} or {@link UiConstants#PREV} to assign.
     * @return the view that corresponds to the resourceId provided here.
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
