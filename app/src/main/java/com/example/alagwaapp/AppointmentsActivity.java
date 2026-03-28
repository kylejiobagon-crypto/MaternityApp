package com.example.alagwaapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.BufferedSource;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppointmentsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RecyclerView calendarRecyclerView;
    private CalendarAdapter calendarAdapter;
    private Calendar currentDisplayDate;
    private Calendar selectedDate;
    private TextView tvMonthYear, tvSelectedDateAppointments;
    private ImageView btnPrevMonth, btnNextMonth;
    private View layoutEmpty, layoutLoading;
    private View btnBookFloating;
    private View cardQueueStatus, statusPulseLive;
    private TextView tvQueueNumber, tvEstimatedWait;
    private AppointmentAdapter adapter;
    private ApiService apiService;
    private SharedPreferences prefs;
    private List<AppointmentResponse.Appointment> fullList = new ArrayList<>();
    private TextView tvReminderDate, tvReminderAdvice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        recyclerView = findViewById(R.id.recyclerViewAppointments);
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView);
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDateAppointments = findViewById(R.id.tvSelectedDateAppointments);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);

        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutLoading = findViewById(R.id.layoutLoading);
        btnBookFloating = findViewById(R.id.btnBookFloating);
        
        tvReminderDate = findViewById(R.id.tvReminderDate);
        tvReminderAdvice = findViewById(R.id.tvReminderAdvice);
        
        // Queue Status has been moved to Dashboard

        currentDisplayDate = Calendar.getInstance();
        selectedDate = Calendar.getInstance();

        setupCalendar();

        if (btnPrevMonth != null) {
            btnPrevMonth.setOnClickListener(v -> {
                currentDisplayDate.add(Calendar.MONTH, -1);
                updateCalendarView();
            });
        }
        if (btnNextMonth != null) {
            btnNextMonth.setOnClickListener(v -> {
                currentDisplayDate.add(Calendar.MONTH, 1);
                updateCalendarView();
            });
        }
        
        NavigationHelper.setupBottomNav(this);
        
        // Launch the Premium Booking UI (as per spec)
        findViewById(R.id.btnBookFloating).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, BookingActivity.class);
            startActivity(intent);
        });
        
        // Express Booking Cards setup
        View cardExpress1 = findViewById(R.id.cardExpress1);
        View cardExpress2 = findViewById(R.id.cardExpress2);
        View cardExpress3 = findViewById(R.id.cardExpress3);
        View cardExpress4 = findViewById(R.id.cardExpress4);
        
        if (cardExpress1 != null) cardExpress1.setOnClickListener(v -> showPremiumTimeslotSheet("MAR 27", "Tomorrow", new String[]{"09:00 AM", "09:30 AM", "11:00 AM", "01:30 PM", "03:00 PM"}));
        if (cardExpress2 != null) cardExpress2.setOnClickListener(v -> showPremiumTimeslotSheet("MAR 28", "Wednesday", new String[]{"10:30 AM", "11:00 AM", "02:00 PM"}));
        if (cardExpress3 != null) cardExpress3.setOnClickListener(v -> showPremiumTimeslotSheet("MAR 29", "Thursday", new String[]{"02:00 PM", "03:30 PM", "04:00 PM"}));
        if (cardExpress4 != null) cardExpress4.setOnClickListener(v -> showPremiumTimeslotSheet("MAR 30", "Friday", new String[]{"08:30 AM", "04:15 PM"}));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppointmentAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        
        initNetworking();
        fetchAppointments();
    }

    private void setupCalendar() {
        if (calendarRecyclerView != null) {
            calendarRecyclerView.setLayoutManager(new GridLayoutManager(this, 7));
            calendarAdapter = new CalendarAdapter(new ArrayList<>(), this::onDateSelected);
            calendarRecyclerView.setAdapter(calendarAdapter);
            updateCalendarView();
        }
    }

    private void updateCalendarView() {
        if (tvMonthYear == null) return;
        List<CalendarDate> days = new ArrayList<>();
        Calendar calendar = (Calendar) currentDisplayDate.clone();
        
        tvMonthYear.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.getTime()).toUpperCase());
        
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1; // 0 for Sunday
        calendar.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);
        
        while (days.size() < 42) {
            // If we've completed 5 rows (35 days) and the next day is from the next month, stop.
            if (days.size() >= 35 && calendar.get(Calendar.MONTH) != currentDisplayDate.get(Calendar.MONTH)) {
                break;
            }

            days.add(new CalendarDate(
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) == currentDisplayDate.get(Calendar.MONTH),
                (Calendar) calendar.clone()
            ));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        // Update dots based on fullList
        for (CalendarDate d : days) {
            d.hasDots = false;
            String dStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d.date.getTime());
            for (AppointmentResponse.Appointment a : fullList) {
                if (dStr.equals(a.date)) {
                    d.hasDots = true;
                    break;
                }
            }
        }
        
        if (calendarAdapter != null) calendarAdapter.updateDates(days, selectedDate);
    }

    private void onDateSelected(Calendar date) {
        selectedDate = (Calendar) date.clone();
        updateCalendarView(); // To refresh selected state
        
        String formattedDate = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(selectedDate.getTime());
        if (tvSelectedDateAppointments != null) tvSelectedDateAppointments.setText("APPOINTMENTS FOR " + formattedDate.toUpperCase());
        
        filterAppointmentsBySelectedDate();
    }

    private void filterAppointmentsBySelectedDate() {
        String targetDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.getTime());
        String currentUser = prefs.getString("fullname", "").trim();
        List<AppointmentResponse.Appointment> filtered = new ArrayList<>();
        
        for (AppointmentResponse.Appointment a : fullList) {
            if (targetDateStr.equals(a.date)) {
                // Filter specifically for the logged-in user if patientName is provided
                if (currentUser.isEmpty() || currentUser.equalsIgnoreCase(a.patientName)) {
                    filtered.add(a);
                }
            }
        }
        adapter.updateList(filtered);
        
        if (filtered.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void initNetworking() {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                String cookies = CookieManager.getInstance().getCookie("http://alagawa.ct.ws/");
                Request.Builder builder = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "application/json");
                if (cookies != null) builder.header("Cookie", cookies);
                
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
                
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

    private void fetchAppointments() {
        layoutLoading.setVisibility(View.VISIBLE);
        
        apiService.getBookingsRaw("list", "true").enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                layoutLoading.setVisibility(View.GONE);
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String raw = response.body().string();
                        if (raw.trim().startsWith("[")) {
                            AppointmentResponse.Appointment[] arr = new com.google.gson.Gson().fromJson(raw, AppointmentResponse.Appointment[].class);
                            fullList.clear();
                            for (AppointmentResponse.Appointment a : arr) fullList.add(a);
                            
                            // UPDATE REMINDER LOGIC (Match Maternity Old Flow)
                            updateRemindersFromHistory();

                            if (fullList.isEmpty()) {
                                layoutEmpty.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                            }
                            updateCalendarView();
                            filterAppointmentsBySelectedDate();
                        } else {
                            updateCalendarView();
                            filterAppointmentsBySelectedDate();
                        }
                    } else {
                        updateCalendarView();
                        filterAppointmentsBySelectedDate();
                    }
                } catch (Exception e) {
                    updateCalendarView();
                    filterAppointmentsBySelectedDate();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                layoutLoading.setVisibility(View.GONE);
                updateCalendarView();
                filterAppointmentsBySelectedDate();
            }
        });
    }

    private void updateRemindersFromHistory() {
        // Find most recent appointment with a next visit date
        AppointmentResponse.Appointment latestWithReminder = null;
        for (AppointmentResponse.Appointment a : fullList) {
            if (a.nextVisitDate != null && !a.nextVisitDate.isEmpty() && !a.nextVisitDate.equals("0000-00-00")) {
                latestWithReminder = a;
                break; // fullList is ordered DESC (latest first) usually via API
            }
        }

        if (latestWithReminder != null && tvReminderDate != null) {
            try {
                SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                SimpleDateFormat outFmt = new SimpleDateFormat("MMMM d", Locale.getDefault());
                String dateDisp = outFmt.format(inFmt.parse(latestWithReminder.nextVisitDate));
                tvReminderDate.setText("Recommended Next: " + dateDisp);
                
                if (tvReminderAdvice != null && latestWithReminder.checkupNotes != null && !latestWithReminder.checkupNotes.isEmpty()) {
                    tvReminderAdvice.setText(latestWithReminder.checkupNotes);
                } else if (tvReminderAdvice != null) {
                    tvReminderAdvice.setText("Based on your last check-up, please return on the date above.");
                }
            } catch (Exception e) {
                 tvReminderDate.setText("Recommended Next: " + latestWithReminder.nextVisitDate);
            }
        }
    }



    private void showBookAppointmentSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.layout_booking_sheet, null);
        dialog.setContentView(sheet);
        dialog.show();
    }

    private View selectedTimeSlotView = null; // Track selected slot

    private void showPremiumTimeslotSheet(String title, String subtitle, String[] slots) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.layout_premium_timeslot_sheet, null);
        dialog.setContentView(sheet);

        TextView tvSheetDate = sheet.findViewById(R.id.tvSheetDate);
        View btnSheetClose = sheet.findViewById(R.id.btnSheetClose);
        android.widget.GridLayout gridTimeSlots = sheet.findViewById(R.id.gridTimeSlots);
        View btnConfirmSlot = sheet.findViewById(R.id.btnConfirmSlot);

        tvSheetDate.setText(title + " • " + subtitle);

        btnSheetClose.setOnClickListener(v -> dialog.dismiss());
        btnConfirmSlot.setOnClickListener(v -> {
            if (selectedTimeSlotView != null) {
                Toast.makeText(this, "Proceeding to book " + title + " at selected time", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please select an available time slot", Toast.LENGTH_SHORT).show();
            }
        });

        // Populate slots with 3D animation
        selectedTimeSlotView = null;
        for (int i = 0; i < slots.length; i++) {
            View slotView = getLayoutInflater().inflate(R.layout.item_premium_timeslot, gridTimeSlots, false);
            TextView tvTime = slotView.findViewById(R.id.tvTime);
            tvTime.setText(slots[i]);
            
            // Staggered pop-in animation
            slotView.setScaleX(0f);
            slotView.setScaleY(0f);
            slotView.setAlpha(0f);
            slotView.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(400)
                .setStartDelay(i * 50L) // Stagger effect
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();

            slotView.setOnClickListener(v -> {
                // Tactile push animation
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                }).start();

                // Deselect previous
                if (selectedTimeSlotView != null && selectedTimeSlotView != v) {
                    selectedTimeSlotView.setBackgroundResource(R.drawable.bg_timeslot_3d_unselected);
                    ((TextView) selectedTimeSlotView.findViewById(R.id.tvTime)).setTextColor(0xFF9CA3AF); // Light gray text
                    selectedTimeSlotView.setElevation(6f); // Restore base 3D shadow
                }

                // Select current
                selectedTimeSlotView = v;
                v.setBackgroundResource(R.drawable.bg_timeslot_3d_selected); // Ultra premium cyan neon 3D
                ((TextView) v.findViewById(R.id.tvTime)).setTextColor(0xFF001B3B); // Deep contrast dark blue
                v.setElevation(14f); // Extreme neon glow effect
            });

            gridTimeSlots.addView(slotView);
        }

        // Apply visual bounce to root container when sheet opens
        sheet.animate().translationY(50f).setDuration(0).withEndAction(() -> {
            sheet.animate().translationY(0f).setDuration(500)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f))
                .start();
        }).start();

        dialog.show();
    }

    private static class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {
        private List<AppointmentResponse.Appointment> list;
        public AppointmentAdapter(List<AppointmentResponse.Appointment> list) { this.list = list; }
        public void updateList(List<AppointmentResponse.Appointment> newList) { this.list = newList; notifyDataSetChanged(); }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_3d, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppointmentResponse.Appointment appointment = list.get(position);
            
            String timeStr = (appointment.time != null) ? appointment.time : "09:00 AM";
            holder.tvDateTime.setText(timeStr + " - " + timeStr); 

            // PIXEL-PERFECT STATE MAPPING (MATCHING WEB)
            if ("Confirmed".equalsIgnoreCase(appointment.status)) {
                holder.tvPatient.setText(appointment.patientName);
                holder.tvService.setText(appointment.serviceType);
                holder.tvDoctor.setText("Assigned: Dr. Cruz");
                holder.tvStatus.setText("CONFIRMED");
                holder.tvStatus.setTextColor(0xFF75FF68);
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_confirmed);
                holder.statusPulse.setBackgroundColor(0xFF75FF68);
                holder.layoutActionsConfirmed.setVisibility(View.VISIBLE);
                holder.layoutActionsPending.setVisibility(View.GONE);
                holder.ivTimeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF00FBFB));
                holder.tvDateTime.setTextColor(0xFF00FBFB);
                holder.nodeCore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00FBFB));
            } else {
                holder.tvPatient.setText(appointment.patientName);
                holder.tvService.setText(appointment.serviceType);
                holder.tvDoctor.setText("Assigned: Dr. Reyes");
                holder.tvStatus.setText("PENDING");
                holder.tvStatus.setTextColor(0xFF7701D0);
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_pending);
                holder.statusPulse.setBackgroundColor(0xFF7701D0);
                holder.layoutActionsConfirmed.setVisibility(View.GONE);
                holder.layoutActionsPending.setVisibility(View.VISIBLE);
                holder.ivTimeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0x66FFFFFF));
                holder.tvDateTime.setTextColor(0x66FFFFFF);
                holder.nodeCore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33B9CAC9));
            }
            
            holder.btnCancel.setOnClickListener(v -> Toast.makeText(v.getContext(), "Cancel requested", Toast.LENGTH_SHORT).show());
            holder.btnReschedule.setOnClickListener(v -> Toast.makeText(v.getContext(), "Reschedule requested", Toast.LENGTH_SHORT).show());
        }

        @Override public int getItemCount() { return list.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvService, tvDoctor, tvDateTime, tvStatus, tvPatient;
            View nodeCore, containerStatus, statusPulse, layoutActionsConfirmed, layoutActionsPending;
            View btnCancel, btnReschedule;
            ImageView ivTimeIcon;
            ViewHolder(View v) { super(v); 
                tvService = v.findViewById(R.id.tvServiceType);
                tvDoctor  = v.findViewById(R.id.tvDoctorName);
                tvPatient = v.findViewById(R.id.tvPatientName);
                tvDateTime = v.findViewById(R.id.tvDateTime);
                tvStatus = v.findViewById(R.id.tvStatus);
                nodeCore = v.findViewById(R.id.nodeCore);
                containerStatus = v.findViewById(R.id.containerStatus);
                statusPulse = v.findViewById(R.id.statusPulse);
                layoutActionsConfirmed = v.findViewById(R.id.layoutActionsConfirmed);
                layoutActionsPending = v.findViewById(R.id.layoutActionsPending);
                ivTimeIcon = v.findViewById(R.id.ivTimeIcon);
                btnCancel = v.findViewById(R.id.btnCancel);
                btnReschedule = v.findViewById(R.id.btnReschedule);
            }
        }
    }
}
