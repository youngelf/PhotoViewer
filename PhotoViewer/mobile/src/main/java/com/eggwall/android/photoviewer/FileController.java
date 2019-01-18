package com.eggwall.android.photoviewer;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.eggwall.android.photoviewer.data.AlbumDatabase;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.io.File.separatorChar;

/**
 * Controls access to files and allows next/previous access to files
 */
class FileController {
    /**
     * Name of the subdirectory in the main folder containing photos
     * TODO: Change this.
     */
    private final static String PICTURES_DIR = "eggwall";
    private static final String TAG = "FileController";

    /**
     * An instance of the database where I will include information about the files and albums.
     */
    private AlbumDatabase db;

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
     * Creates a new file controller and all the other objects it needs.
     * @param context
     */
    FileController(Context context) {
        this.db = AlbumDatabase.getDatabase(context);
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
        // TODO refactor this along with the same code in Callback.requestCompleted.
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
            Log.d(TAG, "I thought I made a directory at " + galleryDir.getAbsolutePath()
                    + " but " + "I couldn't");
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
        return new File(mCurrentGallery, mCurrentGalleryList.get(mCurrentImageIndex))
                .getAbsolutePath();
    }

    /**
     * Handles the completion of a download. This class needs to unzip a file, perhaps decrypt
     * it, etc. As a result, this is the responsibility of the {@link FileController}. This class
     * needs to be static to ensure that no {@link Context} objects are leaking because these
     * callbacks are retained by the {@link android.app.DownloadManager} which is external to this
     * process.
     *
     * A constructor could be added to encode information it needs from the parent
     * {@link FileController} object.
     *
     * To create an object, call {@link FileController#createNewCallback()} rather than directly
     * calling the constructor.
     */
    static class Callback implements NetworkRequestComplete {
        @Override
        public void requestCompleted(String filename, ParcelFileDescriptor Uri) {
            // Unzip the file here.
            // Try opening the URI via a ParcelFileDescriptor

            // TODO: Check for free disk space first.
            File pictureDir = getPictureDirAfterV8();
            ZipFile inputZipped;
            try {
                inputZipped = new ZipFile(new File(pictureDir, filename));
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Could not open zip file " + filename, e);
                // TODO: Cleanup here first.
                return;
            }

            // Create a directory to hold it all
            final File freshGalleryDir = new File (pictureDir, "test");
            boolean result;
            try {
                result = freshGalleryDir.mkdir();
            } catch (Exception e) {
                Log.e(TAG, "Could not create a directory " + e);
                return;
            }

            if (result) {
                Log.d(TAG, "Created directory: " + freshGalleryDir.getAbsolutePath());
            } else {
                Log.d(TAG, "FAILED to make directory: " + freshGalleryDir.getAbsolutePath());
                return;
            }

            Enumeration<? extends ZipEntry> iter = inputZipped.entries();
            final int fourMegs = 4 * 1024 * 1024;
            byte[] buffer = new byte[fourMegs];

            while (iter.hasMoreElements()) {
                ZipEntry zipFile = iter.nextElement();
                String name = zipFile.getName();

                // The name can contain file separators. If so, then take the last part of the
                // filename, essentially flattening the hierarchy.
                Log.d(TAG, "Found filename: " + name);
                int separatorIdx = name.lastIndexOf(separatorChar);
                if (separatorIdx >= 0) {
                    // Extract just the file name
                    String lastName = name.substring(separatorIdx + 1);
                    // If this was a directory (trailing slash), ignore it.
                    if (lastName.length() <= 0) {
                        Log.d(TAG, "Ignoring directory: " + name);
                        continue;
                    }
                    Log.d(TAG, "Using just last part as filename: " + lastName
                            + " was: " + name);
                    name = lastName;
                }
                // Extract the bytes out to a new file.
                try {
                    BufferedInputStream inputStream =
                            new BufferedInputStream(inputZipped.getInputStream(zipFile));
                    File toWrite = new File(freshGalleryDir, name);
                    boolean createStatus = toWrite.createNewFile();
                    if (!createStatus) {
                        Log.e(TAG, "Could not create file " + name);
                        continue;
                    }
                    BufferedOutputStream outputStream =
                            new BufferedOutputStream(new FileOutputStream(toWrite));
                    int numBytes = 0;
                    while ((numBytes = inputStream.read(buffer)) > 0) {
                        Log.d(TAG, "Wrote " + numBytes + " bytes to " + name);
                        outputStream.write(buffer, 0, numBytes);
                    }
                    outputStream.close();
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Now delete the original zip file.
        }

        /** Hidden to force all creation through {@link FileController#createNewCallback()} */
        private Callback() {
            // Do nothing right now.
        }
    }

    /**
     * Creates a new {@link Callback} object including any member-specified information.
     * @return
     */
    Callback createNewCallback() {
        return new Callback();
    }
}
