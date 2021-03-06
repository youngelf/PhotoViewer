package com.eggwall.android.photoviewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.common.base.Charsets;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Makes requests to the network to fetch new content.
 */
class NetworkController {
    private static final String TAG = "NetworkController";

    private final DownloadManager downloadManager;
    /** The orchestrator */
    private MainController mc;
    private Context ctx;

    /**
     * The beacon can point to a URL of max 4k of length. Anything larger than this and
     * we will read the first 4k of characters and try to convert them to a URL. There are
     * some online links that suggest that URLs are a max of 2k of size, so this is a huge
     * bump from that:
     * @see <a href="https://stackoverflow.com/questions/417142/what-is-the-maximum-length-of-a-url-in-different-browsers">
     *     stack overflow article</a>
     */
    final static int MAX_BEACON_SIZE = 4 * 1024;

    /**
     * Run routine tasks. This reads the beacon and finds what information is pointed to
     * by it. This needs to run in the background since it downloads information and then
     * it reads it, and handles the URI.
     */
    @WorkerThread
    void timer() {
        checkBeacon();
    }

    /**
     * Check the beacon to see if any content exists. If so, fetch it, and then ask the
     * {@link MainController} to handle its contents.
     *
     * If the Beacon URL in the settings was malformed, this method modifies settings to empty
     * out the setting (so future runs don't encounter the same problems).
     *
     * A single URL is allowed on a single line in the beacon. No fancy HTML. I am not sure
     * if this means that we also get http headers and other information, but I need to check
     * this and see what I receive.
     */
    @WorkerThread
    private void checkBeacon() {
        // Find out if a beacon URL exists.
        String beacon_pref = mc.pref.getString(Pref.Name.BEACON);
        if (beacon_pref.length() > 0) {
            // Let's poll the beacon
            URL beacon;
            try {
                beacon = new URL(beacon_pref);
            } catch (MalformedURLException e) {
                Log.d(TAG, "Beacon is malformed: " + e.getMessage());
                beacon = null;
            }
            if (null == beacon) {
                Log.d(TAG, "Could not create a valid URL.");
                // Now remove the beacon from the settings because it is malformed.
                mc.pref.modify(Pref.Name.BEACON, "");
                return;
            }
            HttpURLConnection connection;
            InputStream body;
            try {
                connection = (HttpURLConnection) beacon.openConnection();
                if (null == connection) {
                    return;
                }
                // Read the response body here, not the headers.
                body = new BufferedInputStream(connection.getInputStream());
            } catch (Exception e) {
                Log.d(TAG, "Beacon could not be read: " + e.getMessage());
                return;
            }
            byte[] beacon_input = new byte[MAX_BEACON_SIZE];
            try {
                // Expect a single line, which is the instruction of what comes next, no HTML
                // markup.
                int bytes_read = body.read(beacon_input, 0, MAX_BEACON_SIZE);
                if (bytes_read < 0) {
                    // Nothing came in, mark an error for the future, but for now, just return
                    return;
                }
            } catch (IOException e) {
                // Had trouble reading from the beacon. Mark an error.
                Log.d(TAG, "Timer failed to read from beacon: " + e.getMessage());
                return;
            } finally {
                connection.disconnect();
            }

            // Try to parse the byte array into a URL, and then handle it.
            String url = new String(beacon_input, Charsets.UTF_8);
            AndroidRoutines.logDuringDev(TAG, "Beacon produced: " + url);
            // Fetch the URL, unpack the file, and then fetch that file.
            Uri toHandle = Uri.parse(url);
            mc.handleUri(toHandle);
        }
    }

    /**
     * BroadcastReceiver that listens for a download request and updates when the request was done.
     *
     * There is a huge problem with this approach!
     * DownloadManager will try to retry downloads, and a download might finish after the process
     * is dead, or after a reboot. This means that we will never get the onReceive and we
     * might either be keeping our process in memory longer than we need, or we will fail to
     * unzip an dlInfo after it has been downloaded.
     *
     * The only solution is to write the location and download request id to disk, and
     * check if a download has been finished when the process first starts up. This needs
     * to be implemented.
     */
    private class Receiver extends BroadcastReceiver {
        final long mRequestId;
        final Uri mLocation;
        final String mFilename;
        final FileController.Unzipper mUnzipper;

        public Receiver(long requestId, Uri location, String filename,
                        FileController.Unzipper callWhenComplete) {
            mRequestId = requestId;
            mLocation = location;
            mFilename = filename;
            mUnzipper = callWhenComplete;
        }

        @Override
        public void onReceive(final Context context, Intent intent) {
            // We are never getting called again, so let's just unregister ourselves first.
            // TODO: This is not a good idea. There might be other downloads in progress, and we
            // should not remove the entire receiver.
            context.unregisterReceiver(this);

            // Call execute on this if anything bad happens to let the callback know we are
            // done but that we failed to download anything.

            // check if the broadcast message is for our enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (referenceId != mRequestId) {
                logErrorToast("DownloadManager response mismatch!"
                        + " Expected = " + mRequestId
                        + " Received = " + referenceId);
                return;
            }

