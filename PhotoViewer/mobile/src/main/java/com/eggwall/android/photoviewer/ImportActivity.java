package com.eggwall.android.photoviewer;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

/**
 * Activity that has these purposes:
 * <ol>
 *     <li>
 *         Allow copy/paste of an import string in case custom URLs don't work.
 *     This is the case when the URL is embedded inside a mail program like GMail, for
 *     instance. Another reason is Android devices like Chromebook, which have a non-standard
 *     handling of custom URL schemes.
 *     </li>
 *     <li>
 *         Introduce what the program is, to the user. This activity could be invoked
 *         from a 'Demo' label as well, so the user can see what this program does, and
 *         understand the functioning of this program.
 *     </li>
 *     <li>
 *         Unlike the custom URL scheme, the textbox here could input many directives
 *         all at the same time (in the future). So we could batch import multiple keys
 *         and also multiple albums and schedule them all together from a single import.
 *     </li>
 * </ol>
 */
public class ImportActivity extends Activity implements TextWatcher {
    public static final String TAG = "ImportActivity";
    // TODO: Handle batch import for albums and keys.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        EditText inputArea = findViewById(R.id.input_area);
        inputArea.addTextChangedListener(this);
    }

    /**
     * Method called when the "Import It" button is clicked.
     *
     * Needs to be public because it has to be available in the XML namespace.
     * @param ignored the view that was clicked, we ignore it since there is only a single
     *             button that could do this.
     */
    public void importAction(View ignored) {
        EditText inputArea = findViewById(R.id.input_area);
        String input = inputArea.getText().toString();
        Log.d(TAG, "I got: " + input);

        // Now call back to the Activity with this intent. This is tricky because I want
        // to remove the previous activity.
        Uri in = Uri.parse(input);

        // Examine what we got.
        NetworkRoutines.DownloadInfo d = NetworkRoutines.getDownloadInfo(in);
        Log.d(TAG, "Download Info = " + d.debugString());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        // TODO: Add a textwatcher that validates the input or highlights the sections
        // as it sees them.

    }
}
