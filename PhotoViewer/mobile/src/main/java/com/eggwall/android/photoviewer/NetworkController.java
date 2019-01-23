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
        public void onReceive(final Context context, final Intent intent) {
            // We are never getting called again, so let's just unregister ourselves first.
            context.unregisterReceiver(this);

            // Call execute on this if anything bad happens to let the callback know we are done
            // but that we failed to download anything.
            FileTask errorTask = new FileTask(mUnzipper,
                    FileController.Unzipper.FILENAME_ERROR, FileController.Unzipper.PFD_ERROR);

            // check if the broadcast message is for our enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (referenceId != mRequestId) {
                Log.e(TAG, "DownloadManager response mismatch! Expected = " + mRequestId
                    + "Received = " + referenceId);
                errorTask.execute();
                return;
            }
            // Query the download manager to confirm the file was correctly downloaded
            Cursor cursor = downloadManager.query(
                    new DownloadManager.Query().setFilterById(mRequestId));

            // Ensure at least one result.
            if (cursor == null || !cursor.moveToFirst()) {
                Log.e(TAG, "DownloadManager does not know about file: " + mLocation);
                errorTask.execute();
                return;
            }

            // Ensure exactly one result, and log otherwise.
            if (cursor.getCount() != 1) {
                Log.e(TAG, "DownloadManager returned two entries for file: " + mLocation);
            }

            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusIdx);
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Log.e(TAG, "Failed to download file: " + mLocation
                        + " Status = " + status
                        + ". Values at https://developer.android.com/reference/android/app/"
                        + "DownloadManager.html#STATUS_SUCCESSFUL");

                // Delete the entry from the album list, or at least remember the failure.
                errorTask.execute();
                return;
            }

            // File downloaded successfully.
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
                        errorTask.execute();
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
                    errorTask.execute();
                    return;
                }
                // Print out information about the pfd
                long size = pfd.getStatSize();
                Log.d(TAG, "opened file with ParcelFileDescriptor " + dmUri
                        + " of size " + size);
                if (mUnzipper != null) {
                    // Asynchronously, handle the file.
                    (new FileTask(mUnzipper, mFilename, pfd)).execute();
                    return;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                errorTask.execute();
                return;
            }
            // In case we did not return correctly, for any reason, let the error handler know.
            errorTask.execute();
        }
    }

    /**
     * Handle the file in the background by calling the task with the filename. This needs
     * to be handled in the background because the foreground thread is used for UI and file
     * handling is CPU intensive.
     */
    static class FileTask extends AsyncTask<Void, Void, Void> {
        private final String filename;
        private final ParcelFileDescriptor fileDescriptor;

        private final DownloadHandler task;

        FileTask(DownloadHandler task, String filename, ParcelFileDescriptor fileDescriptor) {
            this.filename = filename;
            this.task = task;
            this.fileDescriptor = fileDescriptor;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            task.handleFile(filename, fileDescriptor);
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
     * @param unzipper
     * @return
     */
    boolean requestURI(FileController.Unzipper unzipper) {
        NetworkRoutines.DownloadInfo dlInfo = unzipper.dlInfo;
        // Let's not trust the file name provided to us, and let's write this as an ID that we
        // control.
        DownloadManager.Request request = new DownloadManager
                .Request(dlInfo.location)
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
