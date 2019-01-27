package com.eggwall.android.photoviewer;

//
// Non-javadoc, for remembering why I am adding this class.
// Right now we have three existing Controllers: UI, Network and File, and none of them seem
// like the right place to put overall logic. Clearly it doesn't make sense for the FileController
// to know about the UI controller, or the Network Controller to know about the UI since they have
// relatively straight-forward roles. This means that some other object has to put their combined
// functionality together.

// I'll start out extracting code from {@link MainActivity} because that has some of this
// orchestration logic, and hopefully this class doesn't grow in complexity.

// Ideally, the interfaces that the others provide are crisp enough that we can understand the
// functionality here on its own merit.

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import com.eggwall.android.photoviewer.data.Album;

import java.util.ArrayList;

/**
 * Class that orchestrates the entire application. It has a {@link FileController}, a
 * {@link UiController}, and a {@link NetworkController} and orchestrates their behavior.
 */
public class MainController {
    private static final String TAG = "MainController";

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

    /**
     * Checks if the current thread is the main thread or not.
     * @return
     */
    public static void checkMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // This is the main thread. Do nothing.
            return;
        }
        Log.w(TAG, "Error. NOT main thread!", new Error());
    }

    /**
     * Checks if the current thread is the main thread or not.
     * @return
     */
    public static boolean isMainThread() {
        return (Looper.myLooper() == Looper.getMainLooper());
    }

    /**
     * Confirm the current thread is NOT the main thread or not.
     * @return
     */
    public static void checkBackgroundThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // This is the main thread. Do nothing.
            return;
        }
        Log.w(TAG, "Error. NOT background thread!", new Error());
    }

    /**
     * Confirm that any thread is great.  Confirms that neither {@link #checkBackgroundThread()}
     * nor {@link #checkMainThread()} are needed here.
     */
    public static void checkAnyThread() {
        // This is more for the programmer to read than for anything to be confirmed. The existence
        // of this method confirms that the programmer confirms that any thread is good, and that
        // he/she didn't forget to call checkBackgroundThread() or checkMainThreads() by mistake.
        return;
    }

    /**
     * Utility method to print an error, and crash hard.
     */
    public static void crashHard(String message) {
        Log.wtf(TAG, message, new Error());
        System.exit(-1);
        throw new RuntimeException();
    }

    /**
     * Verify that the object was created before use.
     * @return false if created, true if everything is ok.
     */
    private void creationCheck() {
        if (created) {
            // Everything is ok. I was correctly created.
            return;
        }

        // Nothing is going to work correctly in this situation. Just say 'No'.
        crashHard("MainController used before calling create()");
    }

    /**
     * Creates and initializes a {@link MainController}. Before this method is called, no other
     * public calls are valid.
     *
     * Can be called from any thread, though it is called from onCreate() usually.
     * @return
     */
    boolean create(MainActivity mainActivity) {
        // Should NOT call creationCheck(), because this method creates!
        checkAnyThread();

        if (created) {
            // This is also a problem. The MainController object is being reused!

            // We expect random failures since there are callbacks with stale controllers.
            // Just say 'No'. There is some confusion whether this closes the process.
            crashHard("MainController.create called twice!");
            return false;
        }

        // The order of creation of these objects should NOT matter
        fileC = new FileController(mainActivity, this);

        uiC = new UiController(mainActivity, this);
        uiC.createController();

        networkC = new NetworkController(mainActivity);

        // Now this object can be used.
        created = true;
        return true;
    }

    /**
     * Show the initial screen, load up the gallery list and show the first one.
     * @return true if it found an album to show
     */
    boolean showInitial(Bundle icicle) {
        creationCheck();
        checkBackgroundThread();

        Album initial = fileC.getInitial(icicle);
        if (initial != null) {
            showAlbum(initial);
            return true;
        } else {
            // What can we do here, if the first album is null? Perhaps the splash screen instead?
            showSplash();
        }
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
    void showSplash() {
        creationCheck();
        checkBackgroundThread();

        // Show a generic splash screen, does nothing right now.
        Log.d(TAG, "showSplash called without any implementation!", new Error());
    }

    /**
     * Display this album if it exists, false if it doesn't.
     *
     * Call on the background thread, since this reads disk.
     * @param album
     * @return true if the album was shown, false otherwise.
     */
    boolean showAlbum(Album album) {
        creationCheck();
        checkBackgroundThread();

        return fileC.showAlbum(album);
    };

    /**
     * Allow window focus changes to be handled in an activity containing the UI.
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus) {
        creationCheck();
        // I think the UI thread calls this, and we do UI modification, so let's enforce a UI thread
        checkMainThread();

        uiC.onWindowFocusChanged(hasFocus);
    }

    /**
     * Make a diagnostic message
     *
     * Call from any thread.
     * @param message
     */
    void toast(String message) {
        creationCheck();
        checkAnyThread();
        uiC.MakeText(message);
    }

    /**
     * Requests adding a URI as a gallery.
     *
     * Can be called on the foreground thread or a background thread.
     * @param album to add as a gallery
     */
    void download(final NetworkRoutines.DownloadInfo album) {
        creationCheck();
        checkAnyThread();

        // Needs to be done in the background.
        (new DownloadTask(album, fileC, networkC)).execute();
    }

    /**
     * Import the key provided here into the correct database.
     *
     * Call from any thread.
     * @param key
     */
    public void importKey(final NetworkRoutines.KeyImportInfo key) {
        creationCheck();
        checkAnyThread();

        if (isMainThread()) {
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
     * @param key
     */
    private void importKeyBackgroundThread(NetworkRoutines.KeyImportInfo key) {
        fileC.importKey(key);
    }

    static class DownloadTask extends AsyncTask<Void, Void, Void> {
        NetworkRoutines.DownloadInfo dlInfo;
        final FileController fc;
        final NetworkController nc;
        boolean didSucceed = false;

        DownloadTask(NetworkRoutines.DownloadInfo dlInfo, FileController fc, NetworkController nc) {
            this.dlInfo = dlInfo;
            this.fc = fc;
            this.nc = nc;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // Once a download is finished, we need to handle the file. The filecontroller handles
            // that via a new unzipper object.
            FileController.Unzipper unzipper = fc.createUnzipper(dlInfo);

            if (unzipper == null) {
                Log.d(TAG, "Got a null unzipper");
                didSucceed = false;
                return null;
            }

            // Creating the unzipper changes dlInfo, so let's use its reference instead.
            dlInfo = unzipper.dlInfo;
            boolean status = nc.requestURI(unzipper);
            if (!status) {
                Log.e(TAG, "Could not download file " + dlInfo.location);
                didSucceed = false;
                return null;
            }
            // We can't do anything else since we need to wait for the download to complete.
            Log.d(TAG, "Download for " + dlInfo.location + " queued.");
            didSucceed = true;
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }

    /**
     * Update the image by providing an offset
     *
     * Call from any thread.
     *
     * @param offset  is either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     * @param showFab
     */
    void updateImage(final int offset, final boolean showFab) {
        creationCheck();
        checkAnyThread();

        // Since I can be called from any thread, I might need to run this code in a background
        // thread.
        if (isMainThread()) {
            // Pop into a background thread: shouldn't do file handling from the main thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateImageBackgroundThread(offset, showFab);
                }
            }).start();
        } else {
            // Already background thread, carry on.
            updateImageBackgroundThread(offset, showFab);
        }
    }

    /**
     * Carry out the work of {@link #updateImage(int, boolean)}, but expect to be run from the
     * background thread. Can only be called from {@link #updateImage(int, boolean)}.
     * @param offset
     * @param showFab
     */
    private void updateImageBackgroundThread(final int offset, final boolean showFab) {
        if (offset != UiConstants.NEXT && offset != UiConstants.PREV) {
            crashHard("updateImage: Incorrect offset provided: " + offset);
            return;
        }

        String nextFile = fileC.getFile(offset);
        Log.d(TAG, "updateImage: next file is: " + nextFile);

        // Now switch to a foreground thread to update the UI.
        uiC.updateImage(nextFile, offset, showFab);
    }

}
