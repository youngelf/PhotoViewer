package com.eggwall.android.photoviewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.eggwall.android.photoviewer.data.Album;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static com.eggwall.android.photoviewer.Pref.Name.SLIDESHOW_DELAY;

/* While this class is fine, some cleanup can be done here.
 *** Remove the gesture listening code now that I have onscreen buttons.
 *** Make the Action bar play well with the System UI.  Right now they are disconnected.
 *** Hide all the elements (all fabs, and all navigation) on the same runnable.
 *** Show a progress indicator for the gallery.
 *
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
    private MainActivity mMainActivity;

    /** The orchestrator that will show next image or previous image based on button presses. */
    private MainController mainController;

    private final Handler mHandler = new Handler();

    // Required for handling swipe gestures.
    private final GestureDetector.OnGestureListener mGestureListener = new FlingDetector();

    // References to on-screen elements
    private FloatingActionButton mNextFab;
    private FloatingActionButton mPrevFab;
    private AppCompatImageView mImageView;
    private DrawerLayout mDrawer;

    /**
     * This tells you if we are showing an introduction (true) or if we are showing images
     * (false).
     */
    private boolean showingIntroduction = false;

    /**
     * The bitmap we will populate in the future with next/previous image. This is never shown on
     * the screen, but is passed to {@link BitmapFactory} for it to allocate space.
     *
     * I am doing this because my images are large: 24MB Bitmap byte arrays even after sampling.
     * Even if the RAM exists, there is a huge risk of fragmentation as the previous array is
     * not deleted fast enough and the new one is offset. As a result, I am keeping two Bitmap
     * objects in memory: the one that was previously used and the {@link #current} Bitmap object.
     * When creating the {@link #current} Bitmap, I ask {@link BitmapFactory} to reuse this
     * object, if possible, and then that is assigned to {@link #current}
     */
    private Bitmap future;

    /** The bitmap we are currently displaying */
    private Bitmap current;

    /** True if the slide show is currently on auto-play mode. */
    private boolean slideShowPlaying = false;

    private int mLastSystemUiVis = 0;
    private GestureDetectorCompat mDetector;

    /** A map of menu ID elements to Album ID elements */
    private final SparseArray<Album> menuIdToAlbum = new SparseArray<>();

    /**
     * Show a diagnostic message.
     *
     * Call from any thread.
     * @param message any human readable message (unfortunately not localized!)
     */
    void MakeText(final String message) {
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
        // Also show the message in the logs
        Log.w("MakeText", message, new Error());
    }

    /**
     * Save any instance state that might be needed later.
     * @param icicle guaranteed non-null
     */
    void onSaveInstanceState(@NonNull  Bundle icicle) {
        icicle.putBoolean(SS_AUTOPLAY, slideShowPlaying);
    }

    /**
     * Load up any instance state saved earlier.
     * @param icicle perhaps null
     */
    void loadInitial(Bundle icicle) {
        if (icicle == null) {
            // This method can only work if the icicle exists.
            return;
        }

        boolean startSlideshow = icicle.getBoolean(SS_AUTOPLAY, false);
        MenuItem item = mMainActivity.findViewById(R.id.nav_slideshow);
        if (item != null) {
            setSlideshow(startSlideshow, item);
            dismissIntroScreen();
        }

    }

    /**
     * Adds albums to the drawer. This needs to be called from the background thread.
     */
    void refreshAlbumList() {
        // Get the top-level menu, or return early if we can't find one.
        NavigationView navView = mMainActivity.findViewById(R.id.nav_view);
        if (navView == null) {
            return;
        }

        final Menu drawerMenu = navView.getMenu();
        if (drawerMenu == null) {
            return;
        }
        Log.d(TAG, "refreshAlbumList called.");

        // Get a full list of all albums, this is in no particular sort order right now.
        final List<Album> albumList = mainController.getAlbumList();

        // Update the UI on the main thread. Surprisingly, that is not working, and modifying
        // this in the background works great!? Unexpected.
        // TODO: Make this run on the main thread, or test this on other devices to see what
        // the impact here is.

        // With more testing, this works on Android devices, and failed on the Fire device. Sounds
        // like a bug with Fire devices that I should just ignore.
        Runnable refreshAlbums = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Runnable called");
                // If there are existing elements we added, remove them first
                for (int i = 0, size = menuIdToAlbum.size(); i < size; i++) {
                    int itemId = menuIdToAlbum.keyAt(i);
                    Log.d(TAG, "Removed element with ID: " + itemId);
                    drawerMenu.removeItem(itemId);
                }
                // All elements removed, so clear the map
                menuIdToAlbum.clear();

                // Now add an element for every new album we found in the list. We could get
                // fancy and diff the two lists but it is much simpler, and cleaner to throw
                // out the previous list and add the entire new list.

                // This is arbitrary, we could start anywhere, but hopefully 1001 is a high number
                // that it doesn't conflict with any existing menu items.
                int itemId = 1001;
                for (Album album : albumList) {
                    // Ensure that the item ID doesn't exist. If it does, just increment it till
                    // we find something that doesn't conflict. It is important for the item IDs to
                    // be unique, as they tell us which element was clicked on.
                    while (drawerMenu.findItem(itemId) != null) {
                        itemId++;
                    }
                    MenuItem m = drawerMenu.add(1, itemId, 1, album.getName());
                    menuIdToAlbum.put(itemId, album);
                    Log.d(TAG, "Adding menu with item id = " + itemId
                            + " and Album = " + album.toString());

                    // TODO: Add last used-time, and sort by recency...?
                    if (m != null) {
                        m.setIcon(R.drawable.ic_menu_gallery);
                    }
                    itemId++;
                }
            }
        };
        mDrawer.post(refreshAlbums);
        // If that doesn't work, then refreshAlbums.run() will do the job in the current thread,
        // though it is a bad idea to modify the UI on a background thread since the Android
        // UI code is not thread safe.
    }

    /**
     * Show a splash screen to introduce how to use the program.
     *
     * Needs to be called from the main thread since it modifies UI.
     */
    void showIntroScreen() {
        if (showingIntroduction) {
            // Nothing to do.
            return;
        }

        TextView splashView = mMainActivity.findViewById(R.id.splash_info);
        splashView.setVisibility(View.VISIBLE);
        // Linkify the URL in there
        Pattern linkText = Pattern.compile("my website");
        // TODO My test URL for now, publish both the key and a sample gallery externally
        String url = "http://192.168.11.122/test.html";
        Linkify.TransformFilter removeTrailing = new Linkify.TransformFilter() {
            @Override
            public String transformUrl(Matcher match, String url) {
                return "";
            }
        };
        Linkify.addLinks(splashView, linkText, url, null, removeTrailing);

        // Hide all the photo related buttons
        mMainActivity.findViewById(R.id.photoview).setVisibility(View.GONE);

        // This messes with tapping on the links.
        mMainActivity.findViewById(R.id.drawer_button_invi).setVisibility(View.GONE);
        mMainActivity.findViewById(R.id.next_button_invi).setVisibility(View.GONE);
        mMainActivity.findViewById(R.id.prev_button_invi).setVisibility(View.GONE);
        // TODO: Make this invisible when an album is shown.

        // Remove all callbacks to the runnable
        mHandler.removeCallbacks(mShowNext);

        // Remove the fling detector so it doesn't accidentally want to show next/previous
        mImageView.setOnTouchListener(null);


        showingIntroduction = true;
    }

    /**
     * Hide the splash screen and show photos. This is called when we show photos finally, so
     * no external consumer needs to call it.
     *
     * Needs to be called from the main thread, since it modifies UI.
     */
    private void dismissIntroScreen() {
        if (!showingIntroduction) {
            // Nothing to do
            return;
        }
        mMainActivity.findViewById(R.id.splash_info).setVisibility(View.VISIBLE);

        mMainActivity.findViewById(R.id.drawer_button_invi).setVisibility(View.VISIBLE);
        mMainActivity.findViewById(R.id.next_button_invi).setVisibility(View.VISIBLE);
        mMainActivity.findViewById(R.id.prev_button_invi).setVisibility(View.VISIBLE);

        mMainActivity.findViewById(R.id.photoview).setVisibility(View.GONE);

        // Instantiate the fling listener again.
        mImageView.setOnTouchListener(flingListener);

        // Instantiate the runnable here perhaps?

        showingIntroduction = false;
    }

    /**
     * Run routine tasks. This does nothing right now.
     */
    void timer() {
        // TODO: Show that routine tasks are taking place as an Easter egg.
        MakeText("Thanks for keeping me on so long!");
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
            case R.id.nav_slideshow:
                // Toggle the current state.
                boolean newState = !slideShowPlaying;
                setSlideshow(newState, item);
                break;

            case R.id.nav_import:
                // Import a new gallery.
                // Call an activity that parses the string, and fires the appropriate intent.
                Intent importActivity = new Intent(Intent.ACTION_VIEW);
                importActivity.setClass(mMainActivity, ImportActivity.class);

                // Ask the Import Activity to import a download URI.
                mMainActivity.startActivityForResult(importActivity,
                        ImportActivity.REQUEST_DOWNLOAD);

                // The result comes in MainActivity.onActivityResult();
                break;

            case R.id.nav_manage:
                // Display settings, allow the user to change them.
                Intent settingActivity = new Intent(Intent.ACTION_VIEW);
                settingActivity.setClass(mMainActivity, SettingActivity.class);

                // Ask the Import Activity to import a download URI.
                mMainActivity.startActivityForResult(settingActivity,
                        SettingActivity.REQUEST_SETTINGS);
                // The result comes in MainActivity.onActivityResult();
                break;

            case R.id.nav_timer:
                // Development-only, call the timer.
                mainController.timer();
                break;

            default:
                // This must be an album instead. Get its id, and ask the filecontroller
                // to display this album.
                int id = item.getItemId();
                Log.d(TAG, "Looking for menu with item id =" + id);
                final Album album = menuIdToAlbum.get(id);
                if (album == null) {
                    mainController.toast("Got null album!");
                } else {
                    // Run the showAlbum in the background since it processes disk.
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            mainController.showAlbum(album);
                        }
                    }).start();
                }
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
        // Dismiss the intro screen, if necessary
        dismissIntroScreen();

        BitmapFactory.Options opts = new BitmapFactory.Options();

        // Just calculate how big the file is to learn the sizes
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(nextFile, opts);

        ExifInterface exif;
        try {
            exif = new ExifInterface(nextFile);
        } catch (IOException e) {
            String message = "Failed to open file: " + nextFile;
            mainController.toast(message);
            AndroidRoutines.crashDuringDev(message + e.toString());
            return;
        }

        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);

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

        // We will use the future Bitmap as a hint to BitmapFactory, for it to reuse the byte[]
        // object from it. Then we allocate that to current. If we don't save a reference to the
        // current Bitmap, we will lose it. Here, we hold on to a reference, and once the future
        // object's byte[] has been reused, this can be assigned to future.
        // As a result of that, we hold this object for the next allocation and never see any
        // visual artifacts like rotations or decompression artifacts.
        Bitmap oldReference = current;

        // Sample each dimension by this number. So 4 means 1/4 of the image dimension for height
        // and 1/4 of the image dimension for width, resulting in 1/16 the memory usage.
        opts.inSampleSize = sampleSize;
        // Don't just decode the bounds, actually decode the Bitmap and return it.
        opts.inJustDecodeBounds = false;

        // Don't allocate another byte[] reference if one exists. Use the one from the future.
        // If the future Bitmap is a null, this does nothing so it is safe
        // Our past is our future.
        opts.inBitmap = future;

        // Create the bitmap. If this line crashes, it might not even be out of memory! Decoding
        // a large Bitmap requires contiguous memory that is allocated by the system, and the system
        // might run out of contiguous memory. It is almost certainly a problem with a large file
        // being read without sampling any dimensions. So the entire Bitmap is being loaded into
        // memory after which the imageView has to do more work to actually fit the larger image
        // into the smaller display.
        current = BitmapFactory.decodeFile(nextFile, opts);
        if (current == null) {
            // Try to use more memory. Ignore the previous memory, and allocate a fresh new
            // space. In practice, this should be fine, since we will possibly do two large
            // allocations. This is required on wide or tall screens where there is a lot of
            // letter-boxing and so some images are shown at a very high sampling rate, and others
            // won't fit with a lower degree of sampling.
            opts.inBitmap = null;
            current = BitmapFactory.decodeFile(nextFile, opts);
            if (current != null) {
                Log.d(TAG, "Fixed on the second try: using more memory!");
            } else {
                // Still failed. Try increasing the scaling factor and see if that fixes
                // the problem.
                opts.inSampleSize *= 2;
                current = BitmapFactory.decodeFile(nextFile, opts);

                if (current != null) {
                    Log.d(TAG, "Fixed on the third try: lower quality / sampling!");
                }
            }
        }

        // Depending on the image orientation, rotate the bitmap for human-viewable display.
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                // Fall through
            case ExifInterface.ORIENTATION_UNDEFINED:
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                // Clockwise rotation by 90 degrees.
                if (current != null) {
                    current = getRotated(current, 90);
                }
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                // Clockwise rotation by 270 degrees, or an anticlockwise rotation by 90 degrees.
                if (current != null) {
                    current = getRotated(current, 270);
                }
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                // Clockwise (also anti-clockwise) rotation by 180 degrees.
                if (current != null) {
                    current = getRotated(current, 180);
                }
                break;
            default:
                Log.wtf(TAG, "Exif interface showed unsupported orientation " + orientation);
        }

        // This Bitmap has been rotated now, if required. We can just display it in the imageview
        // which will letterbox the sides or tops if required.
        final Bitmap bMap = current;

        // This byte[] array has to be reused later, so let's remember it. If the previous
        // bitmap array was too small, then it is forgotten, clearing future
        future = oldReference;

        // UI changes happen here, so post a runnable on a view to switch to the correct thread.
        mImageView.post(new Runnable() {
            @Override
            public void run() {
                // This is the bitmap to use.
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
        // End of updateImage, the runnable above runs on the main thread and nothing more here.
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
        // Divide by 2 is the same as left shift by one, since these are integers. Keeps the
        // Android Studio UI quiet as well, because it thinks that division by 2 is somehow
        // wrong for integers.
        matrix.postRotate(degrees, width >> 1, height >> 1);

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
        if (mMainActivity != null) {
            Window window = mMainActivity.getWindow();
            if (window != null) {
                window.getDecorView().setSystemUiVisibility(visibility);
            }
        }
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
        // We want to hide the system UI.
        int newVis = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!visible) {
            newVis |= SYSTEM_UI_FLAG_LOW_PROFILE | SYSTEM_UI_FLAG_FULLSCREEN;
        }
        final boolean changed = newVis == mDrawer.getSystemUiVisibility();

        // Un-schedule any pending event to hide navigation if we are changing the visibility,
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
    }

    /**
     * Listens to touch events to fire next/previous on flings, and also to show the System UI
     * when a touch event happens.
     */
    private View.OnTouchListener flingListener = new View.OnTouchListener() {
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
    UiController(@NonNull MainActivity mainActivity, @NonNull MainController mainController) {
        this.mMainActivity = mainActivity;
        this.mainController = mainController;
    }

    /** Remove all references from this object, to allow them to be garbage collected. */
    void destroy() {
        mHandler.removeCallbacks(mShowNext);
        mMainActivity = null;
        mainController = null;
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
                if (mDrawer.isDrawerVisible(GravityCompat.START)) {
                    // Some item was clicked, so close the drawer.
                    mDrawer.closeDrawers();
                } else {
                    // The drawer isn't open, so this click is a request to open the drawer.
                    // This is the case when it is called from the FAB or the invisible button.
                    mDrawer.openDrawer(GravityCompat.START, true);
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
        View.OnClickListener next_handler = new View.OnClickListener() {
            @Override
            public void onClick(View ignore) {
                mainController.updateImage(UiConstants.NEXT, true);
            }
        };
        // Now assign it as a handler to these elements
        setClickListener(R.id.next_button_invi, next_handler);
        mNextFab = (FloatingActionButton) setClickListener(R.id.next, next_handler);
        if (mNextFab != null) {
            showFab(mNextFab);
        }

        // Half the screen (the left half) for advancing in one direction, arbitrarily called
        // previous.
        View.OnClickListener prev_handler = new View.OnClickListener() {
            @Override
            public void onClick(View ignore) {
                mainController.updateImage(UiConstants.PREV, true);
            }
        };
        // Now assign it as a handler to these elements
        setClickListener(R.id.prev_button_invi, prev_handler);
        mPrevFab = (FloatingActionButton) setClickListener(R.id.prev, prev_handler);
        if (mPrevFab != null) {
            showFab(mPrevFab);
        }

        mImageView = mMainActivity.findViewById(R.id.photoview);
        mImageView.setOnTouchListener(flingListener);

        mDetector = new GestureDetectorCompat(mMainActivity, mGestureListener);

        // Listen to our own Drawer element selection events.
        NavigationView navView = mMainActivity.findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this);

        // Experiment with removing the Navigation Bar and Status Bar.

    }

    /**
     * Show the next image after a delay.
     */
    private final Runnable mShowNext = new Runnable() {
        @Override
        public void run() {
            mainController.updateImage(UiConstants.NEXT, false);
            // New image every few seconds.
            int delay = mainController.pref.getInt(SLIDESHOW_DELAY);

            // The runnable accepts delays in milliseconds, so multiply by 1,000
            mHandler.postDelayed(this, delay * 1000);
        }
    };

    /**
     * Sets the slideshow status, and returns the current status.
     *
     * Only this can modify {@link #slideShowPlaying} so if the value is to be toggled, read
     * {@link #slideShowPlaying}, invert it, and pass it in, and <b>don't</b> modify the
     * value of {@link #slideShowPlaying}.
     *
     * @param slideShow true to start the show, false to stop
     * @param item possibly null, the MenuItem whose icon is to change to show current status
     */
    private void setSlideshow(boolean slideShow, MenuItem item) {
        // Only this method allowed to set slideShowPlaying.
        slideShowPlaying = slideShow;

        if (slideShowPlaying) {
            // Start it in 300 ms from now.
            mHandler.postDelayed(mShowNext, 300);
        } else {
            mHandler.removeCallbacks(mShowNext);
        }
        if (item == null) {
            item = mMainActivity.findViewById(R.id.nav_slideshow);
        }
        // Non-intuitive. If something is playing, then we want to show the PAUSE button, but
        // if something is paused, then we want to show the play button!
        item.setIcon(slideShowPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    /**
     * Utility method to assign the click listener to a resource ID: R.id.something.
     * @param resourceId a valid R.id.X that we can expect non-null
     *      {@link android.app.Activity#findViewById(int)}
     * @param action a click handler to assign.
     * @return the view that corresponds to the resourceId provided here.
     */
    private View setClickListener(int resourceId, final View.OnClickListener action) {
        View v = mMainActivity.findViewById(resourceId);
        if (v != null) {
            v.setOnClickListener(action);
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
