package com.eggwall.android.photoviewer;

import android.content.Context;
import android.content.Intent;

public interface NetworkRequestComplete {
    /**
     * Called when a network request has been completed.
     * @param context
     * @param intent
     */
    void requestCompleted(Context context, Intent intent, String filename);
}
