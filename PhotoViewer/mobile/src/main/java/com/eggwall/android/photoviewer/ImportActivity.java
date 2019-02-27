package com.eggwall.android.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

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

    /**
     * Use as a second parameter to {@link #setIcon(int, int)} to hide the imageview.
     */
    public static final int HIDE = 0;

    /** The key that holds the result URI. */
    public static final String KEY_URI = "input_uri";

    /**
     * The key that holds the result TYPE.  These are values from {@link NetworkRoutines}, in
     * particular they are {@link NetworkRoutines#TYPE_DOWNLOAD},
     * {@link NetworkRoutines#TYPE_SECRET_KEY}, {@link NetworkRoutines#TYPE_DEV_CONTROL}, or
     * {@link NetworkRoutines#TYPE_IGNORE}
     */
    public static final String KEY_TYPE = "input_type";

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

        // Parse the input into a URL.
        Uri in = Uri.EMPTY;
        if (input.length() > 0) {
            in = Uri.parse(input);
        }

        // Now we are done, and we should signal that the activity is done
        Intent result = new Intent();
        // The kind of URI this is. Really, I can get rid of this in the future, but I'll keep it
        // right now so I can annotate the UI with what to download, what it is, and show the
        // user that things might be totally fine.
        result.putExtra(KEY_TYPE, NetworkRoutines.getUriType(in));
        // The URI itself.
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
        // This is what the user provided us. We have to be very careful because the user could
        // enter any garbage here, and might still be editing. So we have to be very defensive.
        String input = s.toString();

        // Validate the URI and print out the components right on input.
        Uri in = Uri.EMPTY;
        if (input.length() > 0) {
            in = Uri.parse(input);
        }

        // This is where the label goes.
        TextView label = findViewById(R.id.label);

        if (in == Uri.EMPTY) {
            setIcon(R.id.type_status, HIDE);
            setIcon(R.id.type_secondary, HIDE);
            label.setText(R.string.import_label);
            return;
        }

        // Read the bits by asking the NetworkRoutines methods.
        int type = NetworkRoutines.getUriType(in);

        switch(type) {
            case NetworkRoutines.TYPE_SECRET_KEY:
                setIcon(R.id.type_status, R.drawable.key);
                // Get the name of the key and do something nice.
                NetworkRoutines.KeyImportInfo key = NetworkRoutines.getKeyInfo(in);
                label.setText(key.name);
                break;

            case NetworkRoutines.TYPE_DOWNLOAD:
                setIcon(R.id.type_status, R.drawable.download);
                // Unpack this and show if there is encryption, what the
                // download location is, etc.

                NetworkRoutines.DownloadInfo album = NetworkRoutines.getDownloadInfo(in);
                setIcon(R.id.type_secondary,
                        (album.isEncrypted ? R.drawable.padlock: R.drawable.open_padlock));

                // Set the label's text to that of the URL alone
                label.setText(album.location.toString());
                break;

            case NetworkRoutines.TYPE_IGNORE:
                // Fall through
            default:
                // Poorly formed input for whatever reason.
                setIcon(R.id.type_status, HIDE);
                setIcon(R.id.type_secondary, HIDE);
                label.setText(R.string.import_label);
                break;
        }
    }

    /**
     * Set the imageview to the drawable provided here.
     * @param imageView a layout ID, like {@link R.id#type_status} that is for an ImageView.
     *                 This method doesn't check that it is a drawable ID for ImageViews.
     * @param drawable a drawable to assign to the ImageView specified by the layoudId earlier.
     *                 This could be {@link R.drawable#key}. There is no check on this as well.
     *                 Pass 0 here to remove the Imageview entirely.
     */
    void setIcon(int imageView, int drawable) {
        ImageView i = findViewById(imageView);
        if (drawable == 0) {
            // Don't change anything, just remove it from the view hierarchy.
            i.setVisibility(View.GONE);
            return;
        }
        i.setImageResource(drawable);
        i.setVisibility(View.VISIBLE);
    }
}
