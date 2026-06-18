package com.example.hazardreportingapp;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HazardAdapter extends FirestoreRecyclerAdapter<HazardModel, HazardAdapter.HazardHolder> {

    public HazardAdapter(@NonNull FirestoreRecyclerOptions<HazardModel> options) {
        super(options);
    }

    @Override
    protected void onBindViewHolder(@NonNull HazardHolder holder, int position, @NonNull HazardModel model) {
        // 1. Set Basic Info
        holder.tvLabel.setText(model.getDetected_label().toUpperCase());
        holder.tvStatus.setText("Status: " + model.getStatus());

        // 2. Set Description
        if (model.getDescription() != null && !model.getDescription().isEmpty()) {
            holder.tvDescription.setText("Note: " + model.getDescription());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // 3. Format Date
        if (model.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
            holder.tvDate.setText(sdf.format(new Date(model.getTimestamp())));
        }

        // 4. Load Image
        Glide.with(holder.itemView.getContext())
                .load(model.getImage_url())
                .into(holder.imgHazard);

        // 5. Display Phone Number
        String phone = model.getReporter_phone();
        if (phone != null && !phone.isEmpty()) {
            holder.tvPhone.setText("Contact: " + phone);
            holder.tvPhone.setVisibility(View.VISIBLE);
        } else {
            holder.tvPhone.setText("Contact: N/A");
        }

        // 6. MAP BUTTON
        holder.btnNavigate.setText("View on Map");
        holder.btnNavigate.setOnClickListener(v -> {
            Intent intent = new Intent(holder.itemView.getContext(), ViewMapActivity.class);
            intent.putExtra("LAT", model.getLatitude());
            intent.putExtra("LNG", model.getLongitude());
            intent.putExtra("LABEL", model.getDetected_label());
            holder.itemView.getContext().startActivity(intent);
        });

        // 7. Verify Button
        // 7. Verify Button Visibility & Action
        if ("verified".equalsIgnoreCase(model.getStatus())) {
            // If it is in the history archive, hide the button completely
            holder.btnVerify.setVisibility(View.GONE);
        } else {
            // If it is an active task, show the button and make it clickable
            holder.btnVerify.setVisibility(View.VISIBLE);
            holder.btnVerify.setOnClickListener(v -> {
                DocumentSnapshot snapshot = getSnapshots().getSnapshot(position);
                snapshot.getReference().update("status", "verified")
                        .addOnSuccessListener(aVoid -> Toast.makeText(holder.itemView.getContext(), "Task Verified!", Toast.LENGTH_SHORT).show());
            });
        }
    }

    @NonNull
    @Override
    public HazardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hazard, parent, false);
        return new HazardHolder(v);
    }

    public static class HazardHolder extends RecyclerView.ViewHolder {
        TextView tvLabel, tvStatus, tvDate, tvDescription, tvPhone; // <-- Added tvPhone
        ImageView imgHazard;
        Button btnNavigate, btnVerify;

        public HazardHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvLabel);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvDescription = itemView.findViewById(R.id.tvDescription);

            // <-- Bind the phone TextView here
            tvPhone = itemView.findViewById(R.id.tvPhone);

            imgHazard = itemView.findViewById(R.id.imgHazard);
            btnNavigate = itemView.findViewById(R.id.btnNavigate);
            btnVerify = itemView.findViewById(R.id.btnVerify);
        }
    }
}