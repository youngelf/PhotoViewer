package com.eggwall.android.photoviewer;

import android.os.Looper;
import android.util.Log;

/**
 * A collection of Android-specific routines that help either debug, or check threading, or
 * crash.
 *
 * During development, it is safest to turn all crashes on. During production, the crashes are
 * turned off, but the logging is retained.
 *
 * To turn development on, set the flag {@link #development} to true. Remember to turn back to
 * false to remove all the recoverable crashes, and to disallow secret development-only behavior.
 */
public class AndroidRoutines {
    public static final String TAG = "AndroidRoutines";

    /**
     * Set this to true for production to avoid crashing on trivial things that we can hobble
     * along with.
     */
    static final boolean development = true;

    /**
     * Checks if the current thread is the main thread or not.
     * @return true if main thread
     */
    static boolean isMainThread() {
        return (Looper.myLooper() == Looper.getMainLooper());
    }

    /**
     * During development: crashes if the current thread is NOT the main thread.
     * In production, logs aberrations, but doesn't crash.
     */
    static void checkMainThread() {
        if (AndroidRoutines.development) {
            // Only during development, as the checks should pass fine once we enter production and
            // we confirm that all the static calls are made through the right codepaths.
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // This is the main thread. Do nothing.
                return;
            }
        }
        crashDuringDev("Error. NOT main thread!");
    }

    /**
     * During development: Crashes if the current thread is NOT the background thread.
     * In production, logs aberrations, but doesn't crash.
     */
    static void checkBackgroundThread() {
        if (AndroidRoutines.development) {
            // Only during development, as the checks should pass fine once we enter production and
            // we confirm that all the static calls are made through the right codepaths.
            if (Looper.myLooper() != Looper.getMainLooper()) {
                // This is the main thread. Do nothing.
                return;
            }
        }
        crashDuringDev("Error. NOT background thread!");
    }

    /**
     * Confirm that any thread is great.  Confirms that neither {@link #checkBackgroundThread()}
     * nor {@link #checkMainThread()} are needed here.
     */
    static void checkAnyThread() {
        // This is more for the programmer to read than for anything to be confirmed. The existence
        // of this method confirms that the programmer is certain that any thread is good, and that
        // they didn't forget to call one of checkBackgroundThread() or checkMainThreads().

        // Do nothing.
    }

    /**
     * Print an error, and crash hard. This will print the message, a backtrace
     * and then close the entire program.
     *
     * If possible, try to recover from this, and only call this method when the entire program is
     * in danger of writing the wrong thing, or that it cannot proceed at all.
     */
    static void crashHard(String message) {
        Log.wtf(TAG, message, new Error());
        throw new RuntimeException();
    }

    /**
     * During development: Print an error, and crash.
     *
     * This will print the message, a backtrace and then close the entire program during development
     * and will log a severe warning during production but try to continue.
     *
     * If possible, try to recover from this, and only call this method when the entire program is
     * in danger of writing the wrong thing, or that it cannot proceed at all.
     */
    static void crashDuringDev(String message) {
        // Always log
        Log.wtf(TAG, message, new Error());

        // Crash only during development
        if (development) {
            throw new RuntimeException();
        }
    }
}
