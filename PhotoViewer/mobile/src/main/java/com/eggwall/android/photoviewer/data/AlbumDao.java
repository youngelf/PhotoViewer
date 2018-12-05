package com.eggwall.android.photoviewer.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums")
    List<Album> getAll();

    @Query("SELECT * FROM albums WHERE id = :id")
    Album findbyId(int id);

    @Insert
    void insert(Album album);

    @Update
    void update(Album album);

}
