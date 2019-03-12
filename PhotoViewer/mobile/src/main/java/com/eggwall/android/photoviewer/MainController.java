package com.eggwall.android.photoviewer;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.eggwall.android.photoviewer.data.Album;

import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import static com.eggwall.android.photoviewer.AndroidRoutines.logDuringDev;
import static com.eggwall.android.photoviewer.Pref.Name.BEACON;

/**
 * Class that orchestrates the entire application. It has a {@link FileController}, a
 * {@link UiController}, and a {@link NetworkController} and orchestrates their behavior.
 *
 * All dependencies should be one-way: {@link MainController} can know about the other controllers
 * but they should only know about the {@link MainController} in order to call other functionality.
 * This separation should help reduce complexity in individual controllers, and also allow them
 * to do threading right: either stay in the background thread or in the main thread.
 */
class MainController {
    private static final String TAG = "MainController";
    private static final int ONE_HOUR = 3600 * 1000;

    /**
     * Has this object been properly created?
     *
     * This is needed because there are many critical objects that need to be created,
     * {@link #fileC}, {@link #uiC}, and these are relatively heavy-weight objects to be created.
     *
     * As a result, I mark the object not constructed, and check fo existence using
     * {@link #creationCheck()} in every public and private method before the method
     * does anything.
     */
    private boolean created = false;

    /** Object responsible for downloading files, and telling you what is available. */
    private FileController fileC = null;

    /** Object responsible for controlling the User Interface, refreshing them, etc. */
    private UiController uiC = null;

    /** Downloads files from the network and knows how to unzip them. */
    private NetworkController networkC;

    /** The preference object that the controllers can read and modify. */
    Pref pref;

    /**
     * A routine timer that executes every hour to do routine things: monitor stuck
     * downloads, check on a beacon (if any), clean up disk space. The user is generally
     * not aware of how frequently this timer runs, for now. Let's make a good choice
     * for the user, neither thrashing the device nor ignoring routine tasks.
     */
    private final Runnable timer = new Runnable() {
        @Override
        public void run() {
            timer();

            // And call ourselves again.
            // Set up the routine timer for every hour.
            new Handler().postDelayed(timer, ONE_HOUR);

        }
    };

    /**
     * Call a routine timer. This instructs all the remaining machinery to run any routine
     * tasks they might want to achieve. It is safe to call this on any thread, and as frequently
     * as required.
     */
    @AnyThread
    void timer() {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Go through every controller and see if they have any routine action they want to run.
        fileC.timer();
        uiC.timer();
        networkC.timer();
    }

    /**
     * Verify that the object was created before use.
     * Proceeds if created, and {@link AndroidRoutines#crashHard(String)} if the controller is used
     * before creation because it signifies a huge problem.
     */
    @AnyThread
    private void creationCheck() {
        if (created) {
            // Everything is ok. I was correctly created.
            return;
        }

        // Nothing is going to work correctly in this situation. Just say 'No'.
        AndroidRoutines.crashHard("MainController used before calling create()");
    }

    /**
     * Creates and initializes a {@link MainController}. Before this method is called, no other
     * public calls are valid.
     *
     * Can be called from any thread, though it is called from onCreate() usually.
     * @return true if a controller was created, false otherwise (should never happen in production)
     */
    @AnyThread
    boolean create(MainActivity mainActivity) {
        // Should NOT call creationCheck(), because this method creates the controller!
        AndroidRoutines.checkAnyThread();

        if (created) {
            // This is also a problem. The MainController object is being reused!

            // We expect random failures since there are callbacks with stale controllers.
            // Just say 'No'. Crash the process hard.
            AndroidRoutines.crashHard("MainController.create called twice!");
            return false;
        }

        // The order of creation of these objects should NOT matter
        fileC = new FileController(mainActivity, this);

        uiC = new UiController(mainActivity, this);
        uiC.createController();

        networkC = new NetworkController(mainActivity, this);

        // Set up the routine timer for every hour. It runs on the main thread.
        new Handler().postDelayed(timer, ONE_HOUR);

        // Get the preferences for the sole (un-named) process.
        pref = new Pref(mainActivity);

        // Now this object can be used.
        created = true;
        return true;
    }

    /**
     * Destroy the object and remove all references so objects can be Garbage Collected. I started
     * adding this code when memory allocation was an issue. Since then memory allocation has
     * been solved but I want to keep this because this makes the onDestroy() code much cleaner.
     */
    @AnyThread
    void destroy() {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Destroy the object permanently.
        fileC.destroy();
        fileC = null;

        uiC.destroy();
        uiC = null;

        networkC.destroy();
        networkC = null;
        
        created = false;
    }


    /**
     * Show the initial screen, load up the gallery list and show the first one.
     * @return true if it found an album to show
     */
    @WorkerThread
    boolean showInitial(Bundle icicle) {
        creationCheck();
        AndroidRoutines.checkBackgroundThread();

        // Ask the UiController to show the album list if it has any
        refreshAlbumList();

        Album initial = fileC.getInitial(icicle);
        if (initial != null) {
            // I have some album to show, and one that hopefully loads.
            if (!showAlbum(initial)) {
                Log.d(TAG, "Could not show initial album!", new Error());
            }
            uiC.loadInitial(icicle);
            return true;
        }

        // What can we do here, if the first album is null? Perhaps the splash screen instead?
        showSplash();
        // Didn't show any album.
        return false;
    }

