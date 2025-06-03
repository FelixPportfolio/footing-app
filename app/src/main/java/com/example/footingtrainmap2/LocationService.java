package com.example.footingtrainmap2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;

public class LocationService extends Service {
    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean isPaused = false;
    private ArrayList<GeoPointHorodate> pathPoints = new ArrayList<>();
    private ArrayList<Long> pauseTimestamps = new ArrayList<>(); // [pause1, resume1, pause2, resume2, ...]
    private long startTime = 0;
    private double totalDistance = 0.0; // total distance without pauses
    private GeoPoint lastValidPoint = null; // last point out of break
    private boolean hasValidLastPoint = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startTime = System.currentTimeMillis();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    double latitude = locationResult.getLastLocation().getLatitude();
                    double longitude = locationResult.getLastLocation().getLongitude();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    GeoPointHorodate point = new GeoPointHorodate(latitude, longitude, elapsedTime);
                    pathPoints.add(point);

                    GeoPoint currentPoint = point.getGeoPoint();
                    if (!isPaused) {
                        if (lastValidPoint != null) {
                            double distance = lastValidPoint.distanceToAsDouble(currentPoint);
                            if (hasValidLastPoint) {
                                totalDistance += distance;
                            } else {
                                hasValidLastPoint = true;
                            }
                        }
                        lastValidPoint = currentPoint;
                    } else {
                        hasValidLastPoint = false;
                    }

                    Intent intent = new Intent("LOCATION_UPDATE");
                    intent.putExtra("latitude", latitude);
                    intent.putExtra("longitude", longitude);
                    intent.putExtra("timestamp", elapsedTime);
                    intent.putExtra("totalDistance", totalDistance);
                    LocalBroadcastManager.getInstance(LocationService.this).sendBroadcast(intent);
                }
            }
        };
        startLocationUpdates();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PAUSE":
                    isPaused = true;
                    pauseTimestamps.add(System.currentTimeMillis() - startTime);
                    break;
                case "RESUME":
                    isPaused = false;
                    pauseTimestamps.add(System.currentTimeMillis() - startTime);
                    break;
                case "STOP":
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                case "GET_PATH":
                    sendPathPoints();
                    break;
            }
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Course en cours")
                .setContentText("Suivi GPS actif")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVibrate(null)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        pathPoints.clear();
        pauseTimestamps.clear();
        totalDistance = 0.0;
        lastValidPoint = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Location Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void sendPathPoints() {
        Intent intent = new Intent("PATH_UPDATE");
        intent.putExtra("pathPoints", pathPoints.toArray(new GeoPointHorodate[0]));
        intent.putExtra("pauseTimestamps", pauseTimestamps.toArray(new Long[0]));
        intent.putExtra("totalDistance", totalDistance);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}