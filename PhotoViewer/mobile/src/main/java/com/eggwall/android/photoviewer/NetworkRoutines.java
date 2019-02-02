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
    private static final String SCHEME = "photoviewer";

    // One of the REQ_ keys need to be provided. None of these REQ_ keys should have the same value
    // as any of the KEY_ strings, because otherwise the optional param will be mistaken for the
    // required param.
    /** CGI param key: URL where the package is available. */
    private static final String REQ_PACKAGE_SRC = "src";

    /** CGI param key: URL where the package is available. */
    private static final String REQ_SECRETKEY = "key";


    /** CGI param key: URL where the package is available. */
    private static final String KEY_NAME = "name";

    /**
     * CGI param key: Unique ID for the key. This is a unique ID that corresponds to the key, and
     * then the key can be looked up. If the key is compromised, create another key, change the
     * UID and encrypt new packages with the updated key and uid. Retained as a string all through
     * and never converted to raw bits.
     *
     * Any UUID that conforms to RFC 4122 is great.
     * https://www.ietf.org/rfc/rfc4122.txt
     */
    private static final String KEY_UNIQUEID = "keyid";

    // Options that go along with REQ_PACKAGE_SRC
    /**
     * CGI param key: is this file encrypted with {@link CryptoRoutines#AES_CBC_PKCS5_PADDING}.
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    private static final String KEY_ENCRYPTED = "encrypted";

    /**
     * CGI param key: Initialization vector as byte[]
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    private static final String KEY_INITIALIZATION_VECTOR = "iv";

    /**
     * CGI param key: is this a zip archive?
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    private static final String KEY_ZIPPED = "zipped";

    /**
     * CGI param key: the unpacked size of the archive.
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    private static final String KEY_SIZE = "size";

    /**
     * CGI param key: Human readable dlInfo name
     * Provided as an option along with {@link #REQ_PACKAGE_SRC}
     */
    private static final String KEY_ALBUMNAME = "name";


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
         * The unique id: the UUID of the key that allows us to look it up, NOT the integer
         * id of the key in the database.
         */
        public final String keyUid;

        /**
         * Human-readable name of the dlInfo. This can contain spaces, and be longer than 8
         * characters and so is not suitable as a storage location.
         */
        public final String name;

        DownloadInfo(Uri location, String pathOnDisk, boolean isEncrypted, byte[] initializationVector,
                     int extractedSize, boolean isZipped, String keyUid, String name) {
            this.location = location;
            this.pathOnDisk = pathOnDisk;
            this.isEncrypted = isEncrypted;
            this.initializationVector = initializationVector;
            this.extractedSize = extractedSize;
            this.isZipped = isZipped;
            this.keyUid = keyUid;
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

    /**
     * A download object that signifies we don't have enough information to actually download
     * anything. This is safer than passing a null object, since we can still extract information
     * from this, like the {@link java.net.URI}, for example, without a problem.
     */
    public final static DownloadInfo EMPTY =
            new DownloadInfo(Uri.EMPTY, "", false, null, 0, false, "", "EMPTY");

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

    /**
     * Ignore this Intent because it doesn't have anything to act upon.
     */
    static final int TYPE_IGNORE = 0;
    /**
     * This is an Intent to download a package, perhaps encrypted.
     */
    static final int TYPE_DOWNLOAD = 1;
    /**
     * This is an Intent to import a secret key into the database.
     */
    static final int TYPE_SECRET_KEY = 2;
    /**
     * During development only, we use this type to perform intrusive control (deleting databases,
     * emptying directories, forcing permissions, etc)
     */
    static final int TYPE_DEV_CONTROL = 3;

    /**
     * Get the type of intent this application was started with.
     *
     * An application can get started by tapping on its icon in the Launcher, or because it is
     * handling a custom URI.
     *
     * Here is a custom URL of the kind
     * photoviewer://eggwall/download?src=http%3A%2F%2Fdropbox.com%2Fslkdjf%2Fal&zipped=y
     * This is a request to download the URL: http://dropbox.com/slkdjf/al
     *
     * This method tells you what kind of Intent this is.
     * @param intent the Intent the application was started from. Usually obtained from
     *               {@link Activity#getIntent()}
     * @return the type of Intent: {@link #TYPE_DOWNLOAD} for downloading a package,
     *          {@link #TYPE_SECRET_KEY} to import a secret key, and {@link #TYPE_IGNORE} for all
     *          other Android-related starts that we can safely ignore because we are not handling
     *          any custom URI.
     *          During Development only, we pass {@link #TYPE_DEV_CONTROL} to perform intrusive
     *          control. This will be disabled during production.
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
        if (lastPathSegment.equalsIgnoreCase("control")) {
            if (AndroidRoutines.development) {
                return TYPE_DEV_CONTROL;
            } else {
                Log.wtf(TAG, "Production build. Intrusive control disabled.");
            }
        }
        return TYPE_IGNORE;
    }

    /**
     * During development only, allow intrusive control of the internal data structures and
     * functionality.
     *
     * Entirely compiled out during production.
     *
     * @param intent the intent that the application was opened with.
     * @param mc The orchestrating controller that can do some pretty intrusive changes.
     */
    static void callControl(Intent intent, MainController mc) {
        if (intent == null || !AndroidRoutines.development) {
            return;
        }

        String action = intent.getAction();

        // Unpack the actual URL from that data string
        Uri uri = intent.getData();
        // That could be empty because the starting intent could have no data associated. This
        // happens when the user launched into it from All apps, or through commandline.
        if (uri == null || action == null) {
            return;
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

            // The strings themselves are included here to avoid compiling them in production builds
            if (names.contains("databasePurge")) {
                // Purge the entire database
                mc.databasePurge();
            }
        }
    }


    /**
     * Key to return when there is nothing to do. This is still safer than a null object because
     * individual objects make sense, an actual empty key, an empty name, for example
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
     * @return information that allows us to import a key if parsed correctly,
     *          {@link #EMPTY_KEY} otherwise.
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
     * @return download information if parsed correctly, {@link #EMPTY} otherwise.
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
        if (action != null && action.equals(Intent.ACTION_VIEW)
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

    /**
     * Given a URL, unpack the CGI params into an object.
     * @param uri a URI that was passed in an Intent.
     * @return an object, possibly {@link #EMPTY} that tells what to download, from where, etc.
     */
    private static DownloadInfo getDownloadInfo(Uri uri) {
        // All the components of the DownloadInfo object.
        // Assume URI is not specified.
        Uri uriR = Uri.EMPTY;
        // Assume not encrypted.
        boolean isEncryptedR = false;
        byte[] initVectorR = null;
        boolean isZippedR = false;
        int extractedSizeR = 0;
        String keyUid="";
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
        if (names.contains(KEY_UNIQUEID)) {
            String encoded = uri.getQueryParameter(KEY_UNIQUEID);
            // If it is available, then try to decode the parameter (since it is a string)
            keyUid = Uri.decode(encoded);
        }

        return new DownloadInfo(uriR, null, isEncryptedR, initVectorR,
                extractedSizeR, isZippedR, keyUid, albumNameR);
    }
}
