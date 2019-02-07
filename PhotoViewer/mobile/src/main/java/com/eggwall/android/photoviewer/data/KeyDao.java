package com.eggwall.android.photoviewer.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface KeyDao {

    @Query("SELECT * FROM `key`")
    List<Key> getAll();

    /**
     * Find by the primary ID, not the unique key id.
     * @param id
     * @return
     */
    @Query("SELECT * FROM `key` where id = :id")
    Key findById(int id);

    /**
     * Find the key that will unlock secrets with this uuid.
     * @param uuid
     * @return
     */
    @Query("SELECT * from `key` where uuid = :uuid")
    Key forUuid(String uuid);


    /**
     * Insert a key into the database.
     * @param key
     * @return
     */
    @Insert
    long insert(Key key);

    @Delete
    void delete(Key key);

    // I don't want to provide an update, since any conflict of UUID is a huge problem.
}
