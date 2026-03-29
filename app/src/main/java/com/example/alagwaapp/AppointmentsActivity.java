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
import okhttp3.ResponseBody;
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
    
    // NEW: Premium Filters
    private TextView filterAll, filterPending, filterConfirmed, filterRejected, filterCompleted;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        initNetworking();

        recyclerView = findViewById(R.id.recyclerViewAppointments);
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView);
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDateAppointments = findViewById(R.id.tvSelectedDateAppointments);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);

        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutLoading = findViewById(R.id.layoutLoading);
        btnBookFloating = findViewById(R.id.btnBookFloating);
        
        tvReminderAdvice = findViewById(R.id.tvReminderAdvice);
        
        // NEW: Premium Filters
        filterAll = findViewById(R.id.filterAll);
        filterPending = findViewById(R.id.filterPending);
        filterConfirmed = findViewById(R.id.filterConfirmed);
        filterRejected = findViewById(R.id.filterRejected);
        filterCompleted = findViewById(R.id.filterCompleted);
        filterAll.setSelected(true); // Default
        
        setupFilterListeners();
        
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
        
        setupExpressBookingCards();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppointmentAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        fetchAppointments();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to this screen so status updates (e.g. confirmed) show instantly
        if (apiService != null) {
            fetchAppointments();
            setupExpressBookingCards();
        }
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
            d.hasConfirmed = false;
            d.hasPending = false;
            String dStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d.date.getTime());
            for (AppointmentResponse.Appointment a : fullList) {
                if (dStr.equals(a.date)) {
                    d.hasDots = true;
                    String status = (a.status != null) ? a.status.toLowerCase().trim() : "";
                    if (status.equals("confirmed")) {
                        d.hasConfirmed = true;
                    } else if (status.equals("pending") || status.isEmpty()) {
                        d.hasPending = true;
                    }
                }
            }
        }
        
        if (calendarAdapter != null) calendarAdapter.updateDates(days, selectedDate);
    }

    private void onDateSelected(Calendar date) {
        selectedDate = (Calendar) date.clone();
        updateCalendarView(); // To refresh selected state
        
        updateFilterHeaderText();
        filterAppointmentsBySelectedDate();
    }

    private void updateFilterHeaderText() {
        if (tvSelectedDateAppointments == null) return;
        
        if (currentFilter.equals("ALL")) {
            tvSelectedDateAppointments.setText("ALL APPOINTMENTS");
        } else if (currentFilter.equals("PENDING")) {
            tvSelectedDateAppointments.setText("ALL PENDING REQUESTS");
        } else if (currentFilter.equals("CONFIRMED")) {
            tvSelectedDateAppointments.setText("ALL CONFIRMED SCHEDULES");
        } else if (currentFilter.equals("REJECTED")) {
            tvSelectedDateAppointments.setText("ALL REJECTED / ACTION REQUIRED");
        } else if (currentFilter.equals("COMPLETED")) {
            tvSelectedDateAppointments.setText("ALL COMPLETED VISITS");
        }
    }

    private void setupFilterListeners() {
        View.OnClickListener listener = v -> {
            // Reset all
            filterAll.setSelected(false);
            filterPending.setSelected(false);
            filterConfirmed.setSelected(false);
            filterRejected.setSelected(false);
            filterCompleted.setSelected(false);
            
            // Set selected
            v.setSelected(true);
            
            if (v.getId() == R.id.filterAll) currentFilter = "ALL";
            else if (v.getId() == R.id.filterPending) currentFilter = "PENDING";
            else if (v.getId() == R.id.filterConfirmed) currentFilter = "CONFIRMED";
            else if (v.getId() == R.id.filterRejected) currentFilter = "REJECTED";
            else if (v.getId() == R.id.filterCompleted) currentFilter = "COMPLETED";
            
            updateFilterHeaderText();
            filterAppointmentsBySelectedDate();
        };
        
        filterAll.setOnClickListener(listener);
        filterPending.setOnClickListener(listener);
        filterConfirmed.setOnClickListener(listener);
        filterRejected.setOnClickListener(listener);
        filterCompleted.setOnClickListener(listener);
    }

    private void filterAppointmentsBySelectedDate() {
        String targetDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.getTime());
        List<AppointmentResponse.Appointment> filtered = new ArrayList<>();
        
        for (AppointmentResponse.Appointment a : fullList) {
            String status = (a.status != null) ? a.status.toLowerCase().trim() : "";
            String dpStatus = (a.downpaymentStatus != null) ? a.downpaymentStatus.toLowerCase().trim() : "";

            // 1. PIXEL-PERFECT REJECTION CHECK (Sync with Adapter)
            boolean isApptRejected = status.equals("declined") || status.equals("rejected") || status.equals("cancelled");
            boolean isPaymentRejected = dpStatus.equals("declined") || dpStatus.equals("rejected") || dpStatus.equals("cancelled");
            
            boolean isRejected = isApptRejected || isPaymentRejected;
            
            // SPECIAL: Filter out pure system-auto-cancelled duplicates
            if (status.equals("cancelled")) {
                boolean hadSubmittedPayment = dpStatus.equals("for_verification")
                        || dpStatus.equals("paid")
                        || dpStatus.equals("applied")
                        || dpStatus.equals("pending");
                if (!hadSubmittedPayment) continue;
            }
            
            // Handle blank status as "pending"
            boolean isPending = (status.equals("pending") || status.isEmpty()) && !isRejected;

            // 2. ULTRA SMART FILTERING (Global Search)
            if (currentFilter.equals("ALL")) {
                // Global list for the selected day
                if (!a.date.equals(targetDateStr)) continue;
                if (isRejected) a.status = "declined"; 
            } else if (currentFilter.equals("PENDING")) {
                // Pending for the selected day
                if (!isPending) continue;
                if (!a.date.equals(targetDateStr)) continue;
            } else if (currentFilter.equals("CONFIRMED")) {
                // Confirmed for the selected day
                if (!status.equals("confirmed")) continue;
                if (!a.date.equals(targetDateStr)) continue;
            } else if (currentFilter.equals("REJECTED")) {
                // Rejected/Cancelled for the selected day
                if (!isRejected) continue;
                if (!a.date.equals(targetDateStr)) continue;
                a.status = "declined"; 
            } else if (currentFilter.equals("COMPLETED")) {
                // Completed for the selected day
                if (!status.equals("completed")) continue;
                if (!a.date.equals(targetDateStr)) continue;
            }

            filtered.add(a);
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
        // InfinityFreeClient auto-solves the AES anti-bot challenge from InfinityFree hosting
        apiService = InfinityFreeClient.buildRetrofit(prefs).create(ApiService.class);
    }

    private void fetchAppointments() {
        layoutLoading.setVisibility(View.VISIBLE);
        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");
        int tenantId = prefs.getInt("tenant_id", 1);
        String role = prefs.getString("role", "patient"); // Default to patient for mobile

        apiService.getBookingsRaw("list", "true", email, username, role, tenantId).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                layoutLoading.setVisibility(View.GONE);
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String raw = response.body().string();

                        // Strip any PHP warnings/notices before the JSON
                        int objStart = raw.indexOf('{');
                        int arrStart = raw.indexOf('[');
                        if (objStart < 0 && arrStart < 0) {
                            Log.e("AlagwaBookings", "No JSON in bookings response");
                            updateCalendarView(); filterAppointmentsBySelectedDate(); return;
                        }
                        // Pick whichever comes first
                        if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) {
                            raw = raw.substring(arrStart);
                        } else {
                            raw = raw.substring(objStart);
                        }

                        Log.d("AlagwaBookings", "Response (first 300): " + raw.substring(0, Math.min(300, raw.length())));

                        AppointmentResponse.Appointment[] arr = null;
                        if (raw.startsWith("[")) {
                            // Legacy bare-array format
                            arr = new com.google.gson.Gson().fromJson(raw, AppointmentResponse.Appointment[].class);
                        } else {
                            // Standard wrapped format: {"success":true,"data":[...]}
                            AppointmentResponse wrapped = new com.google.gson.Gson().fromJson(raw, AppointmentResponse.class);
                            if (wrapped != null && wrapped.success && wrapped.data != null) {
                                arr = wrapped.data.toArray(new AppointmentResponse.Appointment[0]);
                            } else {
                                Log.w("AlagwaBookings", "success=false or empty data");
                            }
                        }

                        fullList.clear();
                        if (arr != null) {
                            for (AppointmentResponse.Appointment a : arr) fullList.add(a);
                        }
                        Log.d("AlagwaBookings", "Total appointments loaded: " + fullList.size());

                        updateRemindersFromHistory();

                        if (fullList.isEmpty()) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            layoutEmpty.setVisibility(View.GONE);
                        }
                        updateCalendarView();
                        filterAppointmentsBySelectedDate();
                    } else {
                        Log.w("AlagwaBookings", "HTTP error: " + response.code());
                        updateCalendarView(); filterAppointmentsBySelectedDate();
                    }
                } catch (Exception e) {
                    Log.e("AlagwaBookings", "fetchAppointments error: " + e.getMessage());
                    updateCalendarView(); filterAppointmentsBySelectedDate();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Log.e("AlagwaBookings", "Network failure: " + t.getMessage());
                layoutLoading.setVisibility(View.GONE);
                updateCalendarView(); filterAppointmentsBySelectedDate();
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

    private void setupExpressBookingCards() {
        String email = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getString("email", "");
        String username = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getString("username", "");
        int tenantId = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getInt("tenant_id", 1);
        
        // Fetch the first available slot for the next 4 non-Sunday days
        apiService.getExpressSlots("express_slots", "true", email, username, tenantId).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String raw = response.body().string();
                        org.json.JSONObject json = new org.json.JSONObject(raw);
                        if (json.getBoolean("success")) {
                            org.json.JSONArray data = json.getJSONArray("data");
                            for (int i = 0; i < data.length() && i < 4; i++) {
                                org.json.JSONObject slot = data.getJSONObject(i);
                                updateExpressCardUI(i + 1, slot);
                            }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {}
        });
    }

    private void updateExpressCardUI(int index, org.json.JSONObject slotData) {
        try {
            String dateIdStr = "tvExpressDate" + index;
            String dayIdStr = "tvExpressDay" + index;
            String timeIdStr = "tvExpressTime" + index;
            String cardIdStr = "cardExpress" + index;

            int dateId = getResources().getIdentifier(dateIdStr, "id", getPackageName());
            int dayId = getResources().getIdentifier(dayIdStr, "id", getPackageName());
            int timeId = getResources().getIdentifier(timeIdStr, "id", getPackageName());
            int cardId = getResources().getIdentifier(cardIdStr, "id", getPackageName());

            TextView tvDate = findViewById(dateId);
            TextView tvDay = findViewById(dayId);
            TextView tvTime = findViewById(timeId);
            View card = findViewById(cardId);

            String displayDate = slotData.getString("display_date").toUpperCase();
            String dayName = slotData.getString("day_name");
            String firstTime = slotData.getString("time");
            String fullDate = slotData.getString("date");

            if (tvDate != null) tvDate.setText(displayDate);
            if (tvDay != null) tvDay.setText(dayName);
            if (tvTime != null) tvTime.setText(firstTime);

            if (card != null) {
                card.setOnClickListener(v -> showPremiumTimeslotSheet(displayDate, dayName, fullDate));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showPremiumTimeslotSheet(String title, String subtitle, String fullDate) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.layout_premium_timeslot_sheet, null);
        dialog.setContentView(sheet);

        TextView tvSheetDate = sheet.findViewById(R.id.tvSheetDate);
        View btnSheetClose = sheet.findViewById(R.id.btnSheetClose);
        android.widget.GridLayout gridTimeSlots = sheet.findViewById(R.id.gridTimeSlots);
        View btnConfirmSlot = sheet.findViewById(R.id.btnConfirmSlot);

        tvSheetDate.setText(title + " • " + subtitle);
        btnSheetClose.setOnClickListener(v -> dialog.dismiss());
        
        final String[] clickedTime = {null};
        btnConfirmSlot.setOnClickListener(v -> {
            if (clickedTime[0] != null) {
                dialog.dismiss();
                Intent intent = new Intent(this, BookingActivity.class);
                intent.putExtra("selected_date", fullDate);
                intent.putExtra("selected_time", clickedTime[0]);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please select an available time slot", Toast.LENGTH_SHORT).show();
            }
        });

        // ══════════════════════════════════════════
        //  FETCH LIVE SLOTS FROM API
        // ══════════════════════════════════════════
        String email = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getString("email", "");
        String username = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getString("username", "");
        int tenantId = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getInt("tenant_id", 1);
        String role = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE).getString("role", "patient");
        
        apiService.getAvailableSlots("available_slots", "true", email, username, role, tenantId, fullDate).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String raw = response.body().string();
                        org.json.JSONObject json = new org.json.JSONObject(raw);
                        if (json.getBoolean("success")) {
                            org.json.JSONArray slots = json.getJSONArray("data");
                            gridTimeSlots.removeAllViews();
                            
                            for (int i = 0; i < slots.length(); i++) {
                                org.json.JSONObject slot = slots.getJSONObject(i);
                                String displayTime = slot.getString("display").split("-")[0].trim();
                                boolean available = slot.getBoolean("available");
                                
                                View slotView = getLayoutInflater().inflate(R.layout.item_premium_timeslot, gridTimeSlots, false);
                                TextView tvTime = slotView.findViewById(R.id.tvTime);
                                tvTime.setText(displayTime);
                                
                                if (!available) {
                                    slotView.setAlpha(0.3f);
                                    slotView.setEnabled(false);
                                } else {
                                    final String finalTime = displayTime;
                                    slotView.setOnClickListener(v -> {
                                        if (selectedTimeSlotView != null) {
                                            selectedTimeSlotView.setBackgroundResource(R.drawable.bg_timeslot_3d_unselected);
                                        }
                                        selectedTimeSlotView = v;
                                        clickedTime[0] = finalTime;
                                        v.setBackgroundResource(R.drawable.bg_timeslot_3d_selected);
                                    });
                                }
                                gridTimeSlots.addView(slotView);
                            }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {}
        });

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
            
            // IRON WALL PRIVACY CHECK (Frontend Guard)
            // Ensure Dustin Pogi ONLY sees Dustin Pogi.
            SharedPreferences userPrefs = holder.itemView.getContext().getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
            String loggedInEmail = userPrefs.getString("email", "");
            String loggedInFullname = userPrefs.getString("fullname", "");
            String currentPatientName = appointment.getFullName();
            
            boolean isOwner = false;
            if (appointment.email != null && !loggedInEmail.isEmpty() && appointment.email.equalsIgnoreCase(loggedInEmail)) {
                isOwner = true;
            } else if (currentPatientName != null && !loggedInFullname.isEmpty() && currentPatientName.equalsIgnoreCase(loggedInFullname)) {
                isOwner = true;
            }
            
            if (!isOwner) {
                // If not the owner, hide the item completely
                holder.itemView.setVisibility(View.GONE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                return;
            } else {
                holder.itemView.setVisibility(View.VISIBLE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            // Always set correct patient name to replace 'Maria Santos' placeholder
            holder.tvPatient.setText(currentPatientName);
            holder.tvService.setText(appointment.serviceType);

            String timeStr = (appointment.time != null) ? appointment.time : "09:00 AM";
            holder.tvDateTime.setText(timeStr + " - " + timeStr); 

            // PIXEL-PERFECT STATE MAPPING (MATCHING WEB)
            if ("Confirmed".equalsIgnoreCase(appointment.status)) {
                holder.tvDoctor.setText("Assigned: Dr. Cruz");
                holder.tvStatus.setText("CONFIRMED");
                holder.tvStatus.setTextColor(0xFF75FF68);
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_confirmed);
                holder.statusPulse.setBackgroundColor(0xFF75FF68);
                holder.layoutActionsConfirmed.setVisibility(View.VISIBLE);
                holder.layoutActionsPending.setVisibility(View.GONE);
                holder.layoutActionsCompleted.setVisibility(View.GONE);
                holder.layoutActionsRejected.setVisibility(View.GONE);
                holder.ivTimeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF00FBFB));
                holder.tvDateTime.setTextColor(0xFF00FBFB);
                holder.nodeCore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00FBFB));
            } else if ("completed".equalsIgnoreCase(appointment.status)) {
                holder.tvDoctor.setText("Assigned: Dr. Cruz");
                holder.tvStatus.setText("COMPLETED");
                holder.tvStatus.setTextColor(0xFFA855F7); // Purple
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_confirmed);
                holder.containerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1AA855F7));
                holder.statusPulse.setBackgroundColor(0xFFA855F7);
                holder.layoutActionsConfirmed.setVisibility(View.GONE);
                holder.layoutActionsPending.setVisibility(View.GONE);
                holder.layoutActionsCompleted.setVisibility(View.VISIBLE);
                holder.layoutActionsRejected.setVisibility(View.GONE);
                holder.ivTimeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFA855F7));
                holder.tvDateTime.setTextColor(0xFFA855F7);
                holder.nodeCore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFA855F7));
                
                String nextVisit = (appointment.nextVisitDate != null && !appointment.nextVisitDate.isEmpty() && !appointment.nextVisitDate.equals("0000-00-00"))
                        ? appointment.nextVisitDate : "NO FOLLOW-UP";
                holder.tvNextVisitText.setText("NEXT VISIT: " + nextVisit);
            } else if ("declined".equalsIgnoreCase(appointment.status) || "rejected".equalsIgnoreCase(appointment.status) || "cancelled".equalsIgnoreCase(appointment.status)) {
                holder.tvDoctor.setText("Assigned: N/A");
                holder.tvStatus.setText("REJECTED / CANCELLED");
                holder.tvStatus.setTextColor(0xFFFF3B30); // Red
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_pending);
                holder.containerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1AFF3B30));
                holder.statusPulse.setBackgroundColor(0xFFFF3B30);
                
                holder.layoutActionsConfirmed.setVisibility(View.GONE);
                holder.layoutActionsPending.setVisibility(View.GONE);
                holder.layoutActionsCompleted.setVisibility(View.GONE);
                holder.layoutActionsRejected.setVisibility(View.VISIBLE);
                
                holder.ivTimeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                holder.tvDateTime.setTextColor(0xFFFF3B30);
                holder.nodeCore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                
                holder.btnRebookRejected.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), BookingActivity.class);
                    v.getContext().startActivity(intent);
                });
            } else {
                // DEFAULT / PENDING
                holder.tvDoctor.setText("Assigned: Dr. Cruz");
                holder.tvStatus.setText("PENDING");
                holder.tvStatus.setTextColor(0xFF00FFFF); // Neon Cyan
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_pending);
                holder.containerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x2200FFFF));
                holder.statusPulse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00FFFF));

                holder.layoutActionsPending.setVisibility(View.VISIBLE);
                holder.layoutActionsConfirmed.setVisibility(View.GONE);
                holder.layoutActionsCompleted.setVisibility(View.GONE);
                holder.layoutActionsRejected.setVisibility(View.GONE);
            }

            // DOWNPAYMENT CHECK
            String dpStatus = (appointment.downpaymentStatus != null) ? appointment.downpaymentStatus.toLowerCase().trim() : "";
            boolean isApptRejected = "declined".equalsIgnoreCase(appointment.status) || "rejected".equalsIgnoreCase(appointment.status) || "cancelled".equalsIgnoreCase(appointment.status);
            
            // CRITICAL FIX: Include 'cancelled' as a rejection state for downpayments.
            // When an admin 'Declines' proof of payment in the billing dashboard, it sets status to 'cancelled'.
            boolean isPaymentRejected = "declined".equals(dpStatus) || "rejected".equals(dpStatus) || "cancelled".equals(dpStatus);
            
            // If payment or appt is rejected, show Pick New Schedule instead of Pay
            if (isApptRejected || isPaymentRejected) {
                holder.tvStatus.setText("REJECTED / DECLINED");
                holder.tvStatus.setTextColor(0xFFFF3B30); // RED GLOW
                holder.containerStatus.setBackgroundResource(R.drawable.bg_glass_pill);
                holder.containerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x26FF3B30));
                holder.statusPulse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                
                holder.layoutActionsConfirmed.setVisibility(View.GONE);
                holder.layoutActionsPending.setVisibility(View.GONE);
                holder.layoutActionsPayment.setVisibility(View.GONE);
                holder.layoutActionsCompleted.setVisibility(View.GONE);
                holder.layoutActionsRejected.setVisibility(View.VISIBLE);
                
                holder.ivTimeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                holder.tvDateTime.setTextColor(0xFFFF3B30);
                holder.nodeCore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                
                holder.btnRebookRejected.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), BookingActivity.class);
                    v.getContext().startActivity(intent);
                });
            } else {
                // Not rejected, continue with payment/approval logic
                boolean needsPay    = ("unpaid".equals(dpStatus)) && appointment.downpaymentId != null;
                boolean underReview = ("pending".equals(dpStatus) || "for_verification".equals(dpStatus)) && appointment.downpaymentId != null;

                if (needsPay) {
                    holder.layoutActionsPayment.setVisibility(View.VISIBLE);
                    holder.layoutActionsConfirmed.setVisibility(View.GONE);
                    holder.layoutActionsPending.setVisibility(View.GONE);
                    holder.layoutActionsRejected.setVisibility(View.GONE);

                    holder.tvStatus.setText("AWAITING DEPOSIT");
                holder.tvStatus.setTextColor(0xFFFF4B8B);
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_pending);
                holder.containerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1AFF4B8B));
                holder.statusPulse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF4B8B));

                holder.btnPayDownpayment.setOnClickListener(v -> {
                    // Refresh the downpayment ID from the passed intent extra
                    Intent intent = new Intent(v.getContext(), BillingActivity.class);
                    intent.putExtra("auto_pay_id", appointment.downpaymentId);
                    v.getContext().startActivity(intent);
                });
            } else if (underReview) {
                holder.layoutActionsPayment.setVisibility(View.GONE);
                holder.layoutActionsConfirmed.setVisibility(View.GONE);
                holder.layoutActionsPending.setVisibility(View.GONE);

                holder.tvStatus.setText("UNDER REVIEW");
                holder.tvStatus.setTextColor(0xFF4299F0);
                holder.containerStatus.setBackgroundResource(R.drawable.bg_status_pending);
                holder.containerStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x1A4299F0));
                holder.statusPulse.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4299F0));
            } else {
                holder.layoutActionsPayment.setVisibility(View.GONE);
                    if (!isApptRejected) {
                        holder.layoutActionsRejected.setVisibility(View.GONE);
                    }
                }
            }
            
            holder.btnNextVisit.setOnClickListener(v -> {
                String nextVisit = (appointment.nextVisitDate != null && !appointment.nextVisitDate.isEmpty() && !appointment.nextVisitDate.equals("0000-00-00"))
                        ? appointment.nextVisitDate : "Not yet scheduled.";
                String notes = (appointment.checkupNotes != null && !appointment.checkupNotes.isEmpty())
                        ? appointment.checkupNotes : "Visit completed. No specific notes provided.";

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(v.getContext(), R.style.BottomSheetDialogTheme)
                    .setTitle("Check-up Completed")
                    .setMessage("Next Schedule: " + nextVisit + "\n\nDoctor's Notes:\n" + notes)
                    .setPositiveButton("GOT IT", null)
                    .show();
            });

            holder.btnCancel.setOnClickListener(v -> Toast.makeText(v.getContext(), "Cancel requested", Toast.LENGTH_SHORT).show());
            holder.btnReschedule.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), BookingActivity.class);
                intent.putExtra("reschedule_booking_id", appointment.bookingId);
                intent.putExtra("selected_date", appointment.date);
                intent.putExtra("service_type", appointment.serviceType);
                v.getContext().startActivity(intent);
            });
        }

        @Override public int getItemCount() { return list.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvService, tvDoctor, tvDateTime, tvStatus, tvPatient, tvNextVisitText;
            View nodeCore, containerStatus, statusPulse, layoutActionsConfirmed, layoutActionsPending, layoutActionsPayment, layoutActionsCompleted, layoutActionsRejected;
            View btnCancel, btnReschedule, btnPayDownpayment, btnNextVisit, btnRebookRejected;
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
                layoutActionsPayment = v.findViewById(R.id.layoutActionsPayment);
                layoutActionsCompleted = v.findViewById(R.id.layoutActionsCompleted);
                layoutActionsRejected = v.findViewById(R.id.layoutActionsRejected);
                ivTimeIcon = v.findViewById(R.id.ivTimeIcon);
                btnCancel = v.findViewById(R.id.btnCancel);
                btnReschedule = v.findViewById(R.id.btnReschedule);
                btnPayDownpayment = v.findViewById(R.id.btnPayDownpayment);
                btnNextVisit = v.findViewById(R.id.btnNextVisit);
                btnRebookRejected = v.findViewById(R.id.btnRebookRejected);
                tvNextVisitText = v.findViewById(R.id.tvNextVisitText);
            }
        }
    }
}
