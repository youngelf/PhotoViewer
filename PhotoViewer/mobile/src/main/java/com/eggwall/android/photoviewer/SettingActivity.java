package com.eggwall.android.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

/**
 * Activity that displays the current settings and allows the user to modify them.
 * All modifications are directly made on {@link android.content.SharedPreferences}
 * and so no information is passed back. The parent activity might need to re-read
 * the preferences on change, though, so the return status is still useful.
 */
public class SettingActivity extends Activity {
    public static final String TAG = "SettingSActivity";

    public static final int REQUEST_SETTINGS = 22;

    /**
     * Name of the preferences file that we will modify.
     */
    public static final String PREFS_FILE = "main";

    /**
     * Key for Slideshow Delay in seconds.
     */
    public static final String SLIDESHOW_DELAY_S = "pref-slideshow-delay";

    /**
     * Key for URI to monitor (there can be only one for now).
     */
    public static final String BEACON_LOCATION = "pref-beacon";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        // We don't allow others to modify our preferences.
        final SharedPreferences p = getSharedPreferences(PREFS_FILE, MODE_PRIVATE);

        // Supply the slideshow delay value
        final EditText slideshow_delay = findViewById(R.id.slideshow_delay);
        slideshow_delay.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                // Update the Shared preferences.
                int value;
                try {
                    value = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    // Just ignore it, and go with the default.
                    value = 10;
                    String val = "" + value;
                    slideshow_delay.setText(val);
                }
                p.edit().putInt(SLIDESHOW_DELAY_S, value).apply();
            }
        });
        int currentDelay = p.getInt(SLIDESHOW_DELAY_S, 10);
        String delay = "" + currentDelay;
        slideshow_delay.setText(delay);
    }

    /**
     * Return back to the previous activity. Called when the user clicks on the "Done" button.
     * This can also be called with a null parameter since it ignores the parameter
     *
     * @param ignored the view that this was called via. We don't need this parameter, so calling
     *                it with a null view is also fine, and closes the Activity.
     */
    public void returnToPrevious(View ignored) {
        // Now we are done, and we should signal that the activity is done
        Intent result = new Intent();
        setResult(RESULT_OK, result);

        // And we are done. This signals to the caller that their onActivityResult should
        // be called and the intent provided previously is given back to them.
        finish();
    }


}
