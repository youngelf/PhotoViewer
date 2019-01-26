package com.eggwall.android.photoviewer;

import android.content.Context;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.eggwall.android.photoviewer.data.Album;
import com.eggwall.android.photoviewer.data.AlbumDao;
import com.eggwall.android.photoviewer.data.AlbumDatabase;
import com.eggwall.android.photoviewer.data.Key;
import com.eggwall.android.photoviewer.data.KeyDao;
import com.eggwall.android.photoviewer.data.KeyDatabase;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.SecretKey;

import static com.eggwall.android.photoviewer.CryptoRoutines.keyFromString;
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
    private AlbumDatabase albumDb;

    /**
     * An instance of the database where I will include information about the files and albums.
     */
    private KeyDatabase keyDb;

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

    private final MainController mc;
    /**
     * The index of the current file being viewed.
     */
    private int mCurrentImageIndex = INVALID_INDEX;

    /**
     * Creates a new file controller and all the other objects it needs.
     * @param context
     * @param mainController
     */
    FileController(Context context, MainController mainController) {
        // TODO: These database calls read/write disk so I need to move them to a background thread
        this.albumDb = AlbumDatabase.getDatabase(context);
        this.keyDb = KeyDatabase.getDatabase(context);
        this.mc = mainController;
    }

    /**
     * Returns the location of the music directory which is
     * [sdcard]/pictures.
     *
     * @return the file representing the music directory.
     */
    public File getPicturesDir() {
        if (mPicturesDir != null) {
            return mPicturesDir;
        }
        final String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // If we don't have an SD card, cannot do anything here.
            Log.e(TAG, "SD card root directory is not available");
            return null;
        }

        final File rootSdLocation =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (rootSdLocation == null) {
            // Not a directory? Completely unexpected.
            Log.e(TAG, "SD card root directory is NOT a directory and is NULL");
            return null;
        }
        // Navigate over to the gallery directory.
        // TODO refactor this along with the same code in Unzipper.handleFile.
        final File galleryDir = new File(rootSdLocation, PICTURES_DIR);
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so try creating one.
            Log.e(TAG, "Gallery directory does not exist: " + rootSdLocation);
            boolean result;
            try {
                result = galleryDir.mkdir();
            } catch (Exception e) {
                String message = "Could not create a directory: " + galleryDir
                        + " Message:" + e.getMessage();
                // Nothing is going to work here, so let's fail hard
                MainController.crashHard(message);
                return null;
            }
            if (result) {
                Log.d(TAG, "Created a directory at " + galleryDir.getAbsolutePath());
            } else {
                String message = "FAILED to make a directory at " + galleryDir.getAbsolutePath();
                // Nothing is going to work here, so let's fail hard
                MainController.crashHard(message);
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

    private String getSubPath(String subpath) {
        return PICTURES_DIR.concat("/").concat(subpath);
    }

    /**
     * Returns the names of all the galleries available to the user.
     *
     * @return list of all the galleries in the pictures directory.
     */
    ArrayList<String> getGalleriesList() {
        // What we return when we don't find anything.
        // It is safer to return a zero length array than null.
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
            Log.d(TAG, "setDirectory: non-existent dir: " + galleryDir.getAbsolutePath());
            return false;
        }
        final String[] fileNames = galleryDir.list();
        if (fileNames.length <= 0) {
            // Empty directory.
            Log.d(TAG, "setDirectory: empty dir: " + galleryDir.getAbsolutePath());
            return false;
        }
        // TODO: I should check that the files that exist here are actually image files.

        // Everything checks out, let's set our current directory here.
        mCurrentGallery = galleryDir;
        mCurrentGalleryList = new ArrayList<>(Arrays.asList(fileNames));
        // Position the pointer just before the start (actually the very end), so the next call
        // to getFile returns the 0th element.
        mCurrentImageIndex = mCurrentGalleryList.size();
        return true;
    }

    /**
     * Show this album now. I need to rename this soon.
     *
     * This needs to be called on a background thread, since it processes files.
     *
     * @param album the album to show
     * @return true if the album was switched.
     */
    boolean setDirectory(Album album) {
        MainController.checkBackgroundThread();

        // Check that the given directory exists and has images
        final File galleryDir = new File(album.getLocalLocation());
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so this is invalid.
            Log.d(TAG, "setDirectory: non-existent dir: " + album.getLocalLocation());
            return false;
        }
        final String[] fileNames = galleryDir.list();
        if (fileNames.length <= 0) {
            // Empty directory.
            Log.d(TAG, "setDirectory: empty dir: " + album.getLocalLocation());
            return false;
        }
        // TODO: I should check that the files that exist here are actually image files.

        // Everything checks out, let's set our current directory here.
        mCurrentGallery = galleryDir;
        mCurrentGalleryList = new ArrayList<>(Arrays.asList(fileNames));
        for (String file : mCurrentGalleryList) {
            Log.d(TAG, "Found file: " + file);
        }

        // Position the pointer just before the start (actually the very end), so the next call
        // to getFile returns the 0th element.
        mCurrentImageIndex = mCurrentGalleryList.size();
        Log.d(TAG, "mCurrentImageIndex = " + mCurrentImageIndex);

        // Update the database to modify last-viewed-timestamp
        album.setLastViewedTimeMs(SystemClock.elapsedRealtime());
        albumDb.albumDao().update(album);

        // Now I need to ask the main controller to advance to next.
        mc.updateImage(UiConstants.NEXT, false);
        return true;
    }

    /**
     * Returns the absolute path for the file to read next.
     *
     * This should be called from a background thread since it reads disk.
     *
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
     * Import this key.
     *
     * Call from the background thread.
     * @param key
     */
    public void importKey(NetworkRoutines.KeyImportInfo key) {
        KeyDao keyDao = keyDb.keyDao();
        if (keyDao.forUuid(key.keyId) != null) {
            // Key exists, so disallow imports.
            Log.d(TAG, "Secret with UUID uuid=" + key.keyId + " EXISTS with name=" + key.name
                    + ". NOT importing");
            mc.toast("Key exists. NOT imported!");
            return;
        }
        Key k = new Key(key.keyId, key.secretKey, key.name);
        long i = keyDao.insert(k);
        Log.d(TAG, "Inserted key with secret " + key.secretKey + ", name=" + key.name
                + ", uuid=" + key.keyId + ", at location=" + i);
        mc.toast("Key imported. All good.");
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
     * To create an object, call {@link FileController#createUnzipper(NetworkRoutines.DownloadInfo)}
     * rather than directly calling the constructor.
     *
     * The critical method here is {@link #handleFile(String, ParcelFileDescriptor)}.
     *
     */
    static class Unzipper implements DownloadHandler {
        NetworkRoutines.DownloadInfo dlInfo;
        private final Album album;
        final AlbumDao albumDao;
        final KeyDao keyDao;
        private final MainController mc;
        final File mPicturesDir;
        public static String FILENAME_ERROR = "";
        public static ParcelFileDescriptor PFD_ERROR = null;

        private String createAbsolutePath(String relativePath) {
            return Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES)
                    .getAbsolutePath()
                    .concat("/")
                    .concat(relativePath);
        }
        /**
         * This method needs to be called on a non-UI thread. It does long-running file processing.
         * @param filename name of the file that was downloaded, relative to mPicturesDir
         * @param Uri a location of the file after it was downloaded. UNUSED.
         */
        @Override
        public void handleFile(String filename, ParcelFileDescriptor Uri) {
            // Check if we failed and the error handling should be invoked
            if (filename == FILENAME_ERROR && Uri == PFD_ERROR) {
                // Modify the database to remove a record of the download.
                albumDao.delete(album);
                return;
            }
            final File toUnpack;

            // Let's check the filename is what we were expecting
            Log.d(TAG, "File expected: " + dlInfo.pathOnDisk + ", observed: " + filename);

            // Unzip the file here.
            // Try opening the URI via a ParcelFileDescriptor
            if (dlInfo.isEncrypted) {
                final String plainPath = createAbsolutePath("plain" + album.getId() + ".zip");
                Log.d(TAG, "Decrypting zip at:" + plainPath);

                if ((new File(plainPath)).delete()) {
                    Log.d(TAG, "Old plain file deleted.");
                }
                // Decrypt it first, and then ask for it to be unzipped.
                try {
                    // Pick up the appropriate key from the database, and decrypt using that.
                    Key x = keyDao.forUuid(dlInfo.keyUid);
                    if (x == null) {
                        Log.d(TAG, "Did NOT find key with uuid = " + dlInfo.keyUid);
                        // Clean the existing file and return.
                        (new File(createAbsolutePath(filename))).delete();
                        return;
                    }
                    Log.d(TAG, "Found key with uuid = " + dlInfo.keyUid);
                    SecretKey KEY = keyFromString(x.getSecret());
                    CryptoRoutines.decrypt(createAbsolutePath(filename), dlInfo.initializationVector, KEY, plainPath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                toUnpack = new File(plainPath);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
                toUnpack = new File(dir, filename);
            }

            ZipFile inputZipped;
            try {
                inputZipped = new ZipFile(toUnpack);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Could not open file: " + toUnpack.getAbsolutePath(), e);
                toUnpack.delete();
                return;
            }

            // Create a directory to hold it all
            final File freshGalleryDir = new File (album.getLocalLocation());
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

            // Done with it, delete the original package file.
            if (toUnpack != null) {
                if (toUnpack.delete()) {
                    Log.d(TAG, "Plain file deleted:" + toUnpack.getAbsolutePath());
                }
            }

            // Has been downloaded right now.
            album.setDownloadTimeMs(SystemClock.elapsedRealtime());

            // Here I should modify the database to tell the file has been correctly pulled.
            albumDao.update(album);

            // Ideally here I should display this image, but there is no good way to do that.
            mc.showAlbum(album);
        }

        /** Hidden to force all creation through
         * {@link FileController#createUnzipper(NetworkRoutines.DownloadInfo)}
         * @param dlInfo The dlInfo that this unzipper was created with.
         * @param album
         * @param dao
         * @param keyDao
         * @param mc
         * @param mPicturesDir
         */
        private Unzipper(NetworkRoutines.DownloadInfo dlInfo, Album album,
                         AlbumDao dao, KeyDao keyDao, MainController mc, File mPicturesDir) {
            this.dlInfo = dlInfo;
            this.album = album;
            this.albumDao = dao;
            this.keyDao = keyDao;
            this.mc = mc;
            this.mPicturesDir = mPicturesDir;
        }
    }

    /**
     * Creates a new {@link Unzipper} object including any member-specified information.
     * Needs to be called from a background thread because it modifies databases.
     * @param dlInfo the name the dlInfo should be called.
     * @return an object that can unzip this dlInfo and extract its contents for later retrieval.
     *
     * This dlInfo object might be modified, so do not use it hence. Instead, get the updated
     * dlInfo object from {@link Unzipper#dlInfo}.
     */
    Unzipper createUnzipper(NetworkRoutines.DownloadInfo dlInfo) {
        // Check to see if we can hold the eventual file size
        File picturesDir = getPicturesDir();
        long available = picturesDir.getFreeSpace();
        if (available < dlInfo.extractedSize) {
            Log.d(TAG, "Out of disk space, cannot unzip: Expected: "
                    + dlInfo.extractedSize
                    + " Available: " + available);
            return null;
        }

        // At this point, we should create a new {@link Album} object, and then insert it into
        // the database.
        Album album = new Album();
        album.setName(dlInfo.name);
        Log.d(TAG, "dlInfo.name = " + dlInfo.name);
        String remoteLocation = dlInfo.location.toString();
        album.setRemoteLocation(remoteLocation);
        Log.d(TAG, "dlInfo.remoteLocation = " + remoteLocation);
        // Only when we insert it do we get a unique ID. This is why this method needs to be called
        // from a background thread.
        long id = albumDb.albumDao().insert(album);
        // Now set that as the canonical ID for this
        album.setId(id);
        Log.d(TAG, "dlInfo.id = " + id);

        String topLevel = picturesDir.getAbsolutePath().concat("/");
        String pathPrefix = "gal_" + String.format(Locale.US, "%04d", id);
        String localLocation = topLevel.concat(pathPrefix);
        Log.d(TAG, "dlInfo.localLocation = " + localLocation);

        // This is the location where the zip file should be stored. This is why it is a bad
        // idea to read the original dlInfo object.
        // location/gal_0335 will be unpacked from gal_0335.zip or gal_0335.asc
        String fileName;
        if (dlInfo.isEncrypted) {
            fileName = pathPrefix.concat(".asc");
        } else {
            fileName = pathPrefix.concat(".zip");
        }
        dlInfo.pathOnDisk = getSubPath(fileName);
        Log.d(TAG, "dlInfo.pathOnDisk = " + fileName);

        Log.d(TAG, "picturesDir = " + picturesDir.getAbsolutePath());

        album.setLocalLocation(localLocation);
        return new Unzipper(dlInfo, album, albumDb.albumDao(), keyDb.keyDao(), mc, picturesDir);
    }
}
