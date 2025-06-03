package com.example.footingtrainmap2;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Run {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp; // date of race in milliseconds
    public double distance; // in meters
    public long duration; // in milliseconds
    public String points;
}