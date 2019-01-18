package com.eggwall.android.photoviewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
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
    private final Context ctx;

    private int fileID = 1;

    /**
     * BroadcastReceiver that listens for a download request and updates when the request was done.
     *
     * There is a huge problem with this approach!
     * DownloadManager will try to retry downloads, and a download might finish after the process
     * is dead, or after a reboot. This means that we will never get the onReceive and we
     * might either be keeping our process in memory longer than we need, or we will fail to
     * unzip an album after it has been downloaded.
     *
     * The only solution is to write the location and download request id to disk, and
     * check if a download has been finished when the process first starts up. This needs
     * to be implemented.
     */
    private class Receiver extends BroadcastReceiver {
        final long mRequestId;
        final Uri mLocation;
        final String mFilename;
        final FileController.Callback mCallback;

        public Receiver(long requestId, Uri location, String filename, FileController.Callback callWhenComplete) {
            mRequestId = requestId;
            mLocation = location;
            mFilename = filename;
            mCallback = callWhenComplete;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            // We are never getting called again, so let's just unregister ourselves first.
            context.unregisterReceiver(this);

            // check if the broadcast message is for our enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (referenceId != mRequestId) {
                Log.e(TAG, "DownloadManager response mismatch! Expected = " + mRequestId
                    + "Received = " + referenceId);
                return;
            }
            // Query the download manager to confirm the file was correctly downloaded
            Cursor cursor = downloadManager.query(
                    new DownloadManager.Query().setFilterById(mRequestId));

            // Ensure at least one result.
            if (cursor == null || !cursor.moveToFirst()) {
                Log.e(TAG, "DownloadManager does not know about file: " + mLocation);
                return;
            }

            // Ensure exactly one result, and log otherwise.
            if (cursor.getCount() != 1) {
                Log.e(TAG, "DownloadManager returned two entries for file: " + mLocation);
            }

            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (cursor.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) {
                Log.e(TAG, "Failed to download file: " + mLocation);
                return;
            }

            // File downloaded successfully.
//            Toast.makeText(context, "Image Download Complete", Toast.LENGTH_LONG)
//                    .show();
            Log.d(TAG, "Downloaded: " + mLocation);

            // Get the canonical name the DownloadManager has for it
            int uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            final String dmUri = cursor.getString(uriIdx);

            final Uri u = android.net.Uri.parse(dmUri);
            final ContentResolver resolver = context.getContentResolver();
            int retryCount = 0;
            try {
                long size = 0;
                do {
                    try {
                        // Poll every second till the file is non-empty.
                        //
                        // This is needed! Maybe my CPU is too busy showing images and I need to
                        // pause the screen, or I need to check periodically and see if the
                        // file size is nonzero. Clearly the file is created, it is just empty.
                        Thread.sleep(1000);
                        retryCount++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    ParcelFileDescriptor pfd = resolver.openFileDescriptor(u, "r");
                    if (pfd == null) {
                        Log.d(TAG, "ParcelFileDescriptor null!");
                        return;
                    }
                    size = pfd.getStatSize();
                    // Modify this check to look for the expected size of bytes, not just nonzero.
                } while (size == 0 && retryCount < 100);
                Log.d(TAG, "opened file with ParcelFileDescriptor " + dmUri + " of size " + size);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // Retry count could have been 100, so I need to check file-size again.
            try {
                final ParcelFileDescriptor pfd = resolver.openFileDescriptor(u, "r");
                if (pfd == null) {
                    Log.d(TAG, "ParcelFileDescriptor null!");
                    return;
                }
                // Print out information about the pfd
                long size = pfd.getStatSize();
                Log.d(TAG, "opened file with ParcelFileDescriptor " + dmUri
                        + " of size " + size);
                if (mCallback != null) {
                    // Asynchronously, unzip the file and extract its contents.
                    (new Unzipper(mCallback, mFilename, pfd)).execute();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Unzip a file in the background by calling the callback with the filename.
     */
    static class Unzipper extends AsyncTask<Void, Void, Void> {
        private final String filename;
        private final ParcelFileDescriptor fileDescriptor;

        private final FileController.Callback callback;

        Unzipper(FileController.Callback callback, String filename,
                 ParcelFileDescriptor fileDescriptor) {
            this.filename = filename;
            this.callback = callback;
            this.fileDescriptor = fileDescriptor;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            callback.requestCompleted(filename, fileDescriptor);
            return null;
        }
    }

    NetworkController(Context ctx) {
        this.ctx = ctx;
        downloadManager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /**
     * Download whatever is at this location, unzipping if required, to the default gallery
     * directory.
     * @param location
     * @param callWhenComplete
     * @return
     */
    boolean requestURI(Uri location, FileController.Callback callWhenComplete) {
        // Let's not trust the file name provided to us, and let's write this as an ID that we
        // control.
        String filename = "x" + fileID + ".zip";
        fileID++;

        DownloadManager.Request request = new DownloadManager
                .Request(location)
                .setTitle("PhotoViewer: " + location)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, filename);
        long requestId = downloadManager.enqueue(request);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        Receiver r = new Receiver(requestId, location, filename, callWhenComplete);
        ctx.registerReceiver(r, filter);

        return true;
    }
}
