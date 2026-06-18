package com.example.hazardreportingapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION};

    private PreviewView previewView;
    private Button captureButton;
    private ProgressBar progressBar;
    private ImageCapture imageCapture;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FusedLocationProviderClient fusedLocationClient;

    // UI Elements for Confirmation Form
    private ImageView imagePreview;
    private LinearLayout formContainer;
    private Button btnSend, btnDiscard;
    private EditText etDescription, etPhone;

    // Temporary storage for the captured bitmap
    private Bitmap tempBitmapForUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 1. Bind UI Views
        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.captureButton);
        progressBar = findViewById(R.id.progressBar);

        // Form Elements
        imagePreview = findViewById(R.id.imagePreview);
        formContainer = findViewById(R.id.formContainer);
        btnSend = findViewById(R.id.btnSend);
        btnDiscard = findViewById(R.id.btnDiscard);
        etDescription = findViewById(R.id.etDescription);
        etPhone = findViewById(R.id.etPhone);

        // 2. Initialize Firebase & Location
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 3. Request Permissions or Start Camera
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // 4. Setup Button Listeners
        captureButton.setOnClickListener(v -> takePhoto());

        btnDiscard.setOnClickListener(v -> resetCameraUI());

        btnSend.setOnClickListener(v -> {
            showLoadingUI();
            getCurrentLocationAndUpload(tempBitmapForUpload);
        });
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        captureButton.setEnabled(false);
        captureButton.setText("Capturing...");

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull androidx.camera.core.ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();

                tempBitmapForUpload = bitmap;
                showConfirmationUI(bitmap);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                Toast.makeText(CameraActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                resetCameraUI();
            }
        });
    }

    // --- UI Helper Methods ---

    private void showConfirmationUI(Bitmap bitmap) {
        runOnUiThread(() -> {
            previewView.setVisibility(View.GONE);
            captureButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);

            imagePreview.setImageBitmap(bitmap);
            imagePreview.setVisibility(View.VISIBLE);
            formContainer.setVisibility(View.VISIBLE);

            // Auto-fill phone field if they logged in with OTP (optional visual feedback)
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
                etPhone.setText(user.getPhoneNumber());
                etPhone.setEnabled(false); // Lock it so they can't change their verified number
            }
        });
    }

    private void resetCameraUI() {
        runOnUiThread(() -> {
            imagePreview.setVisibility(View.GONE);
            formContainer.setVisibility(View.GONE);
            progressBar.setVisibility(View.GONE);

            etDescription.setText("");

            // Only clear phone if it's not locked to an OTP account
            if (etPhone.isEnabled()) {
                etPhone.setText("");
            }

            previewView.setVisibility(View.VISIBLE);
            captureButton.setVisibility(View.VISIBLE);
            captureButton.setEnabled(true);
            captureButton.setText("Take Photo");

            tempBitmapForUpload = null;
        });
    }

    private void showLoadingUI() {
        runOnUiThread(() -> {
            formContainer.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        });
    }

    // --- Upload Logic ---

    private void getCurrentLocationAndUpload(Bitmap bitmap) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission needed", Toast.LENGTH_SHORT).show();
            resetCameraUI();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        uploadImageToFirebase(bitmap, location);
                    } else {
                        Toast.makeText(this, "Failed to get location. Is GPS on?", Toast.LENGTH_LONG).show();
                        resetCameraUI();
                    }
                });
    }

    private void uploadImageToFirebase(Bitmap bitmap, Location location) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        String filename = "hazard_" + System.currentTimeMillis() + ".jpg";
        StorageReference imageRef = storage.getReference().child("hazard_images/" + filename);

        UploadTask uploadTask = imageRef.putBytes(data);
        uploadTask.addOnFailureListener(exception -> {
            Toast.makeText(this, "Upload Failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            resetCameraUI();
        }).addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            saveToFirestore(uri.toString(), location);
        }));
    }

    private void saveToFirestore(String imageUrl, Location location) {
        String userDesc = etDescription.getText().toString().trim();
        String manualPhone = etPhone.getText().toString().trim();

        // Check for verified Phone Number via Firebase Auth
        String finalPhone = "Anonymous";
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null && currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().isEmpty()) {
            finalPhone = currentUser.getPhoneNumber(); // Enforce the verified phone number
        } else if (!manualPhone.isEmpty()) {
            finalPhone = manualPhone; // Fallback to whatever they typed
        }

        Map<String, Object> hazard = new HashMap<>();

        hazard.put("description", userDesc.isEmpty() ? "No description provided" : userDesc);
        hazard.put("reporter_phone", finalPhone); // Changed key to 'reporter_phone' for your Adapter

        hazard.put("detected_label", "Analyzing...");
        hazard.put("status", "pending");
        hazard.put("latitude", location.getLatitude());
        hazard.put("longitude", location.getLongitude());
        hazard.put("image_url", imageUrl);
        hazard.put("timestamp", System.currentTimeMillis());
        hazard.put("processed_at", FieldValue.serverTimestamp());
        hazard.put("authority", "pending");

        if (currentUser != null) {
            hazard.put("userId", currentUser.getUid());
        }

        db.collection("hazards").add(hazard)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Report Sent!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resetCameraUI();
                });
    }

    // --- Camera Setup ---

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Bitmap imageProxyToBitmap(androidx.camera.core.ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(image.getImageInfo().getRotationDegrees());
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}