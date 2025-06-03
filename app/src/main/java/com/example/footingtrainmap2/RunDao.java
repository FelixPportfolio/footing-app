package com.example.footingtrainmap2;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RunDao {
    @Insert
    long insert(Run run); // returns the ID of the inserted line

    @Query("SELECT * FROM Run ORDER BY timestamp DESC")
    List<Run> getAllRuns();

    @Query("SELECT * FROM Run WHERE id = :runId")
    Run getRunById(long runId);
}