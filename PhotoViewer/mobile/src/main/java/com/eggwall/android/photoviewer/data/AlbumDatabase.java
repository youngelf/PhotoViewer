package com.eggwall.android.photoviewer.data;

import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.room.Database;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;
import androidx.annotation.NonNull;

/**
 * Keeps a log of ongoing downloads, where they are from, what files were written for them,
 * and other metadata associated with a download that is later unpacked into a file.
 *
 * Once the download is completed, it becomes a collection of Albums that exist on the device,
 * and a way for the LRU cache to purge out old entries.
 */
@Database(entities = {Album.class}, version = 1)
public abstract class AlbumDatabase extends RoomDatabase {
    public abstract AlbumDao albumDao();

    private static volatile AlbumDatabase INSTANCE;

    /**
     * Get or create a database.
     *
     * Needs to be called on a background thread, since it reads/writes disk.
     * @param context
     * @return
     */
    public static AlbumDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AlbumDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AlbumDatabase.class, "album")
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public AlbumDatabase() {
        // Create a database if none exists.

    }

    @NonNull
    @Override
    protected SupportSQLiteOpenHelper createOpenHelper(DatabaseConfiguration config) {
        return null;
    }

    @NonNull
    @Override
    protected InvalidationTracker createInvalidationTracker() {
        return null;
    }

    @Override
    public void clearAllTables() {

    }
}
