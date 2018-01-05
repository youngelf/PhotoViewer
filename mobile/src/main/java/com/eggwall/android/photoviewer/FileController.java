package com.eggwall.android.photoviewer;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.Arrays;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Controls access to files and allows next/previous access to files
 */
public class FileController {
    public static final String TAG="FileController";

    /**
     * The actual directory that corresponds to the external SD card.
     */
    private File mPicturesDir;

    /**
     * Returns the names of all the galleries available to the user.
     *
     * @return list of all the galleries in the pictures directory.
     */
    String[] getPicturesList() {
        if (mPicturesDir == null) {
            mPicturesDir = getPicturesDir();
        }
        // What we return when we don't find anything. It is safer to return a zero length array than null.
        final String[] foundNothing = new String[0];

        // Still nothing? We don't have a valid pictures directory.
        if (mPicturesDir == null) {
            return foundNothing;
        }

        final String[] filenames = mPicturesDir.list();
        Log.e(TAG, "All directories: " + Arrays.toString(filenames));
        if (filenames.length <= 0) {
            Log.e(TAG, "Gallery directory has no files." + mPicturesDir);
            return foundNothing;
        }
        return filenames;
    }


    /**
     * Name of the subdirectory in the main folder containing photos
     */
    private final static String PICTURES_DIR = "eggwall";

    /**
     * Returns the location of the music directory which is
     * [sdcard]/pictures.
     *
     * @return the file representing the music directory.
     */
    private static File getPicturesDir() {
        final String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // If we don't have an SD card, cannot do anything here.
            Log.e(TAG, "SD card root directory is not available");
            return null;
        }

        final File rootSdLocation;
        if (SDK_INT >= 8) {
            rootSdLocation = getPictureDirAfterV8();
        } else {
            rootSdLocation = getPicturesDirTillV7();
        }
        if (rootSdLocation == null) {
            // Not a directory? Completely unexpected.
            Log.e(TAG, "SD card root directory is NOT a directory: " + rootSdLocation);
            return null;
        }
        // Navigate over to the gallery directory.
        final File galleryDir = new File(rootSdLocation, PICTURES_DIR);
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so try creating one.
            Log.e(TAG, "Gallery directory does not exist." + rootSdLocation);
            boolean result;
            try {
                result = galleryDir.mkdir();
            } catch (Exception e) {
                Log.e(TAG, "Could not create a directory " + e);
                return null;
            }
            if (result) {
                Log.d(TAG, "Created a directory at " + galleryDir.getAbsolutePath());
            } else {
                Log.d(TAG, "FAILED to make a directory at " + galleryDir.getAbsolutePath());
                return null;
            }
        }
        // At this point, we must have a directory, but let's check again to be sure.
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so fail now.
            Log.d(TAG, "I thought I made a directory at " + galleryDir.getAbsolutePath() + " but " +
                    "I couldn't");
            return null;
        }

        return galleryDir;
    }

    /**
     * [sdcard]/music in SDK >= 8
     *
     * @return the [sdcard]/music path in sdk version >= 8
     */
    private static File getPictureDirAfterV8() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    }

    /**
     * [sdcard]/music in SDK < 8
     *
     * @return the [sdcard]/pictures path in sdk version < 8
     */
    private static File getPicturesDirTillV7() {
        return new File(Environment.getExternalStorageDirectory(), "pictures");
    }

    /**
     * Sets the current directory to the name given here. The name is relative to the gallery
     * directory.
     * @param relativeDirectoryName
     * @return whether setting the directory was a success
     */
    boolean setDirectory(String relativeDirectoryName) {
        return false;
    }

    /**
     * Requests adding a URI as a gallery.
     * @param location
     * @return
     */
    boolean addUri (String location) {
        return false;
    }

    /**
     * Returns an array of all galleries in the default location.
     * @return
     */
    String[] getAllGalleries () {
        return null;
    }
}
