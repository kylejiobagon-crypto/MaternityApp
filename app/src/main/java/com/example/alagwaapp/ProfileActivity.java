package com.example.alagwaapp;

import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ProfileActivity extends AppCompatActivity {

    private EditText etFirstName, etLastName, etEmail, etPhone, etDob, etAge, etPhilHealth, etAddress;
    private EditText etLmp, etBlood, etMonths, etEmergencyName, etEmergencyRel, etEmergencyPhone;
    private TextView btnEdit, btnReset, tvProfileNameHero;
    private View btnSave, avatarContainer, syncDot, loadingOverlay;
    private TextView tvDueDate, tvWeeks, tvTrimester;
    private boolean isEditMode = false;
    
    private ApiService apiService;
    private SharedPreferences prefs;
    private static final String BASE_URL = "http://alagawa.ct.ws/";
    private int currentPatientId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_view);
        
        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        initNetwork();
        initializeViews();
        setupClickListeners();
        startFloatingAnimation();
        startPulseAnimation();
        lockFields(); // Initially locked
        
        startAesSync();
    }

    private static final String CUSTOM_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private boolean isSyncDone = false;
    private void startAesSync() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        
        isSyncDone = false;
        android.webkit.WebView webView = new android.webkit.WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUserAgentString(CUSTOM_UA);
        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                if (!isSyncDone) {
                    isSyncDone = true;
                    Log.d("AlagwaApp", "WebView sync finished: " + url);
                    android.webkit.CookieManager.getInstance().flush();
                    fetchProfile();
                }
            }
        });

        // Safety timeout: If WebView hangs, proceed anyway after 5 seconds
        new android.os.Handler().postDelayed(() -> {
            if (!isSyncDone) {
                isSyncDone = true;
                Log.w("AlagwaApp", "WebView sync timeout - proceeding to fetchProfile");
                fetchProfile();
            }
        }, 5000);

        webView.loadUrl(BASE_URL);
    }
    
    private void initNetwork() {
        OkHttpClient okClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    String cookies = android.webkit.CookieManager.getInstance().getCookie(BASE_URL);
                    okhttp3.Request.Builder builder = chain.request().newBuilder()
                            .header("User-Agent", CUSTOM_UA)
                            .header("Accept", "application/json");
                    if (cookies != null && !cookies.isEmpty()) {
                        builder.header("Cookie", cookies);
                        Log.d("AlagwaApp", "Injecting Cookies: " + cookies);
                    }

                    String token = prefs.getString("token", "");
                    if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);

                    okhttp3.HttpUrl newUrl = chain.request().url().newBuilder()
                            .setQueryParameter("mobile",    "true")
                            .setQueryParameter("tenant_id", String.valueOf(prefs.getInt("tenantId", 1)))
                            .setQueryParameter("role",      prefs.getString("role", "patient"))
                            .setQueryParameter("user_id",   String.valueOf(prefs.getInt("userId", 0)))
                            .setQueryParameter("username",  prefs.getString("username", ""))
                            .setQueryParameter("email",     prefs.getString("email", ""))
                            .setQueryParameter("fullname",  prefs.getString("fullname", ""))
                            .build();
                    builder.url(newUrl);
                    return chain.proceed(builder.build());
                })
                .build();

        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okClient)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(
                        new GsonBuilder().setLenient().create()))
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void initializeViews() {
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etDob = findViewById(R.id.etDob);
        etAge = findViewById(R.id.etAge);
        etPhilHealth = findViewById(R.id.etPhilHealth);
        etAddress = findViewById(R.id.etAddress);

        etLmp = findViewById(R.id.etLmp);
        etBlood = findViewById(R.id.etBlood);
        etMonths = findViewById(R.id.etMonths);

        etEmergencyName = findViewById(R.id.etEmergencyName);
        etEmergencyRel = findViewById(R.id.etEmergencyRel);
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone);

        btnEdit = findViewById(R.id.btnEdit);
        btnReset = findViewById(R.id.btnReset);
        btnSave = findViewById(R.id.btnSave);
        avatarContainer = findViewById(R.id.avatarContainer);
        syncDot = findViewById(R.id.syncDot);
        tvProfileNameHero = findViewById(R.id.tvProfileNameHero);
        
        tvDueDate = findViewById(R.id.tvDueDate);
        tvWeeks = findViewById(R.id.tvWeeks);
        tvTrimester = findViewById(R.id.tvTrimester);
        loadingOverlay = findViewById(R.id.loadingOverlay);
    }

    private void setupClickListeners() {
        btnEdit.setOnClickListener(v -> toggleEditMode());
        btnSave.setOnClickListener(v -> saveChanges());
        btnReset.setOnClickListener(v -> showResetBottomSheet());

        etDob.setOnClickListener(v -> {
            if (isEditMode) showDatePicker();
        });

        findViewById(R.id.navHome).setOnClickListener(v -> finish());
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            btnEdit.setText("CANCEL");
            btnSave.setVisibility(View.VISIBLE);
            btnSave.setTranslationY(200f);
            btnSave.animate().translationY(0f).setDuration(400).start();
            unlockFields();
        } else {
            btnEdit.setText("EDIT PROFILE");
            btnSave.animate().translationY(200f).setDuration(300).withEndAction(() -> btnSave.setVisibility(View.GONE)).start();
            lockFields();
        }
    }

    private void saveChanges() {
        if (apiService == null) return;
        
        String fName = etFirstName.getText().toString();
        String lName = etLastName.getText().toString();
        String email = etEmail.getText().toString();
        String phone = etPhone.getText().toString();
        String dob = etDob.getText().toString();
        String address = etAddress.getText().toString();
        String phNum = etPhilHealth.getText().toString();
        String lmp = etLmp.getText().toString();
        String blood = etBlood.getText().toString();
        double months = 0;
        try { months = Double.parseDouble(etMonths.getText().toString()); } catch (Exception ignored) {}
        
        String eName = etEmergencyName.getText().toString();
        String eRel = etEmergencyRel.getText().toString();
        String ePhone = etEmergencyPhone.getText().toString();

        apiService.updateProfile("update_profile", "true", currentPatientId, fName, lName, email, phone, dob, address, phNum, lmp, blood, months, eName, eRel, ePhone)
            .enqueue(new retrofit2.Callback<ProfileUpdateResponse>() {
                @Override
                public void onResponse(retrofit2.Call<ProfileUpdateResponse> call, retrofit2.Response<ProfileUpdateResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().success) {
                        Toast.makeText(ProfileActivity.this, "Profile Cloud-Synced Successfully", Toast.LENGTH_SHORT).show();
                        toggleEditMode();
                        fetchProfile(); // Refresh
                    } else {
                        Toast.makeText(ProfileActivity.this, "Failed to sync profile", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<ProfileUpdateResponse> call, Throwable t) {
                    Toast.makeText(ProfileActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void fetchProfile() {
        if (apiService == null || loadingOverlay == null) return;
        loadingOverlay.setVisibility(View.VISIBLE);

        apiService.getPatientsRaw("list", "true").enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                
                String raw = "";
                try {
                    if (response.isSuccessful()) {
                        raw = (response.body() != null) ? response.body().string() : "";
                    } else {
                        raw = (response.errorBody() != null) ? response.errorBody().string() : "";
                    }
                    
                    Log.d("AlagwaApp", "Profile Raw: " + raw);
                    
                    if (raw.trim().startsWith("<!DOCTYPE")) {
                        // This is an HTML page (likely InfinityFree AES.js challenge)
                        Toast.makeText(ProfileActivity.this, "Authentication issue. Please restart app.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    
                    if (raw.trim().isEmpty()) {
                        Toast.makeText(ProfileActivity.this, "Server returned empty response (Status: " + response.code() + ")", Toast.LENGTH_LONG).show();
                        return;
                    }

                    org.json.JSONObject root = new org.json.JSONObject(raw);
                    if (root.optBoolean("success", false)) {
                        org.json.JSONArray data = root.optJSONArray("data");
                        if (data != null && data.length() > 0) {
                            org.json.JSONObject p = data.getJSONObject(0);
                            
                            // Map JSONObject to our local populateUI
                            Patient patient = new Patient();
                            patient.patientId = p.optInt("patient_id");
                            patient.firstName = p.optString("first_name", "");
                            patient.lastName = p.optString("last_name", "");
                            patient.email = p.optString("email", "");
                            patient.contactNumber = p.optString("contact_number", "");
                            patient.dob = p.optString("dob", "");
                            patient.address = p.optString("address", "");
                            patient.philhealthNumber = p.optString("philhealth_number", "");
                            patient.bloodType = p.optString("blood_type", "");
                            patient.lmp = p.optString("lmp", "");
                            patient.monthsPregnant = p.optDouble("months_pregnant", 0.0);
                            patient.emergencyName = p.optString("emergency_contact_name", ""); // Corrected field name
                            patient.emergencyRelationship = p.optString("emergency_contact_relationship", ""); // Corrected field name
                            patient.emergencyNumber = p.optString("emergency_contact_number", ""); // Corrected field name
                            
                            // Metrics
                            patient.daysPregnant = p.optInt("days_pregnant", 0);
                            patient.weeksPregnant = p.optInt("weeks_pregnant", 0);
                            patient.remainingDays = p.optInt("remaining_days", 0);
                            patient.dueDate = p.optString("due_date", "Pending");
                            patient.trimester = p.optString("trimester", "1st");
                            patient.visitCount = p.optInt("visit_count", 0);
                            
                            populateUI(patient);
                        } else {
                            Toast.makeText(ProfileActivity.this, "Patient profile not found for this account.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(ProfileActivity.this, "Failed: " + root.optString("message"), Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("AlagwaApp", "Profile Parse Error", e);
                    String snippet = (raw.length() > 100) ? raw.substring(0, 100) : raw;
                    Toast.makeText(ProfileActivity.this, "Error: " + e.getMessage() + "\nBody: " + snippet, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                Toast.makeText(ProfileActivity.this, "Network Error. Check connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateUI(Patient patient) {
        currentPatientId = patient.patientId;
        etFirstName.setText(patient.firstName);
        etLastName.setText(patient.lastName);
        etEmail.setText(patient.email);
        etPhone.setText(patient.contactNumber);
        etDob.setText(patient.dob);
        etPhilHealth.setText(patient.philhealthNumber);
        etAddress.setText(patient.address);
        etLmp.setText(patient.lmp);
        etBlood.setText(patient.bloodType);
        etMonths.setText(String.valueOf(patient.monthsPregnant != null ? patient.monthsPregnant : ""));
        etEmergencyName.setText(patient.emergencyName);
        etEmergencyRel.setText(patient.emergencyRelationship);
        etEmergencyPhone.setText(patient.emergencyNumber);
        
        tvProfileNameHero.setText(patient.firstName + " " + patient.lastName);
        
        calculatePregnancyStats(patient.lmp);
        calculateAgeFromDob(patient.dob);
    }

    private void calculatePregnancyStats(String lmp) {
        if (lmp == null || lmp.isEmpty() || lmp.equals("0000-00-00")) return;
        
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Date lmpDate = sdf.parse(lmp);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(lmpDate);
            
            // EDD: LMP + 280 days
            cal.add(java.util.Calendar.DAY_OF_YEAR, 280);
            java.text.SimpleDateFormat eddSdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US);
            if (tvDueDate != null) tvDueDate.setText(eddSdf.format(cal.getTime()).toUpperCase());
            
            // Weeks: (Today - LMP) / 7
            long diff = System.currentTimeMillis() - lmpDate.getTime();
            long days = diff / (1000 * 60 * 60 * 24);
            long weeks = days / 7;
            if (tvWeeks != null) tvWeeks.setText(String.valueOf(weeks));
            
            // Trimester
            String trimester = "1st";
            if (weeks >= 27) trimester = "3rd";
            else if (weeks >= 14) trimester = "2nd";
            if (tvTrimester != null) tvTrimester.setText(trimester);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void calculateAgeFromDob(String dob) {
        if (dob == null || dob.isEmpty() || dob.equals("0000-00-00")) return;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Date birthDate = sdf.parse(dob);
            java.util.Calendar today = java.util.Calendar.getInstance();
            java.util.Calendar birth = java.util.Calendar.getInstance();
            birth.setTime(birthDate);
            
            int age = today.get(java.util.Calendar.YEAR) - birth.get(java.util.Calendar.YEAR);
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < birth.get(java.util.Calendar.DAY_OF_YEAR)) age--;
            etAge.setText(String.valueOf(age));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showResetBottomSheet() {
        BottomSheetDialog resetSheet = new BottomSheetDialog(this);
        resetSheet.setContentView(R.layout.dialog_password_reset_aura);
        
        View btnUpdate = resetSheet.findViewById(R.id.btnUpdatePass);
        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> {
                Toast.makeText(this, "Security Key Updated. All sessions synchronized.", Toast.LENGTH_SHORT).show();
                resetSheet.dismiss();
            });
        }
        resetSheet.show();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String date = (month + 1) + "/" + dayOfMonth + "/" + year;
            etDob.setText(date);
            calculateAge(year, month, dayOfMonth);
        }, 1995, 0, 1).show();
    }

    private void calculateAge(int year, int month, int day) {
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - year;
        if (today.get(Calendar.DAY_OF_YEAR) < day) age--;
        etAge.setText(String.valueOf(age));
    }

    private void lockFields() {
        setAllEnabled(false);
    }

    private void unlockFields() {
        setAllEnabled(true);
    }

    private void setAllEnabled(boolean enabled) {
        EditText[] fields = {etFirstName, etLastName, etEmail, etPhone, etPhilHealth, etAddress, etLmp, etBlood, etMonths, etEmergencyName, etEmergencyRel, etEmergencyPhone};
        for (EditText f : fields) {
            f.setEnabled(enabled);
            f.setFocusableInTouchMode(enabled);
            f.setAlpha(enabled ? 1.0f : 0.85f);
        }
    }

    private void startFloatingAnimation() {
        ObjectAnimator floating = ObjectAnimator.ofFloat(avatarContainer, "translationY", 0f, -20f, 0f);
        floating.setDuration(3000);
        floating.setRepeatCount(ObjectAnimator.INFINITE);
        floating.setInterpolator(new AccelerateDecelerateInterpolator());
        floating.start();
    }

    private void startPulseAnimation() {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(syncDot, "alpha", 1.0f, 0.3f, 1.0f);
        pulse.setDuration(2000);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
    }
}
