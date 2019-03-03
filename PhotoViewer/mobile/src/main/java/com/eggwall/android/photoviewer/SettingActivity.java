package com.eggwall.android.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity that displays the current settings and allows the user to modify them.
 * All modifications are directly made on {@link android.content.SharedPreferences}
 * and so no information is passed back. The parent activity might need to re-read
 * the preferences on change, though, so the return status is still useful.
 */
public class SettingActivity extends Activity {
    public static final String TAG = "SettingSActivity";

    public static final int REQUEST_SETTINGS = 22;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
    }

    /**
     * Return back to the previous activity.
     */
    void returnToPrevious() {
        // Now we are done, and we should signal that the activity is done
        Intent result = new Intent();
        setResult(RESULT_OK, result);

        // And we are done. This signals to the caller that their onActivityResult should
        // be called and the intent provided previously is given back to them.
        finish();
    }


}
