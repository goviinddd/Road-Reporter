package com.example.hazardreportingapp;

import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class AuthorityMapActivity extends AppCompatActivity {

    private MapView map;
    private FirebaseFirestore db;
    private String myDepartment;

    // Default Start Location (e.g., Fire Station HQ)
    // In a real app, you would get this from the phone's GPS
    private GeoPoint startPoint = new GeoPoint(9.9312, 76.2673);
    private Polyline roadOverlay; // Keeps track of the drawn route

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        setContentView(R.layout.activity_view_map);

        db = FirebaseFirestore.getInstance();
        myDepartment = getIntent().getStringExtra("MY_DEPARTMENT");

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);
        map.getController().setCenter(startPoint);

        // Add a "My Location" marker so the user knows where they are starting
        Marker myLoc = new Marker(map);
        myLoc.setPosition(startPoint);
        myLoc.setTitle("MY LOCATION");
        myLoc.setIcon(getResources().getDrawable(org.osmdroid.library.R.drawable.person)); // Uses default person icon
        map.getOverlays().add(myLoc);

        Button btnBack = findViewById(R.id.btnBack);
        if(btnBack != null) btnBack.setOnClickListener(v -> finish());

        loadHazardsOnMap();
    }

    private void loadHazardsOnMap() {
        Toast.makeText(this, "Loading Map...", Toast.LENGTH_SHORT).show();

        db.collection("hazards")
                .whereEqualTo("authority", myDepartment)
                .whereEqualTo("status", "reviewed")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No active hazards found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        String label = doc.getString("detected_label");
                        String desc = doc.getString("description");

                        if (lat != null && lng != null) {
                            GeoPoint hazardPoint = new GeoPoint(lat, lng);

                            Marker marker = new Marker(map);
                            marker.setPosition(hazardPoint);
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            marker.setTitle(label != null ? label.toUpperCase() : "HAZARD");

                            String snippet = (desc != null && !desc.isEmpty()) ? desc : "No details";
                            marker.setSnippet(snippet + "\n(Tap again to SHOW ROUTE)");

                            // --- CLICK LISTENER FOR ROUTING ---
                            marker.setOnMarkerClickListener((m, mapView) -> {
                                if (m.isInfoWindowShown()) {
                                    // If info window is already open, Draw Route!
                                    drawRoute(hazardPoint);
                                    m.closeInfoWindow();
                                    return true;
                                } else {
                                    m.showInfoWindow();
                                    return true;
                                }
                            });

                            map.getOverlays().add(marker);
                        }
                    }
                    map.invalidate();
                });
    }

    // --- NEW FEATURE: IN-APP ROUTING ---
    private void drawRoute(GeoPoint endPoint) {
        Toast.makeText(this, "Calculating Route...", Toast.LENGTH_SHORT).show();

        // Routing requires network, so it MUST run on a background thread
        new Thread(() -> {
            try {
                // 1. Initialize the Road Manager (OSRM is free)
                RoadManager roadManager = new OSRMRoadManager(this, "MY_USER_AGENT");

                // 2. Define Waypoints (Start -> End)
                ArrayList<GeoPoint> waypoints = new ArrayList<>();
                waypoints.add(startPoint);
                waypoints.add(endPoint);

                // 3. Get the Road
                Road road = roadManager.getRoad(waypoints);

                // 4. Update UI on Main Thread
                runOnUiThread(() -> {
                    if (road.mStatus != Road.STATUS_OK) {
                        Toast.makeText(this, "Error: Cannot find road!", Toast.LENGTH_SHORT).show();
                    } else {
                        // Remove old route if exists
                        if (roadOverlay != null) {
                            map.getOverlays().remove(roadOverlay);
                        }

                        // Draw new route
                        roadOverlay = RoadManager.buildRoadOverlay(road);
                        roadOverlay.setWidth(15f); // Thicker line
                        roadOverlay.setColor(Color.BLUE); // Blue Route

                        map.getOverlays().add(0, roadOverlay); // Add at index 0 so markers stay on top
                        map.invalidate(); // Refresh map

                        // Show distance/duration
                        String info = String.format("Distance: %.1f km, Time: %.1f min", road.mLength, road.mDuration / 60);
                        Toast.makeText(this, info, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}