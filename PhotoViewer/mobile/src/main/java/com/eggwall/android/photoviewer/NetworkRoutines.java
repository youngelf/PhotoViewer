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

    /** CGI param key: URL where the package is available. */
    public static final String KEY_LOCATION = "src";

    /** CGI param key: is this file encrypted with {@link CryptoRoutines#AES_CBC_PKCS5_PADDING} */
    public static final String KEY_ENCRYPTED = "encrypted";

    /**  CGI param key: Initialization vector as byte[] */
    public static final String KEY_INITIALIZATION_VECTOR = "iv";

    /** CGI param key: is this a zip archive? */
    public static final String KEY_ZIPPED = "zipped";

    /** CGI param key: the unpacked size of the archive. */
    public static final String KEY_SIZE = "size";

    /**  CGI param key: Human readable dlInfo name */
    public static final String KEY_ALBUMNAME = "name";

    /**
     * All the information that is provided by a URL
     */
    static class DownloadInfo {
        /**
         * Where to download the image package from
         */
        public final Uri location;

        /**
         * Where to download the image package to, relative to
         * {@link android.os.Environment#DIRECTORY_PICTURES}
         */
        public String pathOnDisk;

        /**
         * True if the image package is encrypted with {@link CryptoRoutines#AES_CBC_PKCS5_PADDING}
         */
        public final boolean isEncrypted;

        /**
         * If encrypted, the initialization vector.
         */
        public final byte[] initializationVector;

        /**
         * Final size of the entire package when it is extracted.
         */
        public final int extractedSize;

        /**
         * True if the image package is a zip. This is the only format that is supported.
         */
        public final boolean isZipped;

        /**
         * Human-readable name of the dlInfo. This can contain spaces, and be longer than 8
         * characters and so is not suitable as a storage location.
         */
        public final String name;

        DownloadInfo(Uri location, String pathOnDisk, boolean isEncrypted, byte[] initializationVector,
                     int extractedSize, boolean isZipped, String name) {
            this.location = location;
            this.pathOnDisk = pathOnDisk;
            this.isEncrypted = isEncrypted;
            this.initializationVector = initializationVector;
            this.extractedSize = extractedSize;
            this.isZipped = isZipped;
            this.name = name;

            Log.d(TAG, "Created dlInfo: location = " + location
                    + " isEncrypted = " + isEncrypted
                    + " initializationVector = " + (initializationVector != null
                            ? initializationVector : "null")
                    + " size = " + extractedSize
                    + " isZipped = " + isZipped
                    + " name = " + name
            );
        }
    }

    public final static DownloadInfo EMPTY =
            new DownloadInfo(Uri.EMPTY, "", false, null, 0, false, "EMPTY");

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
    static DownloadInfo getDownloadInfo(Intent intent) {
        String action = intent.getAction();

        // Unpack the actual URL from that data string
        Uri uri = intent.getData();
        // That could be empty because the starting intent could have no data associated. This
        // happens when the user launched into it from All apps, or through commandline.
        if (uri == null) {
            return EMPTY;
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
            return getDownloadInfo(uri);
        }

        return EMPTY;
    }

    static DownloadInfo getDownloadInfo(Uri uri) {
        // All the components of the DownloadInfo object.
        // Assume URI is not specified.
        Uri uriR = Uri.EMPTY;
        // Assume not encrypted.
        boolean isEncryptedR = false;
        byte[] initVectorR = null;
        boolean isZippedR = false;
        int extractedSizeR = 0;
        String albumNameR = "unspecified";

        // That could be empty because the starting intent could have no data associated. This
        // happens when the user launched into it from All apps, or through commandline.
        if (uri == null) {
            return EMPTY;
        }

        Set<String> names = uri.getQueryParameterNames();
        if (names.contains(KEY_LOCATION)) {
            String encoded = uri.getQueryParameter(KEY_LOCATION);
            // If it is available, then try to decode the parameter (since it is a URL itself)
            // and then try to parse it as a URL.
            uriR = Uri.parse(Uri.decode(encoded));
        }
        if (names.contains(KEY_ZIPPED)) {
            String encoded = uri.getQueryParameter(KEY_ZIPPED);
            // We expect the value to be 'Y' or 'y'.
            if (encoded != null) {
                isZippedR = encoded.equalsIgnoreCase("y");
            }
        }
        if (names.contains(KEY_ENCRYPTED)) {
            String encoded = uri.getQueryParameter(KEY_ENCRYPTED);
            // We expect the value to be 'Y' or 'y'.
            if (encoded != null) {
                isEncryptedR = encoded.equalsIgnoreCase("y");
            }
        }
        if (names.contains(KEY_INITIALIZATION_VECTOR)) {
            String encoded = uri.getQueryParameter(KEY_INITIALIZATION_VECTOR);
            // We expect the value to be 'Y' or 'y'.
            if (encoded != null) {
                initVectorR = encoded.getBytes();
            }
        }
        if (names.contains(KEY_SIZE)) {
            String encoded = uri.getQueryParameter(KEY_SIZE);
            // We expect the value to be 'Y' or 'y'.
            if (encoded != null) {
                extractedSizeR = Integer.parseInt(encoded);
            }
        }
        if (names.contains(KEY_ALBUMNAME)) {
            String encoded = uri.getQueryParameter(KEY_ALBUMNAME);
            // If it is available, then try to decode the parameter (since it is a string)
            albumNameR = Uri.decode(encoded);
        }

        return new DownloadInfo(uriR, null, isEncryptedR, initVectorR,
                extractedSizeR, isZippedR, albumNameR);
    }



}
