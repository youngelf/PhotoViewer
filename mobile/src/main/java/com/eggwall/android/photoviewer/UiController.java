package com.eggwall.android.photoviewer;

import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import static android.view.View.INVISIBLE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.VISIBLE;

/**
 * Orchestrates User Interface actions, and drives the display.
 */

public class UiController {
    private final MainActivity mainActivity;
    public static final String TAG = "UiController";

    public static final int NEXT = 1;
    public static final int PREV = -1;
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


    // View animation methods

    /**
     * Shows a Floating Action Button (FAB) immediately, and then fades it out in a few seconds.
     *
     * @param fab
     */
    private void showFab(final View fab) {
        // Show it NOW
        fab.animate().alpha(255).setDuration(750).start();

        Runnable doFade = new Runnable() {
            @Override
            public void run() {
                fab.animate().alpha(0).setDuration(1500).start();
            }
        };
        // Hide in in three seconds from now.
        h.postDelayed(doFade, 3000);
    }

    int mBaseSystemUiVisibility = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | SYSTEM_UI_FLAG_LAYOUT_STABLE;

    /**
     * Update the image by providing an offset
     *
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

        showSystemUI();
        // Show the correct FAB, and hide it after a while
        if (offset == NEXT) {
            showFab(fNext);
        }
        if (offset == PREV) {
            showFab(fPrev);
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


    public static final int SYSUI_INVISIBLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    public static final int SYSUI_VISIBLE = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;


    public final Runnable hideSysUi = new Runnable() {
        @Override
        public void run() {
            setSystemUiVisibility(SYSUI_INVISIBLE);
        }
    };

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
        mainActivity.getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    /**
     * Hide the system UI.
     */
    private void hideSystemUI() {
        mainActivity.getWindow().getDecorView().setSystemUiVisibility(SYSUI_INVISIBLE);
    }
    DrawerLayout mDrawer;

    /**
     * Show the system UI.
     */
    private void showSystemUI() {
        mainActivity.getWindow().getDecorView().setSystemUiVisibility(SYSUI_VISIBLE);
        // And request it to be hidden in five seconds
        h.removeCallbacks(hideSysUi);
        h.postDelayed(hideSysUi, 5000);
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


    FloatingActionButton fNext;
    FloatingActionButton fPrev;
    AppCompatImageView image;


    Handler h = new Handler();

    private GestureDetector.OnGestureListener mGestureListener = new FlingDetector();

    public UiController (MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void createController () {
        // Make the main view full screen, and listen for System UI visibility changes
        mDrawer = (DrawerLayout) mainActivity.findViewById(R.id.drawer_layout);
        mDrawer.setOnSystemUiVisibilityChangeListener(mainActivity);


        final AppBarLayout bar = (AppBarLayout) mainActivity.findViewById(R.id.app_bar);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bar.setVisibility(INVISIBLE);
            }
        };


        final FloatingActionButton fab = (FloatingActionButton) mainActivity.findViewById(R.id.fab);
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

        fNext = (FloatingActionButton) mainActivity.findViewById(R.id.next);
        fNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImage(NEXT);
            }
        });
        mainActivity.findViewById(R.id.next_button_invi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImage(NEXT);
            }
        });


        showFab(fNext);
        fPrev = (FloatingActionButton) mainActivity.findViewById(R.id.prev);
        fPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImage(PREV);
            }
        });
        showFab(fPrev);

        mainActivity.findViewById(R.id.prev_button_invi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateImage(PREV);
            }
        });

        // Hide the navigation after 7 seconds
        h.postDelayed(r, 7000);

        mToolbar = (Toolbar) mainActivity.findViewById(R.id.toolbar);
        mainActivity.setSupportActionBar(mToolbar);


        image = (AppCompatImageView) mainActivity.findViewById(R.id.photoview);
        image.setOnTouchListener(mDelegate);
        updateImage(NEXT);

        mDetector = new GestureDetectorCompat(mainActivity, mGestureListener);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                mainActivity, mDrawer, mToolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.setDrawerListener(toggle);
        toggle.syncState();

    }

    Toolbar mToolbar;

    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            hideSystemUI();
        }
    }


    int mLastSystemUiVis = 0;
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
