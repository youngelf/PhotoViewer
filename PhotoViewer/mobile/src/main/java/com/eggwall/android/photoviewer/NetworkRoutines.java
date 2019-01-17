package com.eggwall.android.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.Set;

/**
 * Collection of assorted network routines that can be called in isolation.
 */
class NetworkRoutines {
    private static final String TAG = "NetworkRoutines";


    /** The scheme for the custom URI. */
    public static final String SCHEME = "photoviewer";

    /** The domain in the custom URI. Not currently checking it. */
    public static final String DOMAIN = "eggwall";

    /** The key for the URL in the CGI params. */
    public static final String KEY_NAME = "src";

    /**
     * Get the URL to download from the intent this application was started from.
     *
     * This will create a URL of the kind
     * from an intent where the Data has the URL: http://dropbox.com/slkdjf/al
     * photoviewer://eggwall/test?q=this&src=http%3A%2F%2Fdropbox.com%2Fslkdjf%2Fal
     * @param intent the Intent the application was started from. Usually obtained from
     *               {@link Activity#getIntent()}
     * @return the URL if one is parsed, {@link Uri#EMPTY} otherwise.
     */
    static Uri getDownloadInfo(Intent intent) {
        String action = intent.getAction();
        Uri toReturn = Uri.EMPTY;

        // Unpack the actual URL from that data string
        Uri uri = intent.getData();
        // That could be empty because the starting intent could have no data associated. This
        // happens when the user launched into it from All apps, or through commandline.
        if (uri == null) {
            return toReturn;
        }

        String scheme = uri.getScheme();
        Log.d(TAG, "Scheme = " + scheme);
        String path = uri.getPath();
        Log.d(TAG, "Path = " + path);

        // Confirm that this is a request to view, with the correct scheme and a non-empty path.
        if (action.equals(Intent.ACTION_VIEW)
                && scheme != null
                && scheme.equals(SCHEME)
                && path != null) {
            // If so, look for all the CGI params. We expect a special KEY_NAME to be available.
            Set<String> names = uri.getQueryParameterNames();
            if (names.contains(KEY_NAME)) {
                String encoded = uri.getQueryParameter(KEY_NAME);
                // If it is available, then try to decode the parameter (since it is a URL itself)
                // and then try to parse it as a URL.
                toReturn = Uri.parse(Uri.decode(encoded));
            }
        }
        return toReturn;
    }
}
