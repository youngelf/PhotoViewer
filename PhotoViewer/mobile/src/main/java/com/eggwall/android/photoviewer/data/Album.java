package com.eggwall.android.photoviewer.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.os.SystemClock;

// A single Album entry.
@Entity(tableName = "album")
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
     * Local location where this download is unpacked to. The local location should exist for
     * this entry to be in the database.
     */
    @ColumnInfo(name = "local_location")
    private String localLocation;

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

    public int getId() {
        return id;
    }

    public String getRemoteLocation() {
        return remoteLocation;
    }

    public String getName() {
        return name;
    }

    public long getLastViewedTimeMs() {
        return lastViewedTimeMs;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setRemoteLocation(String remoteLocation) {
        this.remoteLocation = remoteLocation;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLastViewedTimeMs(long lastViewedTimeMs) {
        this.lastViewedTimeMs = lastViewedTimeMs;
    }

    public long getDownloadTimeMs() {
        return downloadTimeMs;
    }

    public void setDownloadTimeMs(long downloadTimeMs) {
        this.downloadTimeMs = downloadTimeMs;
    }

    @Override
    public String toString() {
        return "Album: " + name + ", id: " + id + ", from " + remoteLocation;
    }

    public String getLocalLocation() {
        return localLocation;
    }

    public void setLocalLocation(String localLocation) {
        this.localLocation = localLocation;
    }
}
