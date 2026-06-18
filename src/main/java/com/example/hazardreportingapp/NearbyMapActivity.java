package com.example.hazardreportingapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class NearbyMapActivity extends AppCompatActivity {

    private MapView map;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private Location currentUserLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. IMPORTANT: Initialize OSM Configuration BEFORE setting the content view!
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        // Set user agent to prevent getting banned from OSM servers
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_nearby_map);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 2. Setup the Map
        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);

        checkLocationAndLoadMap();
    }

    private void checkLocationAndLoadMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }

        // 3. Get User's Current Location
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentUserLocation = location;
                GeoPoint myPos = new GeoPoint(location.getLatitude(), location.getLongitude());

                // Center camera on user
                map.getController().setCenter(myPos);

                // Drop a blue pin for the User
                Marker userMarker = new Marker(map);
                userMarker.setPosition(myPos);
                userMarker.setTitle("You are here");
                // Optional: You can set a custom icon for the user here if you want
                map.getOverlays().add(userMarker);

                // 4. Fetch Hazards and Plot them
                fetchAndPlotNearbyHazards();
            } else {
                Toast.makeText(this, "Could not get current location.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndPlotNearbyHazards() {
        db.collection("hazards")
                .whereNotEqualTo("status", "verified")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Double hazardLat = document.getDouble("latitude");
                        Double hazardLng = document.getDouble("longitude");
                        String label = document.getString("detected_label");
                        String status = document.getString("status");

                        if (hazardLat != null && hazardLng != null) {
                            double distanceKm = calculateDistance(
                                    currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
                                    hazardLat, hazardLng
                            );

                            // If within 5 KM, plot it!
                            if (distanceKm <= 5.0) {
                                GeoPoint hazardPos = new GeoPoint(hazardLat, hazardLng);

                                Marker hazardMarker = new Marker(map);
                                hazardMarker.setPosition(hazardPos);
                                hazardMarker.setTitle(label != null ? label.toUpperCase() : "HAZARD");
                                hazardMarker.setSnippet("Status: " + status + " | " + String.format("%.1f", distanceKm) + " km away");

                                // You can use osmdroid's default marker, or load a custom drawable
                                // hazardMarker.setIcon(getResources().getDrawable(R.drawable.your_red_pin));

                                map.getOverlays().add(hazardMarker);
                            }
                        }
                    }
                    // Refresh the map to show the new markers
                    map.invalidate();
                })
                .addOnFailureListener(e -> Toast.makeText(NearbyMapActivity.this, "Failed to load hazards", Toast.LENGTH_SHORT).show());
    }

    // --- JAVA HAVERSINE FORMULA ---
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // --- Required Map Lifecycle Methods ---
    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
}