    /**
     * Show a splash screen that explains what the program can do, and perhaps points the user
     * to a key they can import (my external key) with some album they can download (something not
     * private to me).
     *
     * Currently not implemented, but I can add to it.
     */
    @WorkerThread
    void showSplash() {
        creationCheck();
        AndroidRoutines.checkBackgroundThread();

        // Show a generic splash screen, does nothing right now.
        uiC.showIntroScreen();
    }

    /**
     * Returns a list of all albums that can be displayed.
     *
     * Can only be called on the background thread, since it reads disk.
     * @return a list of albums, possibly empty but never null.
     */
    @WorkerThread
    @NonNull List<Album> getAlbumList() {
        creationCheck();
        AndroidRoutines.checkBackgroundThread();

        return fileC.getAlbumList();
    }

    /**
     * Refresh the album list in the drawer and anywhere else that might need it.
     * Can be called on any thread.
     */
    @AnyThread
    void refreshAlbumList() {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Needs to be done in the background.
        if (AndroidRoutines.isMainThread()) {
            // Start a background thread to import the actual key.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    uiC.refreshAlbumList();
                }
            }).start();
        } else {
            uiC.refreshAlbumList();
        }
    }

    /**
     * Display this album if it exists, false if it doesn't.
     *
     * Call on the background thread, since this reads disk.
     * @param album An album to be displayed
     * @return true if the album was shown, false otherwise.
     */
    @WorkerThread
    boolean showAlbum(@NonNull Album album) {
        creationCheck();
        AndroidRoutines.checkBackgroundThread();

        return fileC.showAlbum(album);
    }

    /**
     * Allow window focus changes to be handled in an activity containing the UI.
     * @param hasFocus true when the window gets focus and false when it loses focus
     */
    @UiThread
    void onWindowFocusChanged(boolean hasFocus) {
        creationCheck();
        // I think the UI thread calls this, and we do UI modification, so let's enforce a UI thread
        AndroidRoutines.checkMainThread();

        uiC.onWindowFocusChanged(hasFocus);
    }

    /**
     * Make a diagnostic message
     *
     * Call from any thread.
     * @param message any human readable message (unfortunately not localized!)
     */
    @AnyThread
    void toast(String message) {
        creationCheck();
        AndroidRoutines.checkAnyThread();
        uiC.MakeText(message);
    }

    /**
     * Requests adding a URI as a gallery.
     *
     * Can be called on the foreground thread or a background thread.
     * @param album to add as a gallery
     */
    @AnyThread
    private void download(final NetworkRoutines.DownloadInfo album) {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Needs to be done in the background.
        if (AndroidRoutines.isMainThread()) {
            // Start a background thread to import the actual key.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    downloadBackgroundThread(album);
                }
            }).start();
        } else {
            downloadBackgroundThread(album);
        }
    }

    /**
     * Actually run the download in the background thread. This downloads from the information
     * provided here, that should have been picked up from the URL.
     * @param album information required to download the album.
     */
    private void downloadBackgroundThread(NetworkRoutines.DownloadInfo album) {
        FileController.Perm perm = fileC.checkConditionsForDownload(album);

        if (perm.hasError) {
            toast(perm.errorMessage);
            // We can't create an Unzipper with this degenerate object.
            return;
        }

        // Once a download is finished, we need to handle the file. The filecontroller handles
        // that via a new unzipper object.
        FileController.Unzipper unzipper = fileC.createUnzipper(perm);

        // Creating the unzipper changes album, so let's use the updated reference instead.
        // In practice, the only thing we need is the albumId, but let's pick it all up, and start
        // from scratch. Rewriting the input parameter forces all downstream code to only use the
        // updated reference.
        album = unzipper.dlInfo;
        boolean status = networkC.requestURI(unzipper);
        if (!status) {
            Log.e(TAG, "Could not download file " + album.location);
            return;
        }
        // We can't do anything else since we need to wait for the download to complete.
        Log.d(TAG, "Download for " + album.location + " queued.");
    }

    /**
     * Import the key provided here into the correct database.
     *
     * Call from any thread.
     * @param key a key to be imported into the database
     */
    @AnyThread
    private void importKey(final NetworkRoutines.KeyImportInfo key) {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        if (AndroidRoutines.isMainThread()) {
            // Start a background thread to import the actual key.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    importKeyBackgroundThread(key);
                }
            }).start();
        } else {
            // Background thread already, import away.
            importKeyBackgroundThread(key);
        }
    }

    /**
     * Import the key, assuming that we are in a background thread. Can only be called
     * from {@link #importKey(NetworkRoutines.KeyImportInfo)}
     * @param key key to be imported
     */
    private void importKeyBackgroundThread(NetworkRoutines.KeyImportInfo key) {
        fileC.importKey(key);
    }

    /**
     * Activity is going away, save any state here for future reads where the bundle will
     * be passed in {@link #showInitial(Bundle)}
     * @param icicle A bundle to save state in, possibly null
     */
    @UiThread
    void onSaveInstanceState(@NonNull Bundle icicle) {
        creationCheck();
        AndroidRoutines.checkMainThread();

        // Currently, the File controller and the UI controller are the only two who need this.
        fileC.onSaveInstanceState(icicle);
        uiC.onSaveInstanceState(icicle);
    }

    /**
     * Development only: Purge all databases. This is pretty intrusive and should never be allowed
     * during production.
     */
    @AnyThread
    void databasePurge() {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Enter a background thread and delete the databases
        if (AndroidRoutines.isMainThread()) {
            // Pop into a background thread: shouldn't do file handling from the main thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    fileC.databasePurge();
                }
            }).start();
        } else {
            fileC.databasePurge();
        }
    }

    /**
     * Show the next image in the direction indicated. You can show the next or the previous
     * image, and indicate whether the floating action buttons should be displayed (when a user
     * taps the screen) or continue to be hidden (when auto-advancing)
     *
     * Call from any thread.
     *
     * @param direction either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     * @param showFab True if the Floating Action Bar should be shown, false if it should be hidden.
     */
    @AnyThread
    void updateImage(final int direction, final boolean showFab) {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Since I can be called from any thread, I might need to run this code in a background
        // thread.
        if (AndroidRoutines.isMainThread()) {
            // Pop into a background thread: shouldn't do file handling from the main thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateImageBackgroundThread(direction, showFab);
                }
            }).start();
        } else {
            // Already background thread, carry on.
            updateImageBackgroundThread(direction, showFab);
        }
    }

    /**
     * Carry out the work of {@link #updateImage(int, boolean)}, but expect to be run from the
     * background thread. Can only be called from {@link #updateImage(int, boolean)}.
     * @param direction either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     * @param showFab True if the Floating Action Bar should be shown, false if it should be hidden.
     */
    private void updateImageBackgroundThread(final int direction, final boolean showFab) {
        if (direction != UiConstants.NEXT && direction != UiConstants.PREV) {
            AndroidRoutines.crashHard("updateImage: Incorrect direction: " + direction);
            return;
        }

        String nextFile = fileC.getFile(direction);
        Log.d(TAG, "updateImage: next file is: " + nextFile);

        // Now switch to a foreground thread to update the UI.
        uiC.updateImage(nextFile, direction, showFab);
    }

    /**
     * For a given URI, either as a custom URI or as input to {@link ImportActivity}, go through
     * the URI and handle the {@link NetworkRoutines#TYPE_DOWNLOAD} or
     * {@link NetworkRoutines#TYPE_DEV_CONTROL}, {@link NetworkRoutines#TYPE_DEV_CONTROL} actions.
     * @param in the URL to act upon, received either by clicking on a custom URI in a browser, or
     *           as a text input by the user in {@link ImportActivity}
     */
    @AnyThread
    void handleUri(@NonNull final Uri in) {
        creationCheck();
        AndroidRoutines.checkAnyThread();

        // Since I can be called from any thread, I might need to run this code in a background
        // thread.
        if (AndroidRoutines.isMainThread()) {
            // Pop into a background thread: shouldn't do file handling from the main thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    handleUriBackgroundThread(in);
                }
            }).start();
        } else {
            handleUriBackgroundThread(in);
        }
    }

    /**
     * For a given URI, either as a custom URI or as input to {@link ImportActivity}, go through
     * the URI and handle the {@link NetworkRoutines#TYPE_DOWNLOAD} or
     * {@link NetworkRoutines#TYPE_DEV_CONTROL}, {@link NetworkRoutines#TYPE_DEV_CONTROL} actions.
     * @param in the URL to act upon, received either by clicking on a custom URI in a browser, or
     *           as a text input by the user in {@link ImportActivity}
     */
    private void handleUriBackgroundThread(@NonNull Uri in) {
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
                    download(album);
                }
                break;
            case NetworkRoutines.TYPE_SECRET_KEY:
                NetworkRoutines.KeyImportInfo key = NetworkRoutines.getKeyInfo(in);
                if (key != NetworkRoutines.EMPTY_KEY) {
                    Log.d(TAG, "I'm going to import this key now: " + key);
                    // Now download that URL and switch over to that screen.
                    importKey(key);
                }
                break;
            case NetworkRoutines.TYPE_MONITOR:
                // Get the URL, then write it to Settings.
                String beacon = NetworkRoutines.getMonitorUri(in);
                if (beacon.length() > 0) {
                    // TODO: Act upon this URI by unpacking it, downloading the
                    // content, and then adding it to preferences if required.
                    // Some URL needs to be monitored, let's remember it.
                    if (0 != pref.getString(BEACON).compareTo(beacon)) {
                        // It differs, write the new value to disk.
                        pref.modify(BEACON, beacon);
                    }
                }
                break;
            case NetworkRoutines.TYPE_DEV_CONTROL:
                NetworkRoutines.callControl(in, this);
                break;
            default:
                // Should never happen since getIntentType only gives known values.
                Log.wtf(TAG, "Unknown URI: " + in);
                break;
        }
    }
}
