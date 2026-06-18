package com.example.hazardreportingapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;

public class AuthorityDashboardActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HazardAdapter adapter;
    private FirebaseFirestore db;
    private TextView tvTitle;
    private Button btnHistory;
    private Button btnMapAll;

    private String myDepartment;
    private boolean isShowingHistory = false; // Tracks if we are viewing History or Active tasks

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authority_dashboard);

        db = FirebaseFirestore.getInstance();
        myDepartment = getIntent().getStringExtra("MY_DEPARTMENT"); // e.g. "FireForce", "PWD"

        // --- 1. SETUP UI ---
        tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(myDepartment + " Dashboard");

        recyclerView = findViewById(R.id.recyclerView);
// Use the custom wrapper to prevent crashes
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(this));
        // --- 2. LOAD INITIAL DATA (Active Tasks) ---
        loadData(false);

        // --- 3. SITUATION MAP BUTTON (New Feature) ---
        btnMapAll = findViewById(R.id.btnMapAll);
        btnMapAll.setOnClickListener(v -> {
            Intent intent = new Intent(AuthorityDashboardActivity.this, AuthorityMapActivity.class);
            intent.putExtra("MY_DEPARTMENT", myDepartment); // Pass department so map shows only relevant hazards
            startActivity(intent);
        });

        // --- 4. HISTORY TOGGLE BUTTON ---
        btnHistory = findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(v -> {
            isShowingHistory = !isShowingHistory; // Toggle state

            if (isShowingHistory) {
                btnHistory.setText("Back to Active Tasks");
                loadData(true); // Show verified (completed) items
            } else {
                btnHistory.setText("View History Archive");
                loadData(false); // Show pending (active) items
            }
        });

        // --- 5. LOGOUT BUTTON ---
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            // Stop receiving alerts for this department
            if (myDepartment != null) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(myDepartment);
            }

            // Sign out
            FirebaseAuth.getInstance().signOut();

            // Return to Login Screen
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadData(boolean showHistory) {
        Query query;

        if (showHistory) {
            // SHOW HISTORY: Status = 'verified' (Completed/Fixed)
            query = db.collection("hazards")
                    .whereEqualTo("authority", myDepartment)
                    .whereEqualTo("status", "verified")
                    .orderBy("timestamp", Query.Direction.DESCENDING);
        } else {
            // SHOW ACTIVE: Status = 'reviewed' (AI Approved, waiting for fix)
            query = db.collection("hazards")
                    .whereEqualTo("authority", myDepartment)
                    .whereEqualTo("status", "reviewed")
                    .orderBy("timestamp", Query.Direction.DESCENDING);
        }

        FirestoreRecyclerOptions<HazardModel> options = new FirestoreRecyclerOptions.Builder<HazardModel>()
                .setQuery(query, HazardModel.class)
                .build();

        // Swap Adapter
        if (adapter != null) {
            adapter.stopListening();
        }

        adapter = new HazardAdapter(options);
        recyclerView.setAdapter(adapter);
        adapter.startListening();
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
}