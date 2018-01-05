package com.eggwall.android.photoviewer;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

/**
 * Makes requests to the network to fetch new content.
 * Created by viki on 1/4/18.
 */

public class NetworkController {
    private static final String TAG = "NetworkController";
    public static final String location =
            "http://gallery.eggwall.com/gallery_23_Sept_Just_Home/_DSC8193.jpg";


    private final DownloadManager downloadManager;
    private final Context ctx;

    /**
     * BroadcastReceiver that listens for a download request and updates when the request was done.
     */
    class Receiver extends BroadcastReceiver {
        final long mRequestId;

        public Receiver (long requestId) {
            mRequestId = requestId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            //check if the broadcast message is for our enqueued download
            long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (referenceId == mRequestId) {
                Toast toast = Toast.makeText(ctx, "Image Download Complete", Toast.LENGTH_LONG);
                Log.d(TAG, "Image download complete");
                toast.show();

                // We are never getting called again, so let's just stop ourselves.
                ctx.unregisterReceiver(this);
            }
        }
    }

    public NetworkController(Context ctx) {
        this.ctx = ctx;
        downloadManager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /**
     * Download whatever is at this location, unzipping if required, to the default gallery
     * directory.
     * @param location
     * @return
     */
    public boolean requestURI(String location) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(location));
        long requestId = downloadManager.enqueue(request);

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        Receiver r = new Receiver(requestId);
        ctx.registerReceiver(r, filter);

        return true;
    }
}
