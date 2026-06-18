package com.example.hazardreportingapp;

import java.util.Date;

public class HazardModel {
    private String detected_label;
    private String authority;
    private String status;
    private String image_url;
    private String description;

    // FIX: Name perfectly matches the Firestore database field
    private String reporter_phone;

    // FIX: Using Object Wrappers (Double, Long) so Firebase doesn't crash when these are missing!
    private Double latitude;
    private Double longitude;
    private Long timestamp;
    private Double confidence;

    private Date processed_at;
    private String userId;

    public HazardModel() {} // Required empty constructor

    // Getters
    public String getDetected_label() { return detected_label; }
    public String getAuthority() { return authority; }
    public String getStatus() { return status; }
    public String getImage_url() { return image_url; }
    public String getDescription() { return description; }

    // FIX: Updated Getter and Setter to resolve the crash
    public String getReporter_phone() { return reporter_phone; }
    public void setReporter_phone(String reporter_phone) { this.reporter_phone = reporter_phone; }

    // FIX: Safely return 0 if the field is null to prevent UI crashes
    public double getLatitude() { return latitude != null ? latitude : 0.0; }
    public double getLongitude() { return longitude != null ? longitude : 0.0; }
    public long getTimestamp() { return timestamp != null ? timestamp : 0L; }
    public double getConfidence() { return confidence != null ? confidence : 0.0; }

    public Date getProcessed_at() { return processed_at; }
    public String getUserId() { return userId; }
}