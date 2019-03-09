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

import java.io.FileNotFoundException;

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
     * Run routine tasks. This does nothing right now.
     */
    void timer() {
        // TODO: Make this download content.
        String beacon = mc.pref.getString(Pref.Name.BEACON);
        if (beacon.length() > 0) {
            // Let's poll the beacon
            Uri i = Uri.parse(beacon);

            // Fetch the URL, unpack the file, and then fetch that file.
            // TODO: this won't work right now.
            Uri p = i;
            mc.handleUri(p);
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
                long size = 0;
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

    NetworkController(Context ctx, MainController mainController) {
        this.ctx = ctx;
        this.mc = mainController;
        downloadManager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** Remove all references to internal datastructures */
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
