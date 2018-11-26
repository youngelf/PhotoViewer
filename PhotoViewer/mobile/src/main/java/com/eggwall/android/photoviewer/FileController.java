package com.eggwall.android.photoviewer;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Controls access to files and allows next/previous access to files
 */
class FileController {
    private static final String TAG = "FileController";
    /**
     * Name of the subdirectory in the main folder containing photos
     */
    private final static String PICTURES_DIR = "eggwall";
    /**
     * The actual directory that corresponds to the external SD card.  But nobody is allowed to
     * read this, this is only for {@link #getPicturesDir()} to reference.
     */
    private File mPicturesDir = null;

    /**
     * The name of the current gallery being viewed.
     */
    private File mCurrentGallery = null;

    /**
     * The name of the current gallery being viewed.
     */
    private ArrayList<String> mCurrentGalleryList = null;

    private static final int INVALID_INDEX = -1;

    /**
     * The index of the current file being viewed.
     */
    private int mCurrentImageIndex = INVALID_INDEX;

    /**
     * [sdcard]/music in SDK >= 8
     *
     * @return the [sdcard]/music path in sdk version >= 8
     */
    private static File getPictureDirAfterV8() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    }

    /**
     * Returns the location of the music directory which is
     * [sdcard]/pictures.
     *
     * @return the file representing the music directory.
     */
    private File getPicturesDir() {
        if (mPicturesDir != null) {
            return mPicturesDir;
        }
        final String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // If we don't have an SD card, cannot do anything here.
            Log.e(TAG, "SD card root directory is not available");
            return null;
        }

        final File rootSdLocation = getPictureDirAfterV8();
        if (rootSdLocation == null) {
            // Not a directory? Completely unexpected.
            Log.e(TAG, "SD card root directory is NOT a directory and is NULL");
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
        mPicturesDir = galleryDir;
        return mPicturesDir;
    }

    /**
     * Returns the names of all the galleries available to the user.
     *
     * @return list of all the galleries in the pictures directory.
     */
    ArrayList<String> getGalleriesList() {
        // What we return when we don't find anything. It is safer to return a zero length array than null.
        final ArrayList<String> foundNothing = new ArrayList<String>(0);

        File picturesDir = getPicturesDir();

        // We don't have a valid pictures directory.
        if (picturesDir == null) {
            return foundNothing;
        }
        Log.d(TAG, "Looking through directory " + picturesDir);
        final String[] filenames = picturesDir.list();
        ArrayList<String> galleryDirectories = foundNothing;
        if (filenames == null || filenames.length <= 0) {
            return foundNothing;
        }
        galleryDirectories = new ArrayList<>(Arrays.asList(filenames));
        // Iterate over these to ensure they are directories
        for (String name : filenames) {
            final File galleryDir = new File(picturesDir, name);
            if (!galleryDir.isDirectory()) {
                // The directory doesn't exist, so remove it.
                Log.e(TAG, name + " is not a directory.  Removing");
                galleryDirectories.remove(name);
            }
        }

        Log.e(TAG, "-- Start Gallery directories --");
        for (String name : galleryDirectories) {
            Log.e(TAG, name);
        }
        Log.e(TAG, "-- End -- ");

        if (galleryDirectories.size() <= 0) {
            Log.e(TAG, "Gallery directory has no files." + picturesDir);
            return foundNothing;
        }
        return galleryDirectories;
    }

    /**
     * Sets the current directory to the name given here. The name is relative to the gallery
     * directory.
     *
     * @param relativeDirectoryName name to set the directory to.
     * @return whether setting the directory was a success
     */
    boolean setDirectory(String relativeDirectoryName) {
        File picturesDir = getPicturesDir();

        // Check that the given directory exists and has images
        final File galleryDir = new File(picturesDir, relativeDirectoryName);
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so this is invalid.
            return false;
        }
        final String[] fileNames = galleryDir.list();
        if (fileNames.length <= 0) {
            // Empty directory.
            return false;
        }
        // TODO: I should check that the files that exist here are actually image files.

        // Everything checks out, let's set our current directory here.
        mCurrentGallery = galleryDir;
        mCurrentGalleryList = new ArrayList<String>(Arrays.asList(fileNames));
        // Position the pointer just before the start (actually the very end), so the next call
        // to getFile returns the 0th element.
        mCurrentImageIndex = mCurrentGalleryList.size();
        return true;
    }

    /**
     * Returns the absolute path for the file to read next.
     * @param next_or_previous is one of {@link UiConstants#NEXT} to load the next file or
     *                         {@link UiConstants#PREV} to load the previous file.
     * @return name of the file to load next.
     */
    String getFile(int next_or_previous) {
        if (next_or_previous != UiConstants.NEXT && next_or_previous != UiConstants.PREV) {
            // We can advance, or we can go back. Nothing else is allowed.
            return UiConstants.INVALID_GALLERY;
        }
        // We need a valid directory with a non-empty list to proceed.
        if (mCurrentGallery == null || mCurrentGalleryList == null
                || mCurrentGalleryList.size() <= 0) {
            return UiConstants.INVALID_GALLERY;
        }
        // We have never set a file, and we are moving forward
        if (next_or_previous == UiConstants.NEXT) {
            mCurrentImageIndex++;
        } else if (next_or_previous == UiConstants.PREV) {
            mCurrentImageIndex--;
        }
        // Range checks
        final int lastIndex = mCurrentGalleryList.size() - 1;
        if (mCurrentImageIndex > lastIndex) {
            // Wrap around to the start.
            mCurrentImageIndex = 0;
        }
        if (mCurrentImageIndex < 0) {
            // Wrap around to the end.
            mCurrentImageIndex = lastIndex;
        }
        return new File(mCurrentGallery, mCurrentGalleryList.get(mCurrentImageIndex)).getAbsolutePath();
    }

    /**
     * Requests adding a URI as a gallery.
     * TODO(viki): Currently not implemented.
     *
     * @param location URI to add as a gallery
     * @return true if the gallery is added.
     */
    boolean addUri(String location) {
        return false;
    }

}