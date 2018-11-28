package com.eggwall.android.photoviewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

/**
 * Makes requests to the network to fetch new content.
 */

class NetworkController {
    private static final String TAG = "NetworkController";

    private final DownloadManager downloadManager;
    private final Context ctx;

    static final String location =
            "http://gallery.eggwall.com/gallery_23_Sept_Just_Home/_DSC8193.jpg";

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
        final String mLocation;
        final String mFilename;

        public Receiver (long requestId, String location, String filename) {
            mRequestId = requestId;
            mLocation = location;
            mFilename = filename;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
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
            Cursor c = downloadManager.query(
                    new DownloadManager.Query().setFilterById(mRequestId));

            // Ensure at least one result.
            if (c == null || !c.moveToFirst()) {
                Log.e(TAG, "DownloadManager does not know about file: " + mLocation);
                return;
            }

            // Ensure exactly one result, and log otherwise.
            if (c.getCount() != 1) {
                Log.e(TAG, "DownloadManager returned two entries for file: " + mLocation);
            }

            int statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (c.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                Log.e(TAG, "Failed to download file: " + mLocation);
                return;
            }

            // File downloaded successfully.
            Toast.makeText(ctx, "Image Download Complete", Toast.LENGTH_LONG)
                    .show();
            Log.d(TAG, "Downloaded: " + mLocation);
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
    boolean requestURI(String location, FileController.Callback callWhenComplete) {
        String filename = "x" + fileID + ".zip";
        fileID++;

        DownloadManager.Request request = new DownloadManager
                .Request(Uri.parse(location))
                .setTitle("PhotoViewer: " + location)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, filename);
        long requestId = downloadManager.enqueue(request);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        Receiver r = new Receiver(requestId, location, filename);
        ctx.registerReceiver(r, filter);

        return true;
    }
}
