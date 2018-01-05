package com.eggwall.android.photoviewer;

import android.os.Bundle;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

/**
 * TODO: Implement a broadcast receiver when a zip is downloaded.
 * TODO: Unzip a file.
 * TODO: Allow traversing existing file structure.
 * TODO: Delete oldest file: LRU cache.
 * TODO: Taps on different parts of the screen lead to different actions: top for showing nav again,
 * TODO: Onscreen buttons, remove with alpha transparency
 * TODO: Make fullscreen better: allow tap on the screen to turn off full-screen.
 * TODO: Fit and finish: animations all over the place.
 */

/**
 * Create a Photo viewer by default.  This screen should show an image by default, and allow the
 * user to change them using the navigation bar.
 */


public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        View.OnSystemUiVisibilityChangeListener {

    boolean keepScreenOn = true;

    public static final String TAG = "MainActivity";

    UiController uiController;
    NetworkController networkController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        uiController = new UiController(this);
        uiController.createController();

        networkController = new NetworkController(this);
        boolean testing = true;
        // Confirmed working, so I'm removing it right now to avoid making spurious downloads.
        if (testing) {
            networkController.requestURI(NetworkController.location);
        }

        FileController f = new FileController();
        f.getPicturesList();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }


    public void onSystemUiVisibilityChange(int visibility) {
        uiController.onSystemUiVisibilityChange(visibility);
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
        uiController.onWindowFocusChanged(hasFocus);
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
