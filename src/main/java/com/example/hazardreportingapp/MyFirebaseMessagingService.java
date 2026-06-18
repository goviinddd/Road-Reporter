package com.example.hazardreportingapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // 1. Check if the message contains data (Lat/Lng)
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String latStr = data.get("latitude");
            String lngStr = data.get("longitude");
            String label = data.get("label");

            if (latStr != null && lngStr != null) {
                double hazardLat = Double.parseDouble(latStr);
                double hazardLng = Double.parseDouble(lngStr);

                // 2. Check 5km Radius (Filter)
                if (isWithinRange(hazardLat, hazardLng, 5000)) { // 5000 meters = 5km
                    sendNotification("Hazard Alert!", "Caution: " + label + " detected nearby.", hazardLat, hazardLng, label);
                }
            }
        }
    }

    // --- 5KM LOGIC ---
    private boolean isWithinRange(double hLat, double hLng, float radiusMeters) {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Permission Check
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return true; // Default to TRUE if we can't check (Safety first: show alert)
        }

        Location myLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (myLoc == null) myLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (myLoc != null) {
            float[] results = new float[1];
            Location.distanceBetween(myLoc.getLatitude(), myLoc.getLongitude(), hLat, hLng, results);
            return results[0] <= radiusMeters;
        }
        return true; // Fallback
    }

    // --- NOTIFICATION BUILDER ---
    private void sendNotification(String title, String message, double lat, double lng, String label) {
        String channelId = "hazard_alerts";

        // 1. Create Intent to Open Map
        Intent intent = new Intent(this, ViewMapActivity.class);
        intent.putExtra("LAT", lat);
        intent.putExtra("LNG", lng);
        intent.putExtra("LABEL", label);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 2. Create PendingIntent (The Click Action)
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // 3. Build Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent); // <--- Attach intent here

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Android O+ Channel Requirement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Hazard Alerts", NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}