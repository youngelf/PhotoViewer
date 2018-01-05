package com.eggwall.android.photoviewer;

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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import static android.view.View.INVISIBLE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.VISIBLE;

/* While this class is fine, some cleanup can be done here.
 *** Remove the gesture listening code now that I have onscreen buttons.
 *** Make the Action bar play well with the System UI.  Right now they are disconnected.
 *** Avoid showing the System UI every time, it can get laborious.
 *** Hide all the elements (all fabs, and all navigation) on the same runnable.
 *** Show a progress indicator for the gallery.
*/

/**
 * Orchestrates User Interface actions, and drives the display.
 */
class UiController implements NavigationView.OnNavigationItemSelectedListener,
        View.OnSystemUiVisibilityChangeListener {

    private static final String TAG = "UiController";

    private int mSysUiBaseVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private static final int SYSUI_INVISIBLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final int SYSUI_VISIBLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    // All the images we will display
    private static final int[] DRAWABLES = {
            R.drawable.ic_menu_camera,
            R.drawable.ic_menu_gallery,
            R.drawable.ic_menu_manage,
            R.drawable.ic_menu_send,
            R.drawable.ic_menu_share,
            R.drawable.ic_menu_slideshow};

    private final Runnable hideSysUi = new Runnable() {
        @Override
        public void run() {
            setSystemUiVisibility(SYSUI_INVISIBLE);
        }
    };

    private final Runnable hideNav = new Runnable() {
        @Override
        public void run() {
            setNavVisibility(false);
        }
    };

    private final MainActivity mMainActivity;
    private final Handler mHandler = new Handler();
    private final GestureDetector.OnGestureListener mGestureListener = new FlingDetector();

    // References to on-screen elements
    private FloatingActionButton mNextFab;
    private FloatingActionButton mPrevFab;
    private AppCompatImageView mImageView;
    private Toolbar mToolbar;
    private DrawerLayout mDrawer;

    private int mLastSystemUiVis = 0;
    private GestureDetectorCompat mDetector;
    private int mCurrentDrawable = 0;

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
                updateImage(UiConstants.PREV);
            } else if (v > 2000) {
                Log.d("MainActivity", "NEXT image");
                updateImage(UiConstants.NEXT);
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

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) mMainActivity.findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Shows a Floating Action Button (FAB) immediately, and then fades it out in a few seconds.
     *
     * @param fab
     */
    private void showFab(final View fab) {
        // Show it
        fab.animate().alpha(255).setDuration(750).start();

        Runnable fadeAway = new Runnable() {
            @Override
            public void run() {
                fab.animate().alpha(0).setDuration(1500).start();
            }
        };
        // Hide in in three seconds from now.
        mHandler.postDelayed(fadeAway, 3000);
    }

    /**
     * Update the image by providing an offset
     *
     * @param offset is either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     */
    private void updateImage(int offset) {
        if (offset != UiConstants.NEXT && offset != UiConstants.PREV) {
            Log.e(TAG, "updateImage: Incorrect offset provided: " + offset);
            System.exit(-1);
            return;
        }
        mCurrentDrawable += offset;
        if (mCurrentDrawable < 0) {
            mCurrentDrawable = DRAWABLES.length - 1;
        }
        if (mCurrentDrawable >= DRAWABLES.length) {
            mCurrentDrawable = 0;
        }
        mImageView.setImageResource(DRAWABLES[mCurrentDrawable]);

        showSystemUI();
        // Show the correct FAB, and hide it after a while
        if (offset == UiConstants.NEXT) {
            showFab(mNextFab);
        }
        if (offset == UiConstants.PREV) {
            showFab(mPrevFab);
        }
    }

    void setBaseSystemUiVisibility(int visibility) {
        mSysUiBaseVisibility = visibility;
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

    private void setNavVisibility(boolean visible) {
        int newVis = mSysUiBaseVisibility;
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

    UiController(MainActivity mainActivity) {
        this.mMainActivity = mainActivity;
    }

    void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            hideSystemUI();
        }
    }

    void createController() {
        // Make the main view full screen, and listen for System UI visibility changes
        mDrawer = (DrawerLayout) mMainActivity.findViewById(R.id.drawer_layout);
        mDrawer.setOnSystemUiVisibilityChangeListener(this);

        final AppBarLayout bar = (AppBarLayout) mMainActivity.findViewById(R.id.app_bar);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bar.setVisibility(INVISIBLE);
            }
        };


        final FloatingActionButton fab = (FloatingActionButton) mMainActivity.findViewById(R.id.fab);
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

        mMainActivity.findViewById(R.id.next);

        setClickListener(R.id.next_button_invi, UiConstants.NEXT);
        mNextFab = (FloatingActionButton) setClickListener(R.id.next, UiConstants.NEXT);
        showFab(mNextFab);

        setClickListener(R.id.prev_button_invi, UiConstants.PREV);
        mPrevFab = (FloatingActionButton) setClickListener(R.id.prev, UiConstants.PREV);
        showFab(mPrevFab);

        mToolbar = (Toolbar) mMainActivity.findViewById(R.id.toolbar);
        mMainActivity.setSupportActionBar(mToolbar);

        mImageView = (AppCompatImageView) mMainActivity.findViewById(R.id.photoview);
        mImageView.setOnTouchListener(mDelegate);
        updateImage(UiConstants.NEXT);

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
        mHandler.postDelayed(r, 7000);
    }

    /**
     *
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
                    updateImage(action);
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
