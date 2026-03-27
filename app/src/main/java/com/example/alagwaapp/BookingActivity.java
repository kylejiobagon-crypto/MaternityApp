package com.example.alagwaapp;

import android.app.DatePickerDialog;
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

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
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
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
        tvDate.setText(new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(c.getTime()));

        findViewById(R.id.btnFinalize).setOnClickListener(v -> {
            v.setAlpha(0.7f);
            new Handler().postDelayed(() -> v.setAlpha(1.0f), 200);
            submitAppointment();
        });
    }

    private void initNetworking() {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                String cookies = CookieManager.getInstance().getCookie("http://alagawa.ct.ws/");
                Request.Builder builder = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "application/json");
                if (cookies != null) builder.header("Cookie", cookies);
                
                // Add critical Mobile Bypass parameters
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

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://alagawa.ct.ws/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                .build();
        apiService = retrofit.create(ApiService.class);
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, dayOfMonth);
            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(chosen.getTime());
            tvDate.setText(new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(chosen.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void selectService(int index) {
        selectedService = serviceNames[index];
        for (int i = 0; i < serviceViews.length; i++) {
            View view = serviceViews[i];
            if (view == null) continue;
            
            View btnIndicator = view.findViewById(i == 0 ? R.id.bChk : getResources().getIdentifier("b" + i, "id", getPackageName()));

            if (i == index) {
                view.setBackgroundResource(R.drawable.bg_booking_card_3d_selected);
                view.setElevation(8f);
                if (btnIndicator != null) {
                    btnIndicator.setBackgroundResource(R.drawable.bg_button_premium_blue);
                    btnIndicator.setAlpha(1.0f);
                }
            } else {
                view.setBackgroundResource(R.drawable.bg_booking_card_3d_white);
                view.setElevation(2f);
                if (btnIndicator != null) {
                    btnIndicator.setBackgroundResource(R.drawable.bg_glass_circle);
                    btnIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF3F4F6));
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
                view.setBackgroundResource(R.drawable.bg_booking_card_3d_selected);
                view.setElevation(10f);
                tvMain.setTextColor(0xFF007AFF);
                tvSub.setTextColor(0xFF007AFF);
                tvSub.setAlpha(0.6f);
            } else {
                view.setBackgroundResource(R.drawable.bg_booking_card_3d_white);
                view.setElevation(2f);
                tvMain.setTextColor(0xFF0B0E11);
                tvSub.setTextColor(0xFF6B7280);
                tvSub.setAlpha(1.0f);
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
        
        // Use 0 as default if we don't have it; backend will resolve by email in session (interceptor)
        int patientId = prefs.getInt("userId", 0);
        String mobile = "true"; // mobile param is passed via query string now
        
        // PhilHealth is now passed as a dedicated field
        String phNumber = etPhilhealth.getText().toString().trim();

        // Convert common "09:00 AM" to HH:mm (24h) for backend regex: /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/
        String formattedTime = selectedTimeSlot;
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            SimpleDateFormat outFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            formattedTime = outFmt.format(inFmt.parse(selectedTimeSlot));
        } catch (Exception e) {
            // fallback if it was already formatted or something failed
            if (selectedTimeSlot.contains(" AM")) formattedTime = selectedTimeSlot.replace(" AM", "");
            if (selectedTimeSlot.contains(" PM")) {
                 int hr = Integer.parseInt(selectedTimeSlot.split(":")[0]);
                 if (hr < 12) hr += 12;
                 formattedTime = hr + ":" + selectedTimeSlot.split(":")[1].replace(" PM", "");
            }
        }

        apiService.createAppointment("create", "true", patientId, selectedDate, formattedTime, selectedService, notes, phNumber)
            .enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        String body = response.body() != null ? response.body().string() : "";
                        if (body.contains("\"success\":true")) {
                            Toast.makeText(BookingActivity.this, "✅ Appointment Scheduled Successfully!", Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            // Extract error message
                            String msg = "Failed to schedule.";
                            if (body.contains("\"message\":\"")) {
                                msg = body.split("\"message\":\"")[1].split("\"")[0];
                            }
                            Toast.makeText(BookingActivity.this, "❌ " + msg, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(BookingActivity.this, "✅ Done!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(BookingActivity.this, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });
    }
}
