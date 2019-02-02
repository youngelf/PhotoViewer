package com.eggwall.android.photoviewer;

import android.os.Looper;
import android.util.Log;

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
     * Checks if the current thread is the main thread or not.
     */
    static void checkMainThread() {
        if (AndroidRoutines.development) {
            // Only during development, as the checks should pass fine once we enter production and
            // we confirm that all the static calls are made through the right codepaths.
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // This is the main thread. Do nothing.
                return;
            }
            Log.w(TAG, "Error. NOT main thread!", new Error());
        }
    }

    /**
     * Confirm the current thread is NOT the main thread or not.
     */
    static void checkBackgroundThread() {
        if (AndroidRoutines.development) {
            // Only during development, as the checks should pass fine once we enter production and
            // we confirm that all the static calls are made through the right codepaths.
            if (Looper.myLooper() != Looper.getMainLooper()) {
                // This is the main thread. Do nothing.
                return;
            }
            Log.w(TAG, "Error. NOT background thread!", new Error());
        }
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
     * Utility method to print an error, and crash hard. This will print the message, a backtrace
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
     * Utility method to print an error, and crash only during development.
     *
     * This will print the message, a backtrace and then close the entire program during development
     * and will log a severe warning during production but try to continue.
     *
     * If possible, try to recover from this, and only call this method when the entire program is
     * in danger of writing the wrong thing, or that it cannot proceed at all.
     */
    public static void crashDuringDev(String message) {
        // Log always
        Log.wtf(TAG, message, new Error());
        if (development) {
            // Crash during development
            throw new RuntimeException();
        }
    }
}
