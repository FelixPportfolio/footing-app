package com.example.footingtrainmap2;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.room.Room;

import org.json.JSONArray;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Locale;

public class RunActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final double DEFAULT_ZOOM = 15.0;
    private MapView mapView;
    private BroadcastReceiver locationReceiver;
    private BroadcastReceiver pathReceiver;
    private Marker locationMarker;
    private Polyline pathPolyline;
    private ArrayList<GeoPoint> pathPoints;
    private ArrayList<GeoPointHorodate> pathPointsWithTime;
    private ArrayList<Long> pauseTimestamps;
    private Button startButton, pauseButton, stopButton, recenterButton;
    private TextView timeText, avgSpeedText, currentSpeedText, distanceText;
    private boolean isTracking = false;
    private boolean isCameraFollowing = true;
    private long startTimeLocal = 0;
    private Handler timeHandler = new Handler(Looper.getMainLooper());
    private Runnable timeRunnable;
    private RunDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = Room.databaseBuilder(getApplicationContext(), RunDatabase.class, "run-database")
                .allowMainThreadQueries()
                .build();

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("FootingTrainMap2");

        setContentView(R.layout.activity_run);

        mapView = findViewById(R.id.map_view);
        startButton = findViewById(R.id.start_button);
        pauseButton = findViewById(R.id.pause_button);
        stopButton = findViewById(R.id.stop_button);
        recenterButton = findViewById(R.id.recenter_button);
        timeText = findViewById(R.id.time_text);
        avgSpeedText = findViewById(R.id.avg_speed_text);
        currentSpeedText = findViewById(R.id.current_speed_text);
        distanceText = findViewById(R.id.distance_text);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);

        GeoPoint startPoint = new GeoPoint(48.8566, 2.3522);
        locationMarker = new Marker(mapView);
        locationMarker.setPosition(startPoint);
        locationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        locationMarker.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_mylocation));
        mapView.getOverlays().add(locationMarker);

        pathPoints = new ArrayList<>();
        pathPointsWithTime = new ArrayList<>();
        pauseTimestamps = new ArrayList<>();
        pathPolyline = new Polyline();
        pathPolyline.setColor(0xFF0000FF);
        pathPolyline.setWidth(5.0f);
        mapView.getOverlays().add(pathPolyline);

        mapView.getController().setZoom(DEFAULT_ZOOM);
        mapView.getController().setCenter(startPoint);

        Log.d("OSM", "MapView initialized, using data connection: " + mapView.getTileProvider().getTileSource().name());

        mapView.setOnTouchListener((v, event) -> {
            isCameraFollowing = false;
            recenterButton.setVisibility(View.VISIBLE);
            return false;
        });

        recenterButton.setOnClickListener(v -> {
            isCameraFollowing = true;
            recenterButton.setVisibility(View.GONE);
            if (!pathPoints.isEmpty()) {
                GeoPoint lastPoint = pathPoints.get(pathPoints.size() - 1);
                mapView.getController().animateTo(lastPoint, DEFAULT_ZOOM, 1000L);
            }
        });

        startButton.setOnClickListener(v -> startTracking());
        pauseButton.setOnClickListener(v -> pauseTracking());
        stopButton.setOnClickListener(v -> stopTracking());

        updateButtons();

        locationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isTracking) return;
                double latitude = intent.getDoubleExtra("latitude", 0.0);
                double longitude = intent.getDoubleExtra("longitude", 0.0);
                long timestamp = intent.getLongExtra("timestamp", 0L);
                double totalDistance = intent.getDoubleExtra("totalDistance", 0.0);
                Log.d("RunActivity", "Broadcast reçu : " + latitude + ", " + longitude + ", time: " + timestamp);
                if (latitude != 0.0 || longitude != 0.0) {
                    GeoPoint newLocation = new GeoPoint(latitude, longitude);
                    pathPointsWithTime.add(new GeoPointHorodate(latitude, longitude, timestamp));
                    pathPoints.add(newLocation);
                    locationMarker.setPosition(newLocation);
                    if (isCameraFollowing) {
                        mapView.getController().animateTo(newLocation, DEFAULT_ZOOM, 1000L);
                    }
                    pathPolyline.setPoints(pathPoints);
                    mapView.invalidate();
                    distanceText.setText(String.format("Distance: %.2f km", totalDistance / 1000));
                    updateStats();
                    Toast.makeText(RunActivity.this, "Position mise à jour : " + latitude + ", " + longitude + ", time: " + timestamp + "ms", Toast.LENGTH_SHORT).show();
                }
            }
        };

        pathReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                GeoPointHorodate[] points = (GeoPointHorodate[]) intent.getExtras().get("pathPoints");
                Long[] pauseTimes = (Long[]) intent.getExtras().get("pauseTimestamps");
                double totalDistance = intent.getDoubleExtra("totalDistance", 0.0);
                if (points != null) {
                    pathPoints.clear();
                    pathPointsWithTime.clear();
                    for (GeoPointHorodate point : points) {
                        pathPoints.add(point.getGeoPoint());
                        pathPointsWithTime.add(point);
                    }
                    pathPolyline.setPoints(pathPoints);
                    if (isCameraFollowing && !pathPoints.isEmpty()) {
                        GeoPoint lastPoint = pathPoints.get(pathPoints.size() - 1);
                        mapView.getController().animateTo(lastPoint, DEFAULT_ZOOM, 1000L);
                    }
                    mapView.invalidate();
                    Log.d("RunActivity", "Path updated with " + pathPoints.size() + " points");
                }
                if (pauseTimes != null) {
                    pauseTimestamps.clear();
                    for (Long pauseTime : pauseTimes) {
                        pauseTimestamps.add(pauseTime);
                    }
                    Log.d("RunActivity", "Pause timestamps: " + java.util.Arrays.toString(pauseTimes));
                }
                distanceText.setText(String.format("Distance: %.2f km", totalDistance / 1000));
                updateStats();
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE"));
        LocalBroadcastManager.getInstance(this).registerReceiver(pathReceiver, new IntentFilter("PATH_UPDATE"));
    }

    private void startTracking() {
        if (checkLocationPermission()) {
            if (!isServiceRunning(LocationService.class)) {
                Intent serviceIntent = new Intent(this, LocationService.class);
                startService(serviceIntent);
                serviceIntent.setAction("GET_PATH");
                startService(serviceIntent);
                startTimeLocal = System.currentTimeMillis();
                startTimeUpdate();
            } else if (!isTracking) {
                Intent serviceIntent = new Intent(this, LocationService.class);
                serviceIntent.setAction("RESUME");
                startService(serviceIntent);
                startTimeUpdate();
            }
            isTracking = true;
            isCameraFollowing = true;
            recenterButton.setVisibility(View.GONE);
            updateButtons();
        }
    }

    private void pauseTracking() {
        if (isTracking && isServiceRunning(LocationService.class)) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction("PAUSE");
            startService(serviceIntent);
            isTracking = false;
            stopTimeUpdate();
            updateButtons();
        }
    }

    private void stopTracking() {
        if (isServiceRunning(LocationService.class)) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            serviceIntent.setAction("STOP");
            startService(serviceIntent);
            isTracking = false;
            Run run = saveRun();
            Log.d("RunActivity", "Run saved with ID: " + run.id);
            if (run.id != 0) { // Vérifie si l'ID est valide
                Intent intent = new Intent(this, StatsActivity.class);
                intent.putExtra("runId", run.id);
                startActivity(intent);
                finish(); // Fermer après avoir lancé StatsActivity
            } else {
                Log.e("RunActivity", "Failed to save run, ID is 0");
                Toast.makeText(this, "Erreur : Course non sauvegardée", Toast.LENGTH_LONG).show();
            }
            pathPoints.clear();
            pathPointsWithTime.clear();
            pauseTimestamps.clear();
            pathPolyline.setPoints(pathPoints);
            distanceText.setText("Distance: 0.00 km");
            stopTimeUpdate();
            updateButtons();
        }
    }

    private Run saveRun() {
        Run run = new Run();
        run.timestamp = System.currentTimeMillis();
        run.distance = Double.parseDouble(String.format(Locale.US, "%.2f", calculateTotalDistance()));
        run.duration = calculateElapsedTime();
        JSONArray jsonPoints = new JSONArray();
        for (GeoPoint point : pathPoints) {
            jsonPoints.put(String.format(Locale.US, "%f,%f", point.getLatitude(), point.getLongitude()));
        }
        run.points = jsonPoints.toString();
        Log.d("RunActivity", "Saving run: distance=" + run.distance + ", duration=" + run.duration + ", points=" + run.points);
        long insertedId = db.runDao().insert(run); // Capturer l'ID retourné
        run.id = insertedId; // Assigner l'ID à l'objet
        Log.d("RunActivity", "Run inserted, ID assigned: " + run.id);
        return run;
    }

    private void updateButtons() {
        startButton.setEnabled(!isTracking || !isServiceRunning(LocationService.class));
        pauseButton.setEnabled(isTracking && isServiceRunning(LocationService.class));
        stopButton.setEnabled(isServiceRunning(LocationService.class));
    }

    private void updateStats() {
        long elapsedTime = calculateElapsedTime();
        int minutes = (int) (elapsedTime / 1000 / 60);
        int seconds = (int) (elapsedTime / 1000 % 60);
        timeText.setText(String.format("Temps: %02d:%02d", minutes, seconds));

        double totalDistanceFromPoints = calculateTotalDistance();
        double avgSpeed = 0.0;
        if (elapsedTime > 0) {
            avgSpeed = (totalDistanceFromPoints / (elapsedTime / 1000.0)) * 3.6;
        }
        avgSpeedText.setText(String.format("Vitesse moyenne: %.1f km/h", avgSpeed));

        double currentSpeed = calculateCurrentSpeed(3);
        currentSpeedText.setText(String.format("Vitesse actuelle: %.1f km/h", currentSpeed));
    }

    private long calculateElapsedTime() {
        if (startTimeLocal == 0) return 0;
        long currentTime = System.currentTimeMillis();
        long rawElapsed = currentTime - startTimeLocal;
        long pauseDuration = calculatePauseDuration();
        return rawElapsed - pauseDuration;
    }

    private long calculatePauseDuration() {
        long pauseDuration = 0;
        for (int i = 0; i < pauseTimestamps.size() - 1; i += 2) {
            long startPause = pauseTimestamps.get(i);
            long endPause = pauseTimestamps.get(i + 1);
            pauseDuration += (endPause - startPause);
        }
        if (pauseTimestamps.size() % 2 == 1) {
            long lastPauseStart = pauseTimestamps.get(pauseTimestamps.size() - 1);
            long currentTime = System.currentTimeMillis() - startTimeLocal;
            pauseDuration += (currentTime - lastPauseStart);
        }
        return pauseDuration;
    }

    private double calculateTotalDistance() {
        double totalDistance = 0.0;
        for (int i = 1; i < pathPoints.size(); i++) {
            GeoPoint p1 = pathPoints.get(i - 1);
            GeoPoint p2 = pathPoints.get(i);
            totalDistance += p1.distanceToAsDouble(p2);
        }
        return totalDistance;
    }

    private double calculateCurrentSpeed(int numPoints) {
        if (pathPointsWithTime.size() < numPoints) return 0.0;
        int startIndex = pathPointsWithTime.size() - numPoints;
        double distance = 0.0;
        for (int i = startIndex + 1; i < pathPointsWithTime.size(); i++) {
            GeoPoint p1 = pathPointsWithTime.get(i - 1).getGeoPoint();
            GeoPoint p2 = pathPointsWithTime.get(i).getGeoPoint();
            distance += p1.distanceToAsDouble(p2);
        }
        long timeDiff = pathPointsWithTime.get(pathPointsWithTime.size() - 1).getTimestamp() -
                pathPointsWithTime.get(startIndex).getTimestamp();
        if (timeDiff <= 0) return 0.0;
        double speed = (distance / (timeDiff / 1000.0)) * 3.6;
        return speed;
    }

    private void startTimeUpdate() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking) {
                    updateStats();
                    timeHandler.postDelayed(this, 1000);
                }
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void stopTimeUpdate() {
        if (timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                Toast.makeText(this, "Permission de localisation refusée", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        updateButtons();
        if (isServiceRunning(LocationService.class)) {
            Intent intent = new Intent(this, LocationService.class);
            intent.setAction("GET_PATH");
            startService(intent);
            if (isTracking) {
                startTimeUpdate();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (!isTracking) {
            stopTimeUpdate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pathReceiver);
        stopTracking();
        stopTimeUpdate();
    }
}