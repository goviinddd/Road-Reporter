package com.example.hazardreportingapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {

    // Email Views (Authorities Only)
    private EditText etEmail, etPassword;
    private Button btnLoginEmail;

    // Phone Views (Citizens)
    private LinearLayout layoutPhone, layoutOTP;
    private EditText etPhone, etOTP;
    private Button btnSendOTP, btnVerifyOTP;

    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private String mVerificationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // 1. BIND THE PROGRESS BAR FIRST!
        progressBar = findViewById(R.id.progressBar);

        // 2. Check if already logged in
        if (mAuth.getCurrentUser() != null) {
            goToDashboard();
            return;
        }

        // 3. Bind Email Views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLoginEmail = findViewById(R.id.btnLoginEmail);

        // 4. Bind Phone Views
        layoutPhone = findViewById(R.id.layoutPhone);
        layoutOTP = findViewById(R.id.layoutOTP);
        etPhone = findViewById(R.id.etPhone);
        etOTP = findViewById(R.id.etOTP);
        btnSendOTP = findViewById(R.id.btnSendOTP);
        btnVerifyOTP = findViewById(R.id.btnVerifyOTP);

        // ==========================================
        // 1. AUTHORITY EMAIL LOGIN LOGIC
        // ==========================================
        btnLoginEmail.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter department email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            goToDashboard();
                        } else {
                            Toast.makeText(this, "Authority Login Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // ==========================================
        // 2. CITIZEN PHONE OTP LOGIC
        // ==========================================
        btnSendOTP.setOnClickListener(v -> {
            String phone = etPhone.getText().toString().trim();
            if (phone.isEmpty() || phone.length() < 10) {
                Toast.makeText(this, "Enter valid phone with country code (e.g. +91...)", Toast.LENGTH_SHORT).show();
                return;
            }
            sendVerificationCode(phone);
        });

        btnVerifyOTP.setOnClickListener(v -> {
            String code = etOTP.getText().toString().trim();
            if (code.isEmpty() || code.length() < 6) {
                Toast.makeText(this, "Enter the 6-digit code", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyCode(code);
        });
    }

    // --- Phone Auth Helper Methods ---

    private void sendVerificationCode(String phoneNumber) {
        progressBar.setVisibility(View.VISIBLE);
        btnSendOTP.setEnabled(false);

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(mCallbacks)
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    signInWithPhoneAuthCredential(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    progressBar.setVisibility(View.GONE);
                    btnSendOTP.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Verification Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    progressBar.setVisibility(View.GONE);
                    mVerificationId = verificationId;

                    layoutPhone.setVisibility(View.GONE);
                    layoutOTP.setVisibility(View.VISIBLE);
                    Toast.makeText(LoginActivity.this, "OTP Sent via SMS!", Toast.LENGTH_SHORT).show();
                }
            };

    private void verifyCode(String code) {
        try {
            progressBar.setVisibility(View.VISIBLE);
            btnVerifyOTP.setEnabled(false);

            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
            signInWithPhoneAuthCredential(credential);

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            btnVerifyOTP.setEnabled(true);
            Toast.makeText(this, "Error generating credential: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    btnVerifyOTP.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        goToDashboard();
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown Error";
                        Toast.makeText(LoginActivity.this, "Login Failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // --- Navigation & Routing ---
    private void goToDashboard() {
        if (mAuth.getCurrentUser() == null) return;

        progressBar.setVisibility(View.VISIBLE);

        String email = mAuth.getCurrentUser().getEmail();
        String documentIdToSearch = (email != null && !email.isEmpty()) ? email : mAuth.getCurrentUser().getUid();

        // 1. FETCH THE FRESH FCM TOKEN FIRST
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String fcmToken = task.isSuccessful() ? task.getResult() : null;
                    proceedWithRouting(documentIdToSearch, fcmToken);
                });
    }

    private void proceedWithRouting(String documentIdToSearch, String fcmToken) {
        FirebaseFirestore.getInstance().collection("users").document(documentIdToSearch).get()
                .addOnSuccessListener(documentSnapshot -> {
                    progressBar.setVisibility(View.GONE);
                    Intent intent;

                    // 2. PREPARE TOKEN UPDATE FOR DATABASE
                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                    if (fcmToken != null) {
                        updates.put("fcm_token", fcmToken);
                    }

                    if (documentSnapshot.exists() && documentSnapshot.contains("role")) {
                        String rawRole = documentSnapshot.getString("role");
                        String roleCheck = rawRole != null ? rawRole.toUpperCase() : "";

                        if (roleCheck.equals("PWD") || roleCheck.equals("KSEB") || roleCheck.equals("FIREFORCE")) {
                            String exactDepartmentName = roleCheck;
                            if (roleCheck.equals("FIREFORCE")) exactDepartmentName = "FireForce";

                            // 3. SUBSCRIBE AUTHORITY TO THEIR TOPIC NOTIFICATIONS
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(exactDepartmentName);

                            intent = new Intent(LoginActivity.this, AuthorityDashboardActivity.class);
                            intent.putExtra("MY_DEPARTMENT", exactDepartmentName);
                        } else {
                            intent = new Intent(LoginActivity.this, MainActivity.class);
                        }
                    } else {
                        intent = new Intent(LoginActivity.this, MainActivity.class);
                    }

                    // 4. SAVE THE NEW TOKEN TO FIRESTORE (Using merge to avoid deleting roles)
                    if (!updates.isEmpty()) {
                        FirebaseFirestore.getInstance().collection("users")
                                .document(documentIdToSearch)
                                .set(updates, com.google.firebase.firestore.SetOptions.merge());
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this, "Error checking role: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}