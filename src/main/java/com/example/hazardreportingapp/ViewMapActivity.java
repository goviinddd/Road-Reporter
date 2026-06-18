package com.example.hazardreportingapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;

public class ViewMapActivity extends AppCompatActivity {

    private MapView map;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        setContentView(R.layout.activity_view_map);

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 1. Get Hazard Data from Notification
        double hazardLat = getIntent().getDoubleExtra("LAT", 0);
        double hazardLng = getIntent().getDoubleExtra("LNG", 0);
        String label = getIntent().getStringExtra("LABEL");

        if (hazardLat != 0 && hazardLng != 0) {
            GeoPoint hazardPoint = new GeoPoint(hazardLat, hazardLng);

            // 2. Add Hazard Marker
            Marker hazardMarker = new Marker(map);
            hazardMarker.setPosition(hazardPoint);
            hazardMarker.setTitle(label != null ? label.toUpperCase() : "HAZARD");
            hazardMarker.setSnippet("Caution: Incident Reported Here");
            hazardMarker.showInfoWindow();
            map.getOverlays().add(hazardMarker);

            map.getController().setCenter(hazardPoint);

            // 3. Get User Location & Draw Route
            getCurrentLocationAndDrawRoute(hazardPoint);
        }

        Button btnBack = findViewById(R.id.btnBack);
        if(btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void getCurrentLocationAndDrawRoute(GeoPoint hazardPoint) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                GeoPoint userPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

                // Add User Marker
                Marker userMarker = new Marker(map);
                userMarker.setPosition(userPoint);
                userMarker.setTitle("YOU");
                userMarker.setIcon(getResources().getDrawable(org.osmdroid.library.R.drawable.person));
                map.getOverlays().add(userMarker);

                // Draw Route (In background)
                drawRoute(userPoint, hazardPoint);
            }
        });
    }

    private void drawRoute(GeoPoint start, GeoPoint end) {
        new Thread(() -> {
            try {
                RoadManager roadManager = new OSRMRoadManager(this, "MY_USER_AGENT");
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(start);
                waypoints.add(end);

                Road road = roadManager.getRoad(waypoints);
                if (road.mStatus == Road.STATUS_OK) {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.setColor(Color.RED); // Red line for danger!
                    roadOverlay.setWidth(15f);

                    runOnUiThread(() -> {
                        map.getOverlays().add(0, roadOverlay);
                        map.invalidate();
                        Toast.makeText(this, "Displaying incident location", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}