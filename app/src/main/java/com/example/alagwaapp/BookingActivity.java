package com.example.alagwaapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BookingActivity extends AppCompatActivity {

    private TextView tvDate;
    private EditText etPhilhealth, etMedicalNotes;
    private String selectedDate = "";
    private String selectedService = "Normal Delivery";
    private String selectedTimeSlot = "";
    private int rescheduleBookingId = -1; // -1 means new booking
    
    private View btnSelectDate;
    private View[] serviceViews;
    private View[] slotViews;
    private String[] serviceNames = {"Normal Delivery", "Prenatal Check-up", "Ultrasound Scan", "Laboratory Services", "Vaccination"};
    private String[] timeSlots = {"09:00 AM", "10:00 AM", "11:00 AM", "12:30 PM", "01:30 PM", "02:30 PM", "03:30 PM", "04:30 PM"};
    
    private ApiService apiService;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        initNetworking();

        tvDate = findViewById(R.id.tvDate);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        etPhilhealth = findViewById(R.id.etPhilhealth);
        etMedicalNotes = findViewById(R.id.etMedicalNotes);

        serviceViews = new View[]{
            findViewById(R.id.serviceNormalDelivery),
            findViewById(R.id.servicePrenatal),
            findViewById(R.id.serviceUltrasound),
            findViewById(R.id.serviceLab),
            findViewById(R.id.serviceVaccination)
        };

        slotViews = new View[]{
            findViewById(R.id.slot9Am), findViewById(R.id.slot10Am),
            findViewById(R.id.slot11Am), findViewById(R.id.slot12Pm),
            findViewById(R.id.slot1Pm), findViewById(R.id.slot2Pm),
            findViewById(R.id.slot3Pm), findViewById(R.id.slot4Pm)
        };

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        btnSelectDate.setOnClickListener(v -> showDatePicker());

        for (int i = 0; i < serviceViews.length; i++) {
            final int index = i;
            if (serviceViews[i] != null) {
                serviceViews[i].setOnClickListener(v -> selectService(index));
            }
        }

        for (int i = 0; i < slotViews.length; i++) {
            final int index = i;
            if (slotViews[i] != null) {
                slotViews[i].setOnClickListener(v -> selectTimeSlot(index));
            }
        }

        // ══════════════════════════════════════════
        //  EXTRACT INTENT EXTRAS (Express Booking)
        // ══════════════════════════════════════════
        String intentDate = getIntent().getStringExtra("selected_date");
        String intentTime = getIntent().getStringExtra("selected_time");
        
        rescheduleBookingId = getIntent().getIntExtra("reschedule_booking_id", -1);
        if (rescheduleBookingId != -1) {
            TextView tvTitle = findViewById(R.id.tvBookingTitle);
            TextView tvSubtitle = findViewById(R.id.tvBookingSubtitle);
            TextView tvBtn = findViewById(R.id.tvFinalizeBtn);
            if (tvTitle != null) tvTitle.setText("RESCHEDULE APPOINTMENT");
            if (tvSubtitle != null) tvSubtitle.setText("Select a new date and time for your visit");
            if (tvBtn != null) tvBtn.setText("REQUEST RESCHEDULE");
        }

        if (intentDate != null && !intentDate.isEmpty()) {
            selectedDate = intentDate;
            try {
                SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat outFmt = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
                tvDate.setText(outFmt.format(inFmt.parse(intentDate)));
            } catch (Exception e) {
                tvDate.setText(intentDate);
            }
        } else {
            Calendar c = Calendar.getInstance();
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
            tvDate.setText(new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(c.getTime()));
        }

        if (intentTime != null && !intentTime.isEmpty()) {
            selectedTimeSlot = intentTime;
            // Immediate pre-selection for snappier UI
            for (int i = 0; i < timeSlots.length; i++) {
                if (timeSlots[i].equalsIgnoreCase(intentTime)) {
                    selectTimeSlot(i);
                    break;
                }
            }
        } else {
            selectTimeSlot(2); // Default to 11AM if no intent
        }

        fetchAvailableSlots(selectedDate);
        fetchPatientProfile();

        findViewById(R.id.btnFinalize).setOnClickListener(v -> {
            if (selectedTimeSlot == null || selectedTimeSlot.isEmpty()) {
                Toast.makeText(this, "Please select an available time slot first", Toast.LENGTH_SHORT).show();
                return;
            }
            v.setAlpha(0.7f);
            new Handler().postDelayed(() -> v.setAlpha(1.0f), 200);
            submitAppointment();
        });
    }

    private void fetchPatientProfile() {
        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");
        
        apiService.getPatientsRaw("list", "true", email, username).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String raw = response.body().string();
                        com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(raw, com.google.gson.JsonObject.class);
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            com.google.gson.JsonArray data = json.getAsJsonArray("data");
                            if (data.size() > 0) {
                                com.google.gson.JsonObject p = data.get(0).getAsJsonObject();
                                String ph = p.has("philhealth_number") ? p.get("philhealth_number").getAsString() : "";
                                if (ph != null && !ph.isEmpty() && !ph.equals("N/A")) {
                                    etPhilhealth.setText(ph);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Silently fail, manual input still possible
            }
        });
    }

    private void initNetworking() {
        // InfinityFreeClient auto-solves the AES anti-bot challenge from InfinityFree hosting
        apiService = InfinityFreeClient.buildRetrofit(prefs).create(ApiService.class);
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, dayOfMonth);
            
            // Sunday Check (0 = Sunday in PHP/Calendar, but Calendar.DAY_OF_WEEK: 1=Sun, 2=Mon...)
            if (chosen.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                Toast.makeText(this, "The clinic is closed on Sundays. Please select another date.", Toast.LENGTH_LONG).show();
                return;
            }

            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.getTime());
            tvDate.setText(new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(chosen.getTime()));
            
            fetchAvailableSlots(selectedDate);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        
        // Block past dates
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void fetchAvailableSlots(String date) {
        // Show loading state for slots
        for (View v : slotViews) {
            if (v != null) {
                v.setAlpha(0.3f);
                v.setEnabled(false);
            }
        }

        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");
        String role = prefs.getString("role", "patient");
        int tenantId = prefs.getInt("tenant_id", 1);
        
        apiService.getAvailableSlots("available_slots", "true", email, username, role, tenantId, date).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String raw = response.body().string();
                        com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(raw, com.google.gson.JsonObject.class);
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            com.google.gson.JsonArray data = json.getAsJsonArray("data");
                            updateSlotsUI(data);
                        } else {
                            resetSlotsUI();
                        }
                    } else {
                        resetSlotsUI();
                    }
                } catch (Exception e) {
                    resetSlotsUI();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                resetSlotsUI();
            }
        });
    }

    private void updateSlotsUI(com.google.gson.JsonArray data) {
        for (int i = 0; i < data.size() && i < slotViews.length; i++) {
            com.google.gson.JsonObject slot = data.get(i).getAsJsonObject();
            boolean available = slot.get("available").getAsBoolean();
            String reason = slot.get("reason").getAsString();
            
            View view = slotViews[i];
            if (view == null) continue;

            TextView tvSub = view.findViewById(getResources().getIdentifier("tvSlot" + getHourName(i) + "Sub", "id", getPackageName()));
            
            if (available) {
                view.setEnabled(true);
                view.setAlpha(1.0f);
                if (tvSub != null) tvSub.setText(reason.toUpperCase());
                
                // If this slot matches the "Express" intent time, select it automatically
                if (selectedTimeSlot != null && !selectedTimeSlot.isEmpty() && selectedTimeSlot.equalsIgnoreCase(timeSlots[i])) {
                    selectTimeSlot(i);
                }
            } else {
                view.setEnabled(false);
                view.setAlpha(0.4f);
                if (tvSub != null) {
                    tvSub.setText(reason.toUpperCase());
                    tvSub.setTextColor(0xFFFF4D4D); // Red for booked/passed
                }
                // Deselect if it was selected but now unavailable
                if (selectedTimeSlot != null && selectedTimeSlot.equals(timeSlots[i])) {
                    selectedTimeSlot = "";
                    view.setBackgroundResource(R.drawable.bg_3d_real_glass);
                }
            }
        }
    }

    private void resetSlotsUI() {
        for (View v : slotViews) {
            if (v != null) {
                v.setAlpha(1.0f);
                v.setEnabled(true);
            }
        }
    }

    private void selectService(int index) {
        selectedService = serviceNames[index];
        for (int i = 0; i < serviceViews.length; i++) {
            View view = serviceViews[i];
            if (view == null) continue;
            
            View btnIndicator = view.findViewById(i == 0 ? R.id.bChk : getResources().getIdentifier("b" + i, "id", getPackageName()));

            if (i == index) {
                view.setBackgroundResource(R.drawable.bg_booking_card_ultra_premium_selected);
                view.setElevation(12f);
                if (btnIndicator != null) {
                    btnIndicator.setBackgroundResource(R.drawable.bg_glass_circle);
                    btnIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x4DFFFFFF));
                    btnIndicator.setAlpha(1.0f);
                }
            } else {
                view.setBackgroundResource(R.drawable.bg_glass_card_billing);
                view.setElevation(4f);
                if (btnIndicator != null) {
                    btnIndicator.setBackgroundResource(R.drawable.bg_glass_circle);
                    btnIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1AFFFFFF));
                    btnIndicator.setAlpha(1.0f);
                }
            }
        }
    }

    private void selectTimeSlot(int index) {
        selectedTimeSlot = timeSlots[index];
        for (int i = 0; i < slotViews.length; i++) {
            View view = slotViews[i];
            if (view == null) continue;

            TextView tvMain = view.findViewById(getResources().getIdentifier("tvSlot" + getHourName(i), "id", getPackageName()));
            TextView tvSub = view.findViewById(getResources().getIdentifier("tvSlot" + getHourName(i) + "Sub", "id", getPackageName()));
            
            if (tvMain == null || tvSub == null) continue;

            if (i == index) {
                view.setBackgroundResource(R.drawable.bg_booking_card_ultra_premium_selected);
                view.setElevation(12f);
                tvMain.setTextColor(0xFFFFFFFF);
                tvSub.setTextColor(0xCCFFFFFF);
                tvSub.setAlpha(1.0f);
            } else {
                view.setBackgroundResource(R.drawable.bg_3d_real_glass);
                view.setElevation(2f);
                tvMain.setTextColor(0xFFFFFFFF);
                tvSub.setTextColor(0xAAFFFFFF);
                tvSub.setAlpha(0.6f);
            }
        }
    }
    
    private String getHourName(int index) {
        switch (index) {
            case 0: return "9Am";
            case 1: return "10Am";
            case 2: return "11Am";
            case 3: return "12Pm";
            case 4: return "1Pm";
            case 5: return "2Pm";
            case 6: return "3Pm";
            case 7: return "4Pm";
            default: return "";
        }
    }

    private void submitAppointment() {
        String notes = etMedicalNotes.getText().toString().trim();

        // Send patient_id = 0: backend will auto-resolve the patient from session email (set by interceptor).
        // This avoids sending the user_id (which is NOT the patient_id in the DB).
        int patientId = 0;

        // PhilHealth is now passed as a dedicated field
        String phNumber = etPhilhealth.getText().toString().trim();

        // Convert "09:00 AM" to HH:mm (24h) for backend regex
        String formattedTime = selectedTimeSlot;
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            SimpleDateFormat outFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            formattedTime = outFmt.format(inFmt.parse(selectedTimeSlot));
        } catch (Exception e) {
            if (selectedTimeSlot.contains(" AM")) formattedTime = selectedTimeSlot.replace(" AM", "");
            if (selectedTimeSlot.contains(" PM")) {
                int hr = Integer.parseInt(selectedTimeSlot.split(":")[0]);
                if (hr < 12) hr += 12;
                formattedTime = hr + ":" + selectedTimeSlot.split(":")[1].replace(" PM", "");
            }
        }

        // Disable button to prevent double-submit
        View btnFinalize = findViewById(R.id.btnFinalize);
        if (btnFinalize != null) {
            btnFinalize.setEnabled(false);
            btnFinalize.setAlpha(0.5f);
        }

        final String finalFormattedTime = formattedTime;
        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");

        if (rescheduleBookingId != -1) {
            // RESCHEDULE MODE
            apiService.rescheduleBooking("reschedule", "true", email, username, rescheduleBookingId, selectedDate, finalFormattedTime)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (btnFinalize != null) {
                            btnFinalize.setEnabled(true);
                            btnFinalize.setAlpha(1.0f);
                        }
                        try {
                            String raw = response.body() != null ? response.body().string() : "{}";
                            org.json.JSONObject obj = new org.json.JSONObject(raw);
                            if (obj.optBoolean("success")) {
                                Toast.makeText(BookingActivity.this, "Reschedule request sent! Waiting for approval.", Toast.LENGTH_LONG).show();
                                finish();
                            } else {
                                Toast.makeText(BookingActivity.this, obj.optString("message", "Error rescheduling"), Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        if (btnFinalize != null) {
                            btnFinalize.setEnabled(true);
                            btnFinalize.setAlpha(1.0f);
                        }
                        Toast.makeText(BookingActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
        } else {
            // NEW BOOKING MODE
            apiService.createAppointment("create", "true", email, username, patientId, selectedDate, finalFormattedTime, selectedService, notes, phNumber)
            .enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (btnFinalize != null) {
                        btnFinalize.setEnabled(true);
                        btnFinalize.setAlpha(1.0f);
                    }
                    String rawBody = "(empty)";
                    try {
                        rawBody = response.body() != null ? response.body().string() : "";
                        android.util.Log.d("BOOKING_DEBUG", "HTTP Status: " + response.code());
                        android.util.Log.d("BOOKING_DEBUG", "Raw Response: " + rawBody);

                        // Strip any PHP warnings/notices before the JSON
                        int jsonStart = rawBody.indexOf('{');
                        if (jsonStart > 0) rawBody = rawBody.substring(jsonStart);

                        org.json.JSONObject obj = new org.json.JSONObject(rawBody);
                        boolean success = obj.optBoolean("success", false);

                        if (!success) {
                            String msg = obj.optString("message", "Booking failed. Please try again.");
                            boolean profileIncomplete = obj.optBoolean("profile_incomplete", false);
                            if (profileIncomplete) {
                                new android.app.AlertDialog.Builder(BookingActivity.this)
                                    .setTitle("Complete Your Profile First")
                                    .setMessage(msg + "\n\nPlease update your profile before booking.")
                                    .setPositiveButton("Go to Profile", (d, w) -> {
                                        Intent intent = new Intent(BookingActivity.this, ProfileActivity.class);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            } else {
                                Toast.makeText(BookingActivity.this, msg, Toast.LENGTH_LONG).show();
                            }
                            return;
                        }

                        // SUCCESS — get payment_id returned by backend
                        int paymentId = obj.optInt("payment_id", 0);
                        int bookingId = obj.optInt("booking_id", 0);

                        // SHOW CUSTOM PENDING DIALOG
                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_booking_pending, null);
                        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(BookingActivity.this)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create();
                        
                        // Ensure the dialog window itself is transparent to show the glass card correctly
                        if (dialog.getWindow() != null) {
                            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                        }

                        ((TextView) dialogView.findViewById(R.id.tvDialogMessage)).setText(
                            "Your request has been received! \n\nTo secure your " + selectedService + " for " + selectedDate + ", a downpayment is required.");

                        dialogView.findViewById(R.id.btnDialogPay).setOnClickListener(vPay -> {
                            dialog.dismiss();
                            Intent nIntent = new Intent(BookingActivity.this, PaymentCheckoutActivity.class);
                            nIntent.putExtra("service_name", selectedService);
                            nIntent.putExtra("amount", "₱300.00");
                            nIntent.putExtra("payment_id", paymentId);
                            nIntent.putExtra("booking_id", bookingId);
                            nIntent.putExtra("booking_date", selectedDate);
                            nIntent.putExtra("booking_time", finalFormattedTime);
                            startActivity(nIntent);
                            finish();
                        });

                        dialogView.findViewById(R.id.btnDialogLater).setOnClickListener(vLater -> {
                            dialog.dismiss();
                            Intent nIntent = new Intent(BookingActivity.this, AppointmentsActivity.class);
                            nIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(nIntent);
                            finish();
                        });

                        dialog.show();

                    } catch (Exception e) {
                        android.util.Log.e("BOOKING_DEBUG", "Parse error: " + e.getMessage() + " | Raw: " + rawBody);
                        // Show a truncated version of raw response so we can debug
                        String preview = rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody;
                        new android.app.AlertDialog.Builder(BookingActivity.this)
                            .setTitle("Server Response (Debug)")
                            .setMessage(preview)
                            .setPositiveButton("OK", null)
                            .show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    if (btnFinalize != null) {
                        btnFinalize.setEnabled(true);
                        btnFinalize.setAlpha(1.0f);
                    }
                    Toast.makeText(BookingActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}