            // Handle download in a background thread: onReceive is called on the main thread
            // and we have to read disk which should be done in a background thread.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    handleDownloadBackgroundThread(context);
                }
            }).start();
        }

        /**
         * Actually handle the download in a background thread.
         *
         * This waits for the full file to be written, and then passes it to the unzipper
         * that was created earlier in {@link #mUnzipper} to process the file which involves
         * decrypting it, unzipping it, etc.
         *
         * @param context the context received in the
         *      {@link BroadcastReceiver#onReceive(Context, Intent)} because it could be different
         *                from the application's Context, and we shouldn't be storing
         *                these objects.
         */
        private void handleDownloadBackgroundThread(Context context) {
            // Query the download manager to confirm the file was correctly downloaded
            Cursor cursor = downloadManager.query(
                    new DownloadManager.Query().setFilterById(mRequestId));

            // Ensure at least one result.
            if (cursor == null || !cursor.moveToFirst()) {
                logErrorToast("DownloadManager does not know about file: "  + mLocation);
                return;
            }

            // Ensure exactly one result, and log otherwise.
            if (cursor.getCount() != 1) {
                logErrorToast("DownloadManager returned two entries for file: "
                        + mLocation);
                return;
            }

            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusIdx);
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                logErrorToast("Failed to download file: " + mLocation);
                Log.e(TAG, "Status = " + status
                        + ". Values at https://developer.android.com/reference/android/app/"
                        + "DownloadManager.html#STATUS_SUCCESSFUL");
                return;
            }

            // File downloaded successfully.
            Log.d(TAG, "Downloaded: " + mLocation);

            // Get the canonical name the DownloadManager has for it
            int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            final String dmUri = cursor.getString(uriIdx);

            final Uri u = Uri.parse(dmUri);
            final ContentResolver resolver = context.getContentResolver();
            int retryCount = 0;
            try {
                long size;
                do {
                    try {
                        // Poll every second till the file is non-empty.
                        //
                        // This is needed! Maybe my CPU is too busy showing images and we
                        // need to pause the screen, or I need to check periodically if the
                        // file size is nonzero. Clearly the file is created, it is just
                        // empty.
                        Thread.sleep(1000);
                        retryCount++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        // But ignore it because we can try reading the file.
                    }
                    ParcelFileDescriptor pfd = resolver.openFileDescriptor(u, "r");
                    if (pfd == null) {
                        logErrorToast("ParcelFileDescriptor null!");
                        return;
                    }
                    size = pfd.getStatSize();
                    // TODO: Modify this check to look for the expected size of bytes,
                    // not just nonzero.
                } while (size == 0 && retryCount < 100);
                Log.d(TAG, "opened file with ParcelFileDescriptor " + dmUri
                        + " of size " + size);

            } catch (FileNotFoundException e) {
                logErrorToast("File not found! " + e.getMessage());
                return;
            }

            // Retry count could have been 100, so I need to check file-size again.
            try {
                final ParcelFileDescriptor pfd = resolver.openFileDescriptor(u, "r");
                if (pfd == null) {
                    logErrorToast("ParcelFileDescriptor null!");
                    return;
                }
                // Print out information about the pfd
                long size = pfd.getStatSize();
                Log.d(TAG, "opened file with ParcelFileDescriptor " + dmUri
                        + " of size " + size);
                if (mUnzipper != null) {
                    // Actually handle the file here, which means unzip it, decrypt it
                    // if required, etc.
                    mUnzipper.handleFile(mFilename, pfd);
                    return;
                }
            } catch (FileNotFoundException e) {
                logErrorToast("File not found! " + e.getMessage());
                return;
            }
            // In case we did not return correctly, for any reason, let the error
            // handler know.
            logErrorToast("Could not open file successfully for unknown reasons");
        }

        /**
         * Log an error, show a toast with a message, and execute the error task so that the
         * album entry is cleaned up appropriately.
         * @param message a human-readable message to show (and log with
         *                  {@link Log#e(String, String)} to indicate the problem
         */
        private void logErrorToast(String message) {
            Log.e(TAG, message);
            mc.toast(message);
            // This is how we signal an error, by calling the unzipper with error constants.
            mUnzipper.handleFile(FileController.Unzipper.FILENAME_ERROR,
                    FileController.Unzipper.PFD_ERROR);
        }
    }

    NetworkController(@NonNull Context ctx, @NonNull MainController mainController) {
        this.ctx = ctx;
        this.mc = mainController;
        downloadManager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** Remove all references to internal data structures */
    void destroy() {
        ctx = null;
        mc = null;
    }

    /**
     * Download whatever is at this location, unzipping if required, to the default gallery
     * directory.
     * @param unzipper an object that can unzip the file correctly once it is downloaded.
     * @return true if the download was requested correctly
     */
    boolean requestURI(FileController.Unzipper unzipper) {
        NetworkRoutines.DownloadInfo dlInfo = unzipper.dlInfo;

        // Let's not trust the file name provided to us, and let's write this as an ID that we
        // control.
        DownloadManager.Request request = new DownloadManager.Request(dlInfo.location)
                .setTitle("PhotoViewer: " + dlInfo.location)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_PICTURES, dlInfo.pathOnDisk);

        long requestId = downloadManager.enqueue(request);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        Receiver r = new Receiver(requestId, dlInfo.location, dlInfo.pathOnDisk, unzipper);
        ctx.registerReceiver(r, filter);

        return true;
    }
}
