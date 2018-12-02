package com.eggwall.android.photoviewer;

import android.os.ParcelFileDescriptor;

public interface NetworkRequestComplete {
    /**
     * Called when a network request has been completed.
     */
    void requestCompleted(String filename, ParcelFileDescriptor Uri);
}
