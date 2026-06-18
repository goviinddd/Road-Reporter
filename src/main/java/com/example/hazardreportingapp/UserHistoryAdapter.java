package com.example.hazardreportingapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UserHistoryAdapter extends FirestoreRecyclerAdapter<HazardModel, UserHistoryAdapter.HistoryHolder> {

    public UserHistoryAdapter(@NonNull FirestoreRecyclerOptions<HazardModel> options) {
        super(options);
    }

    @Override
    protected void onBindViewHolder(@NonNull HistoryHolder holder, int position, @NonNull HazardModel model) {
        holder.tvLabel.setText(model.getDetected_label() != null ? model.getDetected_label().toUpperCase() : "PROCESSING...");

        // Status Styling
        String status = model.getStatus();
        holder.tvStatus.setText("Status: " + (status != null ? status.toUpperCase() : "UNKNOWN"));

        // Apply appropriate colors based on the exact status
        if ("verified".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else if ("failed".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(Color.parseColor("#F44336")); // Red
        } else if ("reviewed".equalsIgnoreCase(status)) {
            holder.tvStatus.setTextColor(Color.parseColor("#2196F3")); // Blue
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#FF9800")); // Orange (Pending)
        }

        // Date
        if (model.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            holder.tvDate.setText(sdf.format(new Date(model.getTimestamp())));
        }

        // Image
        Glide.with(holder.itemView.getContext()).load(model.getImage_url()).into(holder.imgThumb);
    }

    @NonNull
    @Override
    public HistoryHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_history, parent, false);
        return new HistoryHolder(v);
    }

    static class HistoryHolder extends RecyclerView.ViewHolder {
        TextView tvLabel, tvDate, tvStatus;
        ImageView imgThumb;

        public HistoryHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvLabel);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            imgThumb = itemView.findViewById(R.id.imgThumb);
        }
    }
}