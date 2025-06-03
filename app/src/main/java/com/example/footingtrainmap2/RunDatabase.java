package com.example.footingtrainmap2;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Run.class}, version = 1)
public abstract class RunDatabase extends RoomDatabase {
    public abstract RunDao runDao();
}