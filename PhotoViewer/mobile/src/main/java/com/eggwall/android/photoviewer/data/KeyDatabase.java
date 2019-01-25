package com.eggwall.android.photoviewer.data;

import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.DatabaseConfiguration;
import android.arch.persistence.room.InvalidationTracker;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.support.annotation.NonNull;

@Database(entities={Key.class}, version=1)
public abstract class KeyDatabase extends RoomDatabase {

    public abstract KeyDao keyDao();

    private static volatile KeyDatabase INSTANCE;

    public static KeyDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (KeyDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            KeyDatabase.class, "key")
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public KeyDatabase() {

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
