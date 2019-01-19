package com.eggwall.android.photoviewer;

import android.os.ParcelFileDescriptor;

/**
 * Handler that can be called when a network request is completed.
 */
public interface DownloadHandler {
    /**
     * Called when a network request has been completed. This will happen on a background
     * thread because file processing is time consuming.
     */
    void handleFile(String filename, ParcelFileDescriptor Uri);
}
