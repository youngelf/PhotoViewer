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
import android.util.Log;

import com.eggwall.android.photoviewer.data.Album;

import java.io.File;
import java.util.ArrayList;

/**
 * Class that orchestrates the entire application. It has a {@link FileController}, a
 * {@link UiController}, and a {@link NetworkController} and orchestrates their behavior.
 */
public class MainController {
    private static final String TAG = "MainController";

    private boolean created = false;

    /** Object responsible for downloading files, and telling you what is available. */
    private FileController fileC = null;

    /** Object responsible for controlling the User Interface, refreshing them, etc. */
    private UiController uiC = null;

    /** Downloads files from the network and knows how to unzip them. */
    private NetworkController networkC;

    /**
     * Verify that the object was created before use.
     * @return true if created, false otherwise.
     * @param expectedValue
     */
    private boolean assertCheckIs(boolean expectedValue) {
        if (created != expectedValue) {
            // During development, crash pretty hard.
            Log.wtf(TAG, "Assertion failed! Expected create = " + expectedValue, new Error());
            return false;
        }
        return true;
    }

    /**
     * Creates and initializes a {@link MainController}. Before this method is called, no other
     * public calls are valid.
     * @return
     */
    boolean create(MainActivity mainActivity) {
        if (!assertCheckIs(false)) { return false; }

        // The order is important right now. The UI controller is aware of the File controller.
        // Clearly this is a bad idea, and I need to remove this dependency over time, and have all
        // three of them be entirely separate.
        fileC = new FileController(mainActivity, this);

        uiC = new UiController(mainActivity, this);
        uiC.createController();

        networkC = new NetworkController(mainActivity);

        created = true;
        return created;
    }

    /**
     * Show the initial screen, load up the gallery list and show the first one.
     * @return
     */
    boolean showInitial() {
        if (!assertCheckIs(true)) { return false; }

        // File handling on the main thread. This is a bad idea.
        ArrayList<String> galleriesList = fileC.getGalleriesList();
        if (galleriesList.size() >= 1) {
            // Select the first directory.
            fileC.setDirectory(galleriesList.get(0));
        }

        Log.d(TAG, "The next file is: " + fileC.getFile(UiConstants.NEXT));
        return true;
    }

    /**
     * Display this dlInfo if it exists, false if it doesn't.
     * @param album
     * @return
     */
    boolean showAlbum(Album album) {
        fileC.setDirectory(album);
        return true;
    };

    /**
     * Allow window focus changes to be handled in an activity containing the UI.
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus) {
        if (!assertCheckIs(true)) { return; }

        uiC.onWindowFocusChanged(hasFocus);
    }

    /**
     * Requests adding a URI as a gallery.
     *
     * @param album to add as a gallery
     */
    void download(final NetworkRoutines.DownloadInfo album) {
        // Needs to be done in the background.
        (new DownloadTask(album, fileC, networkC)).execute();
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
     * @param offset  is either {@link UiConstants#NEXT} or {@link UiConstants#PREV}
     * @param showFab
     */
    void updateImage(int offset, boolean showFab) {
        if (offset != UiConstants.NEXT && offset != UiConstants.PREV) {
            Log.e(TAG, "updateImage: Incorrect offset provided: " + offset);
            System.exit(-1);
            return;
        }

        String nextFile = fileC.getFile(offset);
        Log.d(TAG, "updateImage: next file is: " + nextFile);

        uiC.updateImage(nextFile, offset, showFab);
    }
}
