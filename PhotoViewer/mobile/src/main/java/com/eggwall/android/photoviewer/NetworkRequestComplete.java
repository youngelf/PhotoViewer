package com.eggwall.android.photoviewer;

public interface NetworkRequestComplete {
    /**
     * Called when a network request has been completed.
     * @param context
     * @param intent
     */
    void requestCompleted(String filename);
}
