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

        selectService(0);
        selectTimeSlot(2); 
        
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
        tvDate.setText(new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(c.getTime()));

        fetchAvailableSlots(selectedDate);

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

        apiService.getAvailableSlots("available_slots", "true", date).enqueue(new Callback<ResponseBody>() {
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
            } else {
                view.setEnabled(false);
                view.setAlpha(0.4f);
                if (tvSub != null) {
                    tvSub.setText(reason.toUpperCase());
                    tvSub.setTextColor(0xFFFF4D4D); // Red for booked/passed
                }
                // Deselect if it was selected but now unavailable
                if (selectedTimeSlot.equals(timeSlots[i])) {
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
        apiService.createAppointment("create", "true", patientId, selectedDate, finalFormattedTime, selectedService, notes, phNumber)
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

                        Intent intent = new Intent(BookingActivity.this, PaymentCheckoutActivity.class);
                        intent.putExtra("service_name", selectedService != null ? selectedService : "General Check-up");
                        intent.putExtra("amount", "₱300.00");
                        intent.putExtra("payment_id", paymentId);
                        intent.putExtra("booking_id", bookingId);
                        intent.putExtra("booking_date", selectedDate);
                        intent.putExtra("booking_time", finalFormattedTime);
                        startActivity(intent);
                        finish();

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

