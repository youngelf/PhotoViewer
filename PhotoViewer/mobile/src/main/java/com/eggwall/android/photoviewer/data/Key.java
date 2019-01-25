package com.eggwall.android.photoviewer.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

import javax.crypto.SecretKey;

import static com.eggwall.android.photoviewer.CryptoRoutines.keyFromString;

@Entity(tableName = "key")
public class Key {
    /**
     * An opaque ID that uniquely defines this entry for all time.
     */
    @PrimaryKey(autoGenerate = true)
    private long id;

    /**
     * An opaque user-provided UUID that maps future albums to this key
     */
    @ColumnInfo(name = "uuid")
    private String uuid;

    /**
     * The actual key, as a byte array.
     */
    @ColumnInfo(name = "secret")
    private String secret;

    /**
     * A convenient human-readable name.
     */
    @ColumnInfo(name = "name")
    private String name;

    public long getId() {
        return id;
    }

    public SecretKey getKey () {
        return keyFromString(secret);
    }

    public String getSecret () {
        return secret;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return "Key called: \'" + name + "\' with unique id=\'" + id
                + "\' with SECRET=" + secret;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setName(String name) {
        this.name = name;
    }
}
