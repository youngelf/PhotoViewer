package com.eggwall.android.photoviewer.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface AlbumDao {
    /**
     * Returns all the albums in the database.
     * @return
     */
    @Query("SELECT * FROM album")
    List<Album> getAll();

    /**
     * Find a single ID in the database.
     * @param id
     * @return
     */
    @Query("SELECT * FROM album WHERE id = :id")
    Album findbyId(int id);

    /**
     * Get a list of all the IDs that match any in the list provided
     * @param ids
     * @return
     */
    @Query("SELECT * FROM album WHERE id in (:ids)")
    List<Album> findByIdArray(int[] ids);

    @Insert
    void insert(Album album);

    @Update
    void update(Album album);

    @Delete
    void delete(Album album);

}
