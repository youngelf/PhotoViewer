package com.eggwall.android.photoviewer.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

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
    Album findbyId(long id);

    /**
     * Get a list of all the IDs that match any in the list provided
     * @param ids
     * @return
     */
    @Query("SELECT * FROM album WHERE id in (:ids)")
    List<Album> findByIdArray(int[] ids);

    /**
     * Insert a new Album into the database
     * @param album an album in which the id is unspecified (0 is fine).
     * @return the id that was assigned (guaranteed to be unique) for this album.
     */
    @Insert
    long insert(Album album);

    @Update
    void update(Album album);

    @Delete
    void delete(Album album);

    /**
     * Return the most recently viewed album.
     * @return the most recently viewed album, null if database is empty
     */
    @Query("SELECT * FROM album ORDER BY last_viewed_time DESC LIMIT 1")
    Album findRecent();
}
