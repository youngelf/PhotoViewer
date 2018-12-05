package com.eggwall.android.photoviewer.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.os.SystemClock;

// A single Album entry.
@Entity(tableName = "albums")
public class Album {
    /**
     * An opaque ID that uniquely defines this entry for all time.
     */
    @PrimaryKey
    private int id;
    /**
     * Remote location where this download was requested from. This remote location might
     * not exist as the provider could delete the file.
     */
    @ColumnInfo(name = "remote_location")
    private String remoteLocation;
    /**
     * Name of the unpacked location within /sdcard/Pictures/Eggwall/ for us to be able
     * to pick up this album.
     */
    @ColumnInfo(name = "name")
    private String name;
    /**
     * Last viewed by the user, in {@link SystemClock#elapsedRealtime()} millis since start of
     * device. Used only at the week-level granularity
     */
    @ColumnInfo(name = "last_viewed_time")
    private long lastViewedTimeMs;
    /**
     * Downloaded time, , in {@link SystemClock#elapsedRealtime()} millis since start of
     * device. Used only at the week-level granularity.
     */
    @ColumnInfo(name = "download_time")
    private long downloadTimeMs;
}
