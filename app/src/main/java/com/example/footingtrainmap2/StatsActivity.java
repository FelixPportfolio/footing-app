package com.example.footingtrainmap2;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import org.json.JSONArray;
import org.json.JSONException;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private RunDatabase db;
    private MapView mapView;
    private List<GeoPoint> pathPoints = new ArrayList<>();
    private List<Double> speeds = new ArrayList<>();
    private double minSpeed = Double.MAX_VALUE;
    private double maxSpeed = Double.MIN_VALUE;
    private TextView selectedSpeedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("FootingTrainMap2");

        setContentView(R.layout.activity_stats);

        db = Room.databaseBuilder(getApplicationContext(), RunDatabase.class, "run-database")
                .allowMainThreadQueries()
                .build();

        long runId = getIntent().getLongExtra("runId", -1);
        if (runId == -1) {
            Toast.makeText(this, "Erreur : ID de la course non trouvé", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Run run = db.runDao().getRunById(runId);
        if (run == null) {
            Toast.makeText(this, "Erreur : Course non trouvée", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            JSONArray jsonPoints = new JSONArray(run.points);
            for (int i = 0; i < jsonPoints.length(); i++) {
                String[] coords = jsonPoints.getString(i).split(",");
                double latitude = Double.parseDouble(coords[0]);
                double longitude = Double.parseDouble(coords[1]);
                pathPoints.add(new GeoPoint(latitude, longitude));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur lors du chargement des points", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        calculateSpeeds();

        mapView = findViewById(R.id.stats_map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);

        drawPathWithSpeedGradient();

        // center and zoom on the race
        if (!pathPoints.isEmpty()) {
            BoundingBox boundingBox = BoundingBox.fromGeoPoints(pathPoints);
            // add a margin of 10%
            double latSpan = boundingBox.getLatitudeSpan();
            double lonSpan = boundingBox.getLongitudeSpan();
            double latMargin = latSpan * 0.1;
            double lonMargin = lonSpan * 0.1;

            BoundingBox adjustedBox = new BoundingBox(
                    boundingBox.getLatNorth() + latMargin,
                    boundingBox.getLonEast() + lonMargin,
                    boundingBox.getLatSouth() - latMargin,
                    boundingBox.getLonWest() - lonMargin
            );

            mapView.zoomToBoundingBox(adjustedBox, true);

            // limit minimum zoom
            double currentZoom = mapView.getZoomLevelDouble();
            if (currentZoom < 10.0) {
                mapView.getController().setZoom(10.0);
                mapView.getController().setCenter(adjustedBox.getCenterWithDateLine());
            }
        }

        TextView titleText = findViewById(R.id.stats_title);
        TextView distanceText = findViewById(R.id.stats_distance);
        TextView durationText = findViewById(R.id.stats_duration);
        TextView avgSpeedText = findViewById(R.id.stats_avg_speed);
        TextView speedRangeText = findViewById(R.id.speed_range);
        selectedSpeedText = findViewById(R.id.selected_speed);

        titleText.setText(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(run.timestamp)));
        distanceText.setText(String.format("Distance: %.2f km", run.distance / 1000));
        durationText.setText(String.format("Durée: %02d:%02d", run.duration / 1000 / 60, (run.duration / 1000) % 60));
        double avgSpeed = run.duration > 0 ? (run.distance / (run.duration / 1000.0)) * 3.6 : 0.0;
        avgSpeedText.setText(String.format("Vitesse moyenne: %.1f km/h", avgSpeed));
        speedRangeText.setText(String.format("%.1f km/h - %.1f km/h", minSpeed, maxSpeed));
    }

    private void calculateSpeeds() {
        final long INTERVAL_MS = 5000;
        for (int i = 1; i < pathPoints.size(); i++) {
            GeoPoint p1 = pathPoints.get(i - 1);
            GeoPoint p2 = pathPoints.get(i);
            double distance = p1.distanceToAsDouble(p2);
            double speed = (distance / (INTERVAL_MS / 1000.0)) * 3.6;
            speeds.add(speed);

            if (speed < minSpeed) minSpeed = speed;
            if (speed > maxSpeed) maxSpeed = speed;
        }

        if (speeds.isEmpty()) {
            minSpeed = 0.0;
            maxSpeed = 0.0;
        }
    }

    private void drawPathWithSpeedGradient() {
        if (pathPoints.size() < 2) return;

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            List<GeoPoint> segment = new ArrayList<>();
            segment.add(pathPoints.get(i));
            segment.add(pathPoints.get(i + 1));

            Polyline line = new Polyline();
            line.setPoints(segment);

            double speed = speeds.get(i);
            int color = interpolateColor(speed, minSpeed, maxSpeed);
            line.setColor(color);
            line.setWidth(5.0f);
            mapView.getOverlays().add(line);
        }
        mapView.invalidate();
    }

    private int interpolateColor(double speed, double minSpeed, double maxSpeed) {
        if (maxSpeed == minSpeed) return Color.BLUE;

        double ratio = (speed - minSpeed) / (maxSpeed - minSpeed);

        int red = (int) (255 * ratio);
        int blue = (int) (255 * (1 - ratio));
        return Color.rgb(red, 0, blue);
    }

    private void findNearestSpeed(GeoPoint clickedPoint) {
        if (pathPoints.size() < 2) return;

        double minDistance = Double.MAX_VALUE;
        int nearestSegmentIndex = -1;

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            GeoPoint p1 = pathPoints.get(i);
            GeoPoint p2 = pathPoints.get(i + 1);

            double distance = pointToSegmentDistance(clickedPoint, p1, p2);
            if (distance < minDistance) {
                minDistance = distance;
                nearestSegmentIndex = i;
            }
        }

        if (minDistance < 50 && nearestSegmentIndex >= 0) {
            double speed = speeds.get(nearestSegmentIndex);
            selectedSpeedText.setText(String.format("Vitesse au point: %.1f km/h", speed));
        } else {
            selectedSpeedText.setText("");
        }
    }

    private double pointToSegmentDistance(GeoPoint p, GeoPoint a, GeoPoint b) {
        double len2 = Math.pow(a.distanceToAsDouble(b), 2);
        if (len2 == 0) return p.distanceToAsDouble(a);

        double t = Math.max(0, Math.min(1, dotProduct(p, a, b) / len2));
        GeoPoint projection = new GeoPoint(a.getLatitude() + t * (b.getLatitude() - a.getLatitude()),
                a.getLongitude() + t * (b.getLongitude() - a.getLongitude()));
        return p.distanceToAsDouble(projection);
    }

    private double dotProduct(GeoPoint p, GeoPoint a, GeoPoint b) {
        return (p.getLatitude() - a.getLatitude()) * (b.getLatitude() - a.getLatitude()) +
                (p.getLongitude() - a.getLongitude()) * (b.getLongitude() - a.getLongitude());
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}