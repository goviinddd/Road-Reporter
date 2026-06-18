package com.example.hazardreportingapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private UserHistoryAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";

        // Request location permissions so we can update the user's background location
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        } else {
            updateUserLocationInDatabase();
        }

        // 1. Setup Recycler
        recyclerView = findViewById(R.id.recyclerHistory);
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(this)); // Use your safe wrapper!

        // 2. Load User's History
        Query query = db.collection("hazards")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        FirestoreRecyclerOptions<HazardModel> options = new FirestoreRecyclerOptions.Builder<HazardModel>()
                .setQuery(query, HazardModel.class)
                .build();

        adapter = new UserHistoryAdapter(options);
        recyclerView.setAdapter(adapter);

        // 3. Report Button -> Open CameraActivity
        ExtendedFloatingActionButton btnReport = findViewById(R.id.btnReport);
        btnReport.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivity(intent);
        });

        // 4. Logout Logic
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        Button btnNearbyMap = findViewById(R.id.btnNearbyMap);
        if (btnNearbyMap != null) {
            btnNearbyMap.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, NearbyMapActivity.class);
                startActivity(intent);
            });
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) adapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) adapter.stopListening();
    }
    private void updateUserLocationInDatabase() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient =
                    com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null && mAuth.getCurrentUser() != null) {

                    // Create a map to securely merge the new GPS coordinates
                    java.util.Map<String, Object> locationData = new java.util.HashMap<>();
                    locationData.put("latitude", location.getLatitude());
                    locationData.put("longitude", location.getLongitude());

                    String email = mAuth.getCurrentUser().getEmail();
                    String docId = (email != null && !email.isEmpty()) ? email : mAuth.getCurrentUser().getUid();

                    // Update the users collection silently
                    db.collection("users").document(docId).set(locationData, com.google.firebase.firestore.SetOptions.merge());
                }
            });
        }
    }

    // Catch the permission result and trigger the location update
    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateUserLocationInDatabase();
        }
    }
}