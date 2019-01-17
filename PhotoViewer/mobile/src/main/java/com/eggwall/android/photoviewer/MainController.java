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

import android.net.Uri;
import android.util.Log;

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
        fileC = new FileController(mainActivity);

        uiC = new UiController(mainActivity, fileC);
        uiC.createController();

        created = true;
        return created;
    }

    /**
     * Show the initial screen, load up the gallery list and show the first one.
     * @return
     */
    boolean showInitial() {
        if (!assertCheckIs(true)) { return false; }

        ArrayList<String> galleriesList = fileC.getGalleriesList();
        if (galleriesList.size() >= 1) {
            // Select the first directory.
            fileC.setDirectory(galleriesList.get(0));
        }

        Log.d(TAG, "The next file is: " + fileC.getFile(UiConstants.NEXT));
        return true;
    }

    /**
     * Allow window focus changes to be handled in an activity containing the UI.
     * @param hasFocus
     */
    void onWindowFocusChanged(boolean hasFocus) {
        if (!assertCheckIs(true)) { return; }

        uiC.onWindowFocusChanged(hasFocus);
    }

    // Methods that we need

    /**
     * Downloads a URI, unzips, unpacks, and then displays it.
     * @param in
     */
    void downloadLocation(Uri in) {

    }


}