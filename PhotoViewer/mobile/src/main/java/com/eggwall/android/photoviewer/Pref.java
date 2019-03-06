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
     * Key for Slideshow Delay in seconds.
     */
    private static final String SLIDESHOW_DELAY_S = "pref-slideshow-delay";
    /**
     * Key for URI to monitor (there can be only one for now).
     */
    private static final String BEACON_LOCATION = "pref-beacon";

    /**
     * All the preferences that can be modified. It is an enum to limit the values that the
     * modify and get* methods can act upon.
     */
    public enum Name {
        /**
         * The slideshow delay in seconds. Should be 10 by default.
         */
        SLIDESHOW_DELAY (0),
        /**
         * A URL to monitor for new keys or content. Empty by default.
         */
        BEACON (1),
        ;                  // Required to close off the names.

        /**
         * An integer that holds the value of this enum. It needs to be a zero-based offset since
         * it needs to refer to values in {@link #PrefKeys}.
         */
        private final int level;

        /**
         * The string keys that are used to look up the values.
         */
        private String[] PrefKeys = {
                SLIDESHOW_DELAY_S,
                BEACON_LOCATION,
        };

        /**
         * Get the string key name that corresponds to this preference name
         * @return key name for this Pref.
         */
        private @NonNull String getKeyName() {
            return PrefKeys[this.level];
        }

        /**
         * Create a Preference name with the given integer
         * @param level a zero-indexed index, starting at 0, and incrementing. Needs to be unique.
         */
        Name(int level) {
            this.level = level;
        }
    }

    /**
     * Modify the Preferences to the value given here.
     * @param c the context of the application.
     * @param p the preference to modify
     * @param value the value to set to.
     */
    static void modify(Context c, Name p, String value) {
        SharedPreferences prefs = c.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().putString(p.getKeyName(), value).apply();
    }

    /**
     * Return the Preference for the name provided here. If nothing is stored, the default value
     * is provided. The storage is never modified after this call.
     * @param c the context of the application.
     * @param p the preference to read
     * @param defaultValue the value to return if nothing is set.
     * @return the value stored, or the defaultValue if nothing is stored.
     */
    static String getString(Context c, Name p, String defaultValue) {
        SharedPreferences prefs = c.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        return prefs.getString(p.getKeyName(), defaultValue);
    }

    /**
     * Modify the Preferences to the value given here.
     * @param c the context of the application.
     * @param p the preference to modify
     * @param value the value to set to.
     */
    static void modify(Context c, Name p, int value) {
        SharedPreferences prefs = c.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        prefs.edit().putInt(p.getKeyName(), value).apply();
    }

    /**
     * Return the Preference for the name provided here. If nothing is stored, the default value
     * is provided. The storage is never modified after this call.
     * @param c the context of the application.
     * @param p the preference to read
     * @param defaultValue the value to return if nothing is set.
     * @return the value stored, or the defaultValue if nothing is stored.
     */
    static int getInt(Context c, Name p, int defaultValue) {
        SharedPreferences prefs = c.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        return prefs.getInt(p.getKeyName(), defaultValue);
    }

}
