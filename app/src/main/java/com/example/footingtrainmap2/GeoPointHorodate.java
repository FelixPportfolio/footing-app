package com.example.footingtrainmap2;

import org.osmdroid.util.GeoPoint;

public class GeoPointHorodate {
    private GeoPoint geoPoint;
    private long timestamp;

    public GeoPointHorodate(double latitude, double longitude, long timestamp) {
        this.geoPoint = new GeoPoint(latitude, longitude);
        this.timestamp = timestamp;
    }

    public GeoPoint getGeoPoint() {
        return geoPoint;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getLatitude() {
        return geoPoint.getLatitude();
    }

    public double getLongitude() {
        return geoPoint.getLongitude();
    }
}