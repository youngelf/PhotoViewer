package com.eggwall.android.photoviewer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * A utility class to handle reading and writing to Android's {@link SharedPreferences}.
 *
 * All the methods here require a context object and allow reading and writing to preferences
 * without being aware of where the file is written, and what the keys are called.
 */
class Pref {
    /**
     * Name of the preferences file that we will modify.
     */
    private static final String PREFS_FILE = "main";

    /**
     * All the preferences that can be modified. It is an enum to limit the values that the
     * modify and get* methods can act upon.
     */
    public enum Name {
        /**
         * The slideshow delay in seconds. INT: 10 by default.
         */
        SLIDESHOW_DELAY ("pref-slideshow-delay", 10),
        /**
         * A URL to monitor for new keys or content. STRING: Empty by default.
         */
        BEACON ("pref-beacon", ""),

        ;  // Required to close off the names.

        private final String keyName;

        private @NonNull String stringDefault = "";
        private int intDefault = 0;

        /**
         * Create a Preference name with the given integer
         * @param keyName a string that identifies this preference in {@link SharedPreferences}
         *                on disk. Needs to be unique.
         */
        Name(String keyName, @NonNull String stringDefault) {
            this.keyName = keyName;
            this.stringDefault = stringDefault;
        }

        /**
         * Create a Preference name with the given integer
         * @param keyName a string that identifies this preference in {@link SharedPreferences}
         *                on disk. Needs to be unique.
         */
        Name(String keyName, int intDefault) {
            this.keyName = keyName;
            this.intDefault = intDefault;
        }
    }

    /**
     * Modify the Preferences to the value given here.
     * @param context the context of the application.
     * @param key the preference to modify
     * @param value the value to set to.
     */
    static void modify(Context context, Name key, @NonNull String value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().putString(key.keyName, value).apply();
    }

    /**
     * Modify the Preferences to the value given here.
     * @param context the context of the application.
     * @param key the preference to modify
     * @param value the value to set to.
     */
    static void modify(Context context, Name key, int value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().putInt(key.keyName, value).apply();
    }

    /**
     * Return the Preference for the name provided here. If nothing is stored, the default value
     * is provided. The storage is never modified after this call.
     * @param context the context of the application.
     * @param key the preference to read
     * @return the value stored, or the defaultValue if nothing is stored.
     */
    static @NonNull String getString(Context context, Name key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        String value;
        try {
            value = prefs.getString(key.keyName, key.stringDefault);
        } catch (ClassCastException e) {
            AndroidRoutines.logDuringDev("Pref", "Failed to read " + key.keyName);
            value = key.stringDefault;
        }
        if (value == null) {
            value = key.stringDefault;
        }
        return value;
    }

    /**
     * Return the Preference for the name provided here. If nothing is stored, the default value
     * is provided. The storage is never modified after this call.
     * @param context the context of the application.
     * @param key the preference to read
     * @return the value stored, or the defaultValue if nothing is stored.
     */
    static int getInt(Context context, Name key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        int value;
        try {
            value = prefs.getInt(key.keyName, key.intDefault);
        } catch (ClassCastException e) {
            AndroidRoutines.logDuringDev("Pref", "Failed to read " + key.keyName);
            value = key.intDefault;
        }
        return value;
    }

}
