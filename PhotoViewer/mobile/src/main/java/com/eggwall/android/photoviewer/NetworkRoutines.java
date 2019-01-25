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

    // One of the REQ_ keys need to be provided. None of these REQ_ keys should have the same value
    // as any of the KEY_ strings, because otherwise the optional param will be mistaken for the
    // required param.
    /** CGI param key: URL where the package is available. */
    public static final String REQ_PACKAGE_SRC = "src";

    /** CGI param key: URL where the package is available. */
    public static final String REQ_SECRETKEY = "key";


    /** CGI param key: URL where the package is available. */
    public static final String KEY_NAME = "name";

    /**
     * CGI param key: Unique ID for the key. This is a unique ID that corresponds to the key, and
     * then the key can be looked up. If the key is compromised, create another key, change the
     * UID and encrypt new packages with the updated key and uid. Retained as a string all through
     * and never converted to raw bits.
     *
     * Any UUID that conforms to RFC 4122 is great.
     * https://www.ietf.org/rfc/rfc4122.txt
     */
    public static final String KEY_UNIQUEID = "keyid";

    // Options that go along with REQ_PACKAGE_SRC
    /**
     * CGI param key: is this file encrypted with {@link CryptoRoutines#AES_CBC_PKCS5_PADDING}.
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    public static final String KEY_ENCRYPTED = "encrypted";

    /**
     * CGI param key: Initialization vector as byte[]
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    public static final String KEY_INITIALIZATION_VECTOR = "iv";

    /**
     * CGI param key: is this a zip archive?
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    public static final String KEY_ZIPPED = "zipped";

    /**
     * CGI param key: the unpacked size of the archive.
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    public static final String KEY_SIZE = "size";

    /**
     * CGI param key: Human readable dlInfo name
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    public static final String KEY_ALBUMNAME = "name";


    /**
     * All the information that is provided by a URL. This is constructed when a
     * {@link #REQ_PACKAGE_SRC} CGI param (and associated param list) is present.
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
     * All the information required to import a secret key into the database. This is constructed
     * when a {@link #REQ_SECRETKEY} CGI param is present.
     */
    static class KeyImportInfo {
        /**
         * The key itself, as a Base64 encoding of the byte array.
         */
        public final String secretKey;
        /**
         * Which keyId this key corresponds to.
         */
        public final String keyId;
        /**
         * A helpful, human-readable name for the user to call this.
         */
        public final String name;

        KeyImportInfo(String secretKey, String keyId, String name) {
            this.secretKey = secretKey;
            this.keyId = keyId;
            this.name = name;
            Log.d(TAG, "Received a new key: (id=" + keyId + ", name = " + name
                    + ", secret = " + secretKey);
        }
    }

    public static final int TYPE_IGNORE = 0;
    public static final int TYPE_DOWNLOAD = 1;
    public static final int TYPE_SECRET_KEY = 2;

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
    static int getIntentType(Intent intent) {
        if (intent == null) {
            return TYPE_IGNORE;
        }
        String action = intent.getAction();
        // Unpack the actual URL from that data string
        Uri uri = intent.getData();

        // That could be empty because the starting intent could have no data associated. This
        // happens when the user launched into it from All apps, or through commandline.
        if (action == null || !action.equals(Intent.ACTION_VIEW)
                || uri == null) {
            return TYPE_IGNORE;
        }

        String scheme = uri.getScheme();
        String path = uri.getPath();

        // Confirm that this is a request to view, with the correct scheme and a non-empty path.
        if (scheme == null || !scheme.equals(SCHEME)
                || path == null) {
            return TYPE_IGNORE;
        }

        // I should change this to be based on lastPathSegment instead.
        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null) {
            return TYPE_IGNORE;
        }

        if (lastPathSegment.equalsIgnoreCase("import")) {
            return TYPE_SECRET_KEY;
        }
        if (lastPathSegment.equalsIgnoreCase("download")) {
            return TYPE_DOWNLOAD;
        }
        return TYPE_IGNORE;
    }

    /**
     * Key to return when there is nothing to do.
     */
    final static KeyImportInfo EMPTY_KEY = new KeyImportInfo("", "", "");

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
    static KeyImportInfo getKeyInfo(Intent intent) {
        if (intent == null) {
            return EMPTY_KEY;
        }

        String action = intent.getAction();

        // Unpack the actual URL from that data string
        Uri uri = intent.getData();
        // That could be empty because the starting intent could have no data associated. This
        // happens when the user launched into it from All apps, or through commandline.
        if (uri == null || action == null) {
            return EMPTY_KEY;
        }

        String scheme = uri.getScheme();
        Log.d(TAG, "Scheme = " + scheme);
        String path = uri.getPath();
        Log.d(TAG, "Path = " + path);

        String secretKey = "";
        String name = "";
        String keyId = "";

        // Confirm that this is a request to view, with the correct scheme and a non-empty path.
        if (action.equals(Intent.ACTION_VIEW)
                && scheme != null && scheme.equals(SCHEME)
                && path != null) {

            // All the parameters, to find what kind we are looking at.
            Set<String> names = uri.getQueryParameterNames();

            // Keys need to have the secret associated
            if (names.contains(REQ_SECRETKEY)) {
                String encoded = uri.getQueryParameter(REQ_SECRETKEY);
                secretKey = Uri.decode(encoded);
                Log.d(TAG, "Secret Key = " + secretKey);
            }
            if (names.contains(KEY_NAME)) {
                String encoded = uri.getQueryParameter(KEY_NAME);
                // If it is available, then try to decode the parameter (since it is a string)
                name = Uri.decode(encoded);
            }
            if (names.contains(KEY_UNIQUEID)) {
                String encoded = uri.getQueryParameter(KEY_UNIQUEID);
                // If it is available, then try to decode the parameter (since it is a string)
                keyId = Uri.decode(encoded);
            }
            return new KeyImportInfo(secretKey, keyId, name);
        }
        // Assume downloads.
        return EMPTY_KEY;
    }

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
                && scheme != null && scheme.equals(SCHEME)
                && path != null) {

            // All the parameters, to find what kind we are looking at.
            Set<String> names = uri.getQueryParameterNames();

            // Downloads need to have a path associated with them.
            if (names.contains(REQ_PACKAGE_SRC)) {
                return getDownloadInfo(uri);
            }
        }
        // Assume downloads.
        return EMPTY;
    }

    private static DownloadInfo getDownloadInfo(Uri uri) {
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
        if (names.contains(REQ_PACKAGE_SRC)) {
            String encoded = uri.getQueryParameter(REQ_PACKAGE_SRC);
            // If it is available, then try to decode the parameter (since it is a URL itself)
            // and then try to parse it as a URL.
            uriR = Uri.parse(Uri.decode(encoded));
        }
        if (names.contains(KEY_ZIPPED)) {
            String encoded = uri.getQueryParameter(KEY_ZIPPED);
            // We expect the value to be 'Y' or 'y', or 'T' or 't'.
            if (encoded != null) {
                isZippedR = encoded.equalsIgnoreCase("y")
                        || encoded.equalsIgnoreCase("t");
            }
        }
        if (names.contains(KEY_ENCRYPTED)) {
            String encoded = uri.getQueryParameter(KEY_ENCRYPTED);
            // We expect the value to be 'Y' or 'y' or 'T' or 't'.
            if (encoded != null) {
                isEncryptedR = encoded.equalsIgnoreCase("y")
                        || encoded.equalsIgnoreCase("t");
            }
        }
        if (names.contains(KEY_INITIALIZATION_VECTOR)) {
            String encoded = uri.getQueryParameter(KEY_INITIALIZATION_VECTOR);
            if (encoded != null) {
                initVectorR = CryptoRoutines.STob(encoded);
                Log.d(TAG, "initialization vector = " + CryptoRoutines.bToS(initVectorR));
            }
        }
        if (names.contains(KEY_SIZE)) {
            // Size in bytes.
            String encoded = uri.getQueryParameter(KEY_SIZE);
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
