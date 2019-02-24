package com.eggwall.android.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

    public static final int REQUEST_DOWNLOAD = 12;
    /** The key that holds */
    public static final String KEY_URI = "users_input";

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

        // Now call back to the Activity with this intent. This is tricky because I want
        // to remove the previous activity.
        Uri in = Uri.parse(input);

        // Now we are done, and we should signal that the activity is done
        Intent result = new Intent();
        result.putExtra(KEY_URI, in);
        setResult(RESULT_OK, result);

        // And we are done. This signals to the caller that their onActivityResult should
        // be called and the intent provided previously is given back to them.
        finish();
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
