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
     * Returns all the albums in the database that have some local location.
     * @return all the albums that can realistically be displayed.
     */
    @Query("SELECT * FROM album WHERE local_location not null")
    List<Album> getAll();

    /**
     * Find a single ID in the database.
     * @param id an id to look for.
     * @return an Album which has the ID provided here
     */
    @Query("SELECT * FROM album WHERE id = :id")
    Album findbyId(long id);

    /**
     * Find an album (if any) in the database with the specified name and remote location
     * @param remoteLocation the URL that was downloaded for this album.
     * @param name a human readable name
     * @return an Album, if one is found, and null otherwise.
     */
    @Query("SELECT * FROM album WHERE name = :name AND remote_location = :remoteLocation")
    Album find(String remoteLocation, String name);

    //    /**
//     * Get a list of all the IDs that match any in the list provided
//     * @param ids an array of IDs to look for
//     * @return a list of albums for all the albums that exist for the ids provided in the arry.
//     */
//    @Query("SELECT * FROM album WHERE id in (:ids)")
//    List<Album> findByIdArray(int[] ids);

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
