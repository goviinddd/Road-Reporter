package com.example.hazardreportingapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportResultActivity extends AppCompatActivity {

    private TextView tvStatusHeader, tvLabel, tvAuthority, tvTimestamp;
    private FirebaseFirestore db;
    private String docId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_result);

        docId = getIntent().getStringExtra("DOC_ID");
        db = FirebaseFirestore.getInstance();

        tvStatusHeader = findViewById(R.id.tvStatusHeader);
        tvLabel = findViewById(R.id.tvLabel);
        tvAuthority = findViewById(R.id.tvAuthority);
        tvTimestamp = findViewById(R.id.tvTimestamp);
        Button btnDone = findViewById(R.id.btnDone);

        startLiveAnalysis();

        btnDone.setOnClickListener(v -> finish());
    }

    private void startLiveAnalysis() {
        if (docId == null) return;

        db.collection("hazards").document(docId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;

                    String status = snapshot.getString("status");
                    String label = snapshot.getString("detected_label");
                    String authority = snapshot.getString("authority");
                    Long timestamp = snapshot.getLong("timestamp");

                    // Wait for Python Backend to finish (status changes to 'reviewed' or 'verified')
                    if ("reviewed".equals(status) || "verified".equals(status)) {

                        tvStatusHeader.setText("Analysis Complete ✅");
                        tvStatusHeader.setTextColor(0xFF4CAF50); // Green

                        tvLabel.setText(label != null ? label.toUpperCase() : "UNKNOWN");
                        tvAuthority.setText(authority != null ? authority : "Admin");

                        // Format Timestamp
                        if (timestamp != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                            tvTimestamp.setText(sdf.format(new Date(timestamp)));
                        } else {
                            tvTimestamp.setText("Unknown Time");
                        }

                    } else {
                        tvStatusHeader.setText("Analyzing Image...");
                        tvLabel.setText("Processing...");
                    }
                });
    }
}