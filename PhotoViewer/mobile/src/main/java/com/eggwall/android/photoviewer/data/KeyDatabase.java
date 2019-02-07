package com.eggwall.android.photoviewer.data;

import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.room.Database;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;
import androidx.annotation.NonNull;

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
