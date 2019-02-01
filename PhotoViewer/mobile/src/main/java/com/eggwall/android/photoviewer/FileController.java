package com.eggwall.android.photoviewer;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.annotation.NonNull;
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
     * TODO: Change this to something more sane, or perhaps more glorious!
     */
    private final static String PICTURES_DIR = "eggwall";
    private static final String TAG = "FileController";

    /**
     * {@link Bundle} key for the album ID that is currently displayed.
     */
    private static final String SS_ALBUMID = "fc-albumId";

    /**
     * {@link Bundle} key for the offset of the image (index from start) that is currently
     * displayed.
     */
    private static final String SS_IMAGEOFFSET = "fc-offset";
    private static final int INVALID_ALBUMID = -1;

    /**
     * An instance of the database where I will include information about the files and albums.
     */
    private AlbumDatabase albumDb;

    /**
     * An instance of the database where I will include information about encryption keys.
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

    /**
     * Index in the gallery that is guaranteed never to be valid.
     */
    private static final int INVALID_INDEX = -1;

    /**
     * A handle to the {@link MainController}. This is needed because the {@link FileController}
     * cannot modify UI but needs to (when displaying an album, for instance). In these cases
     * the {@link MainController} is our single reverse-delegate and correctly delegates all other
     * methods on our behalf.
     */
    private MainController mc;

    /**
     * The index of the current file being viewed. Point to {@link #INVALID_INDEX} if no index
     * is known, or one cannot be set (in case an album can't be read)
     */
    private int mCurrentImageIndex = INVALID_INDEX;

    /**
     * Just for the purpose of saving state in {@link #onSaveInstanceState(Bundle)}, we need to
     * remember what the current Album is.
     */
    private long mCurrentAlbumId;

    /**
     * Creates a new file controller and all the other objects it needs.
     * @param context The context that the Activity was started with (Application context should
     *                be fine as well)
     * @param mainController the main orchestrator we can call for UI changes or other behavior
     *                       that we can't do.
     */
    FileController(Context context, MainController mainController) {
        // TODO: These database calls read/write disk so I need to move them to a background thread
        this.albumDb = AlbumDatabase.getDatabase(context);
        this.keyDb = KeyDatabase.getDatabase(context);
        this.mc = mainController;
    }

    public void destroy() {
        albumDb = null;
        keyDb = null;
        mc = null;
    }


    /**
     * Returns the location of the music directory which is [sdcard]/pictures.
     * If it cannot get a directory to read/write from, the entire gallery program is useless.
     * As a result, this method will either return a valid directory or crash hard with a
     * (hopefully) helpful error message that allows diagnosis for the problem.
     *
     * @return the file representing the music directory. Always returns non-null.
     */
    private @NonNull File getPicturesDir() {
        if (mPicturesDir != null) {
            return mPicturesDir;
        }

        // The root directory (guaranteed to exist on a UNIX filesystem)
        // This is never used because we always crash hard when returning this, killing the program.
        File EMPTY = new File("/");

        final String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // If we don't have an SD card, cannot do anything here.
            AndroidRoutines.crashHard("SD card root directory is not available");
            return EMPTY;
        }

        final File rootSdLocation =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (rootSdLocation == null) {
            // Not a directory? Completely unexpected.
            AndroidRoutines.crashHard("SD card root directory is NOT a directory and is NULL");
            return EMPTY;
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
                AndroidRoutines.crashHard(message);
                return EMPTY;
            }
            if (result) {
                Log.d(TAG, "Created a directory at " + galleryDir.getAbsolutePath());
            } else {
                String message = "FAILED to make a directory at " + galleryDir.getAbsolutePath();
                // Nothing is going to work here, so let's fail hard
                AndroidRoutines.crashHard(message);
                return EMPTY;
            }
        }
        // At this point, we should have a directory, but let's confirm.
        if (!galleryDir.isDirectory()) {
            // The directory still doesn't exist, we should fail as hard as possible.
            // It cold be that directories that are created are not flushed, and therefore not
            // available immediately. I've never seen this happen, but it could. Even so, downstream
            // code relies on the existence of this directory, and we are better off crashing right
            // away than failing.
            // One possibility could be to wait for a second for the flash to settle down and
            // check if the directory exists.
            AndroidRoutines.crashHard(
                    "Failed to make a directory at " + galleryDir.getAbsolutePath());
            return EMPTY;
        }

        // A valid, non-null, directory that exists, and we can write to. Remember this for the
        // future.
        mPicturesDir = galleryDir;
        return mPicturesDir;
    }

    /**
     * Given a relative path for the gallery, this prepares a path that is relative to the
     * Android Pictures directory (like /sdcard/Pictures) that we will write to.
     * @param galleryDir the directory where the current album will be housed
     * @return the directory within the Pictures directory (like /sdcard/Pictures) where this
     *          content is housed.
     */
    private String getSubPath(String galleryDir) {
        return PICTURES_DIR.concat("/").concat(galleryDir);
    }

    /**
     * Get an initial album. This could be the previous album that was specified in the bundle
     * or it could be the newest album specified earlier.
     * @param icicle the saved instance state from a previous run, this argument can be null.
     * @return the album that we should show. The choice here could be completely arbitrary, and
     *          could also be null if there is no album we can show.
     */
    Album getInitial(Bundle icicle) {
        Album album;
        AlbumDao albumDao = albumDb.albumDao();

        if (icicle != null) {
            mCurrentAlbumId = icicle.getLong(SS_ALBUMID, INVALID_ALBUMID);
            if (mCurrentAlbumId != INVALID_ALBUMID) {
                album = albumDao.findbyId(mCurrentAlbumId);
                if (album != null) {
                    // See if an offset exists, and read that.
                    mCurrentImageIndex = icicle.getInt(SS_IMAGEOFFSET, INVALID_INDEX);
                    return album;
                }
                // Oops, we had a saved instance state, but it pointed to a non-existent album
                Log.d(TAG, "Nonexistent album with id=" + mCurrentAlbumId);
            }
            // Fallthrough to default behavior.
        }
        // Go through the album db, and find the most recent one.
        album = albumDao.findRecent();
        if (album == null) {
            // Ouch, the user has not downloaded anything. Show a generic splash screen.
            mc.showSplash();
        } else {
            Log.d(TAG, "getInitial: Returning album with id=" + album.getId()
                    + " at location = " + album.getLocalLocation());
        }
        return album;
    }

    /**
     * Show this album now.
     *
     * This needs to be called on a background thread, since it processes files.
     *
     * @param album the album to show
     * @return true if the album was switched.
     */
    boolean showAlbum(Album album) {
        AndroidRoutines.checkBackgroundThread();

        // Check that the given directory exists and has images
        final File galleryDir = new File(album.getLocalLocation());
        if (!galleryDir.isDirectory()) {
            // The directory doesn't exist, so this is invalid.
            Log.d(TAG, "showAlbum: non-existent dir: " + album.getLocalLocation());
            return false;
        }
        final String[] fileNames = galleryDir.list();
        if (fileNames.length <= 0) {
            // Empty directory.
            Log.d(TAG, "showAlbum: empty dir: " + album.getLocalLocation());
            return false;
        }
        // Everything checks out, let's set our current directory here.
        mCurrentGallery = galleryDir;
        mCurrentAlbumId = album.getId();
        mCurrentGalleryList = new ArrayList<>(Arrays.asList(fileNames));
        for (String file : mCurrentGalleryList) {
            Log.d(TAG, "Found file: " + file);
        }

        // Check if the index is too far out or not initialized. If it is initialized, it could
        // have been done in showInitial() where we read the index from an icicle, or from the
        // previous gallery, in which case it is a random index. Just make sure it still points to
        // a location in the current gallery.
        int size = mCurrentGalleryList.size();
        if (mCurrentImageIndex > size || mCurrentImageIndex == INVALID_INDEX) {
            // Position the pointer just before the start (actually the very end), so the next call
            // to getFile returns the 0th element.
            mCurrentImageIndex = size;
        }
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
     * @param key a key to import
     */
    void importKey(NetworkRoutines.KeyImportInfo key) {
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
     * Save any state that I might need in {@link #getInitial(Bundle)}.
     * @param icicle guaranteed non-null
     */
    void onSaveInstanceState(Bundle icicle) {
        // The next time we load it, we'll advance it to the next image, so reverse back an image.
        if (mCurrentImageIndex == 0) {
            mCurrentImageIndex = mCurrentGalleryList.size();
        } else {
            mCurrentImageIndex--;
        }
        icicle.putInt(SS_IMAGEOFFSET, mCurrentImageIndex);
        icicle.putLong(SS_ALBUMID, mCurrentAlbumId);
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
        static String FILENAME_ERROR = "";
        static ParcelFileDescriptor PFD_ERROR = null;

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
            if (filename.equals(FILENAME_ERROR) && Uri == PFD_ERROR) {
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
                if (!toUnpack.delete()) {
                    Log.d(TAG, "Could not delete the temporary file: "
                            + toUnpack.getAbsolutePath());
                }
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
                    int numBytes;
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
            if (toUnpack != null && toUnpack.delete()) {
                Log.d(TAG, "Plain file deleted:" + toUnpack.getAbsolutePath());
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
         * @param dlInfo The dlInfo that specifies the URI for the package, whether it should be
         *               decrypted, which key ID to use when decrypting, whether it should be
         *               unzipped, etc.
         * @param album The album object from our database. This is needed in addition to the
         *              dlInfo object because they have different information. This album object
         *              knows what unique ID corresponds to this in the database, while only the
         *              dlInfo object has the remote URL, and whether the album has to be
         *              encrypted.
         * @param dao The Data Access Object that allows us to access the Album DB.
         * @param keyDao The Data Access Object that allows us access to the Key DB.
         * @param mc the orchestrating main controller
         * @param mPicturesDir the directory that we should unpack files into. This should be the
         *                     output of {@link #getPicturesDir()} but since this is a static object
         *                     it is cleaner to pass the picture directory.
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

        String remoteLocation = dlInfo.location.toString();
        album.setRemoteLocation(remoteLocation);

        // Only when we insert it do we get a unique ID. This is why this method needs to be called
        // from a background thread.
        long id = albumDb.albumDao().insert(album);
        // Now set that as the canonical ID for this
        album.setId(id);

        String topLevel = picturesDir.getAbsolutePath().concat("/");
        String pathPrefix = "gal_" + String.format(Locale.US, "%04d", id);
        String localLocation = topLevel.concat(pathPrefix);

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

        Log.d(TAG, "dlInfo.name = " + dlInfo.name
                + "\n\t dlInfo.id = " + id  + ", dlInfo.remoteLocation = " + remoteLocation
                + "\n\t dlInfo.localLocation = " + localLocation
                + "\n\t dlInfo.pathOnDisk = " + fileName
                + ", picturesDir = " + picturesDir.getAbsolutePath());

        album.setLocalLocation(localLocation);
        return new Unzipper(dlInfo, album, albumDb.albumDao(), keyDb.keyDao(), mc, picturesDir);
    }
}
