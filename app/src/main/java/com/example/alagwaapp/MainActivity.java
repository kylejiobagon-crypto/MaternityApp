package com.example.alagwaapp;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AlagwaMain";

    // ─── DRAWER ──────────────────────────────────
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private View ivMenu;
    private View btnLogout;

    // ─── HEADER ──────────────────────────────────
    private TextView tvWelcomeName;
    
    // ─── STAT CARDS ──────────────────────────────
    private TextView tvStatValue1;   // Today's appointments
    private TextView tvStatValue2;   // Upcoming appointments
    private TextView tvStatValue3;   // Pending bills
    private TextView tvStatValue4;   // Total records

    // ─── PREGNANCY TRACKER (WAVE - OPTION 2) ─────
    private MaternityWaveView maternityWaveView;
    private TextView tvOrbitValue, tvOrbitSubValue, tvDetailDueDate, tvDetailWeeks;
    private View viewTimelineProgress;
    private View statusPulseLive;
    private TextView tvQueueNumber, tvEstimatedWait;

    // All dashboard clean-up.

    // ─── APPOINTMENT PREVIEW ──────────────────────
    private TextView tvApptDate1, tvApptMonth1, tvApptTitle1, tvApptTime1, tvApptStatus1;
    private TextView tvApptDate2, tvApptMonth2, tvApptTitle2, tvApptTime2, tvApptStatus2;
    private LinearLayout layoutApptPreview1, layoutApptPreview2;

    // ─── QUICK ACTIONS ───────────────────────────
    private View qaWalkIn, qaBookAppt, qaViewBilling, qaViewRecords;

    // ─── BOTTOM NAV STUBS ────────────────────────

    // ─── NETWORKING ──────────────────────────────
    private SharedPreferences prefs;
    private ApiService apiService;
    private Retrofit retrofit;
    private OkHttpClient sharedClient;
    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // ─────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);

        // ── Auth Guard ────────────────────────────
        if (!prefs.getBoolean("isLoggedIn", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        bindViews();
        setupStaticContent();      // live date, greeting from prefs cache
        setupQuickActions();       // click listeners for quick action buttons
        setupDrawer();

        NavigationHelper.setupBottomNav(this);

        initNetworking();
        fetchClinicBranding();
        fetchDashboardSummary();   // hits API → fills stat cards + name
        fetchUpcomingAppointments(); // hits API → fills appointment preview
        setupHeroAnimations();     // moving smoke effect
    }

    private void setupHeroAnimations() {
        View smokeLayer = findViewById(R.id.smokeEffectSurface);
        if (smokeLayer == null) return;

        // Infinite Translation (Horizontal movement)
        ObjectAnimator transX = ObjectAnimator.ofFloat(smokeLayer, "translationX", -150f, 150f);
        transX.setDuration(8000);
        transX.setRepeatCount(ObjectAnimator.INFINITE);
        transX.setRepeatMode(ObjectAnimator.REVERSE);
        transX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        transX.start();

        // Infinite Translation (Vertical movement)
        ObjectAnimator transY = ObjectAnimator.ofFloat(smokeLayer, "translationY", -100f, 100f);
        transY.setDuration(12000);
        transY.setRepeatCount(ObjectAnimator.INFINITE);
        transY.setRepeatMode(ObjectAnimator.REVERSE);
        transY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        transY.start();

        // Infinite Scale (Breathing effect)
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(smokeLayer, "scaleX", 1f, 1.4f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(smokeLayer, "scaleY", 1f, 1.4f);
        scaleX.setDuration(10000);
        scaleY.setDuration(10000);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        scaleX.start();
        scaleY.start();
    }

    // ─────────────────────────────────────────────
    //  BIND ALL VIEWS
    // ─────────────────────────────────────────────
    private void bindViews() {
        // Drawer
        drawerLayout      = findViewById(R.id.drawerLayout);
        navigationView    = findViewById(R.id.navView);
        ivMenu            = findViewById(R.id.ivMenu);
        btnLogout         = findViewById(R.id.btnLogout);

        // Header
        tvWelcomeName     = findViewById(R.id.tvWelcomeName);

        // Hero Wave
        maternityWaveView = findViewById(R.id.maternityWaveView);
        tvOrbitValue        = findViewById(R.id.tvOrbitValue);
        tvOrbitSubValue     = findViewById(R.id.tvOrbitSubValue);
        tvDetailDueDate     = findViewById(R.id.tvDetailDueDate);
        tvDetailWeeks       = findViewById(R.id.tvDetailWeeks);

        // Stats
        tvStatValue1      = findViewById(R.id.tvStatValue1);
        tvStatValue2      = findViewById(R.id.tvStatValue2);
        tvStatValue3      = findViewById(R.id.tvStatValue3);
        tvStatValue4      = findViewById(R.id.tvStatValue4);

        // Queue Status (Inside Drawer Header)
        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            View header = navigationView.getHeaderView(0);
            statusPulseLive = header.findViewById(R.id.statusPulseLive);
            tvQueueNumber   = header.findViewById(R.id.tvQueueNumber);
            tvEstimatedWait = header.findViewById(R.id.tvEstimatedWait);
            setupLivePulse();
        }

        // Pregnancy tracker (Compatibility)
        viewTimelineProgress = findViewById(R.id.viewTimelineProgress);

        // Profile warning

        // Ghost stubs
    }

    // ─────────────────────────────────────────────
    //  STATIC / IMMEDIATE CONTENT (no network)
    // ─────────────────────────────────────────────
    private void setupStaticContent() {
        // Greeting from cached name in prefs
        String cachedName = prefs.getString("fullname", "");
        if (!cachedName.isEmpty()) {
            tvWelcomeName.setText(cachedName.toLowerCase());
        }

        // Live date — the API summary will set the specific pregnancy data later
        String today = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                .format(new Date()).toUpperCase();

        // Determine smart time-of-day greeting prefix
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12)      greeting = "Good Morning,";
        else if (hour < 17) greeting = "Good Afternoon,";
        else                greeting = "Good Evening,";

        // Show time-of-day greeting in the header TextView
        // The static "Good Day," label has no ID but we set greeting dynamically in memory
        // No further action needed — the API will overwrite the name field
    }

    // ─────────────────────────────────────────────
    //  QUICK ACTIONS CLICK LISTENERS
    // ─────────────────────────────────────────────
    private void setupQuickActions() {
        // Walk-In → Appointments (first tab)
        View qaWalkIn = findViewById(R.id.qaWalkIn);
        if (qaWalkIn != null) qaWalkIn.setOnClickListener(v -> {
            Intent i = new Intent(this, AppointmentsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            overridePendingTransition(0, 0);
        });

        // Book Appointment → BookingActivity
        View qaBookAppt = findViewById(R.id.qaBookAppt);
        if (qaBookAppt != null) qaBookAppt.setOnClickListener(v -> {
            Intent i = new Intent(this, BookingActivity.class);
            startActivity(i);
        });

        // View Billing → BillingActivity
        View qaViewBilling = findViewById(R.id.qaViewBilling);
        if (qaViewBilling != null) qaViewBilling.setOnClickListener(v -> {
            Intent i = new Intent(this, BillingActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            overridePendingTransition(0, 0);
        });

        // View Records → RecordsActivity
        View qaViewRecords = findViewById(R.id.qaViewRecords);
        if (qaViewRecords != null) qaViewRecords.setOnClickListener(v -> {
            Intent i = new Intent(this, RecordsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
            overridePendingTransition(0, 0);
        });

        // Stat card taps → navigate deeper
        if (tvStatValue1 != null) tvStatValue1.getRootView().setOnClickListener(null); // handled per-card
        View card1 = findViewById(R.id.statCardToday);
        View card2 = findViewById(R.id.statCardUpcoming);
        View card3 = findViewById(R.id.statCardBilling);
        View card4 = findViewById(R.id.statCardRecords);

        if (card1 != null) card1.setOnClickListener(v -> {
            startActivity(new Intent(this, AppointmentsActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        if (card2 != null) card2.setOnClickListener(v -> {
            startActivity(new Intent(this, AppointmentsActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        if (card3 != null) card3.setOnClickListener(v -> {
            startActivity(new Intent(this, BillingActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
        if (card4 != null) card4.setOnClickListener(v -> {
            startActivity(new Intent(this, RecordsActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });

        // "See All" appointments link
        View seeAllAppts = findViewById(R.id.tvSeeAllAppts);
        if (seeAllAppts != null) seeAllAppts.setOnClickListener(v -> {
            startActivity(new Intent(this, AppointmentsActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });


        // Top avatar → ProfileActivity
        View profileAvatar = findViewById(R.id.profilePicContainer);
        if (profileAvatar != null) profileAvatar.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            overridePendingTransition(0, 0);
        });
    }

    // ─────────────────────────────────────────────
    //  DRAWER SETUP
    // ─────────────────────────────────────────────
    private void setupDrawer() {
        if (ivMenu != null) ivMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        if (btnLogout != null) btnLogout.setOnClickListener(v -> handleLogout());

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chat) {
                Intent ci = new Intent(this, ChatActivity.class);
                ci.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(ci);
                overridePendingTransition(0, 0);
            } else if (id == R.id.nav_logout) {
                handleLogout();
            }
            drawerLayout.closeDrawer(GravityCompat.END);
            return true;
        });
    }

    // ─────────────────────────────────────────────
    //  LOGOUT
    // ─────────────────────────────────────────────
    private void handleLogout() {
        prefs.edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ─────────────────────────────────────────────
    //  NETWORKING
    // ─────────────────────────────────────────────
    private void initNetworking() {
        apiService = InfinityFreeClient.buildRetrofit(prefs).create(ApiService.class);
    }

    // ─────────────────────────────────────────────
    //  API: CLINIC BRANDING
    // ─────────────────────────────────────────────
    private void fetchClinicBranding() {
        apiService.getClinicInfo("get_clinic_info", prefs.getInt("tenantId", 1), "true")
                .enqueue(new Callback<ClinicResponse>() {
                    @Override
                    public void onResponse(Call<ClinicResponse> call, Response<ClinicResponse> response) {
                        if (!response.isSuccessful() || response.body() == null) return;
                        ClinicResponse clinic = response.body();

                        // Hide nav items the clinic disabled
                        View navAppts   = findViewById(R.id.navAppointments);
                        View navRecords = findViewById(R.id.navRecords);
                        if ("false".equals(clinic.show_appointments) && navAppts != null) navAppts.setVisibility(View.GONE);
                        if ("false".equals(clinic.show_records)      && navRecords != null) navRecords.setVisibility(View.GONE);
                    }
                    @Override public void onFailure(Call<ClinicResponse> call, Throwable t) {
                        Log.w(TAG, "Branding fetch failed: " + t.getMessage());
                    }
                });
    }

    // ─────────────────────────────────────────────
    //  API: DASHBOARD SUMMARY (name, stats, pregnancy)
    // ─────────────────────────────────────────────
    private void fetchDashboardSummary() {
        String email = prefs.getString("email", "");
        if (email.isEmpty()) {
            loadFallbackStats();
            return;
        }

        apiService.getSummary("stats", "true", email)
                .enqueue(new Callback<SummaryResponse>() {
                    @Override
                    public void onResponse(Call<SummaryResponse> call, Response<SummaryResponse> response) {
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().data == null) {
                            loadFallbackStats();
                            return;
                        }

                        SummaryResponse.Data data = response.body().data;

                        // Persist patient_id for booking / records
                        prefs.edit()
                             .putInt("patient_id", data.patient_id)
                             .putString("fullname", data.fullname != null ? data.fullname : "")
                             .apply();

                        // ── Name ──────────────────────────────
                        if (tvWelcomeName != null) {
                            String name = (data.fullname != null && !data.fullname.isEmpty())
                                    ? data.fullname.toLowerCase() : "patient";
                            tvWelcomeName.setText(name);
                        }

                        // ── Stat Cards ────────────────────────
                        if (tvStatValue1 != null) tvStatValue1.setText(fmt(data.today_queue));
                        if (tvStatValue2 != null) tvStatValue2.setText(fmt(data.upcoming_appointments));
                        if (tvStatValue3 != null) tvStatValue3.setText(fmt(data.getPendingBills()));
                        if (tvStatValue4 != null) tvStatValue4.setText(fmt(data.records_count));

                        // ── Pregnancy Tracker (ORBIT) ─────────
                        SummaryResponse.PregnancyStats ps = data.pregnancy_stats;
                        if (ps != null) {
                            if (tvOrbitValue != null) tvOrbitValue.setText(String.valueOf(ps.weeks));
                            
                            if (tvOrbitSubValue != null) {
                                tvOrbitSubValue.setText(ps.trimester);
                            }

                            if (tvDetailDueDate != null && ps.edd != null) {
                                tvDetailDueDate.setText("Due on " + ps.edd);
                            }

                            // Update Wave Progress
                            if (maternityWaveView != null) {
                                float waveProgress = (float) ps.progress_percent / 100f;
                                maternityWaveView.setProgress(waveProgress);
                            }
                            
                            if (tvDetailWeeks != null) {
                                int weeksToGo = Math.max(0, 40 - ps.weeks);
                                tvDetailWeeks.setText(weeksToGo + " milestone weeks to go");
                            }
                            
                            // Compatibility
                            updateTimelineProgress((float) ps.progress_percent / 100f);
                        }

                    }

                    @Override public void onFailure(Call<SummaryResponse> call, Throwable t) {
                        Log.w(TAG, "Summary fetch failed: " + t.getMessage());
                        loadFallbackStats();
                    }
                });
    }

    /** Show dashes while API is unavailable so the card doesn't show stale 0s */
    private void loadFallbackStats() {
        if (tvStatValue1 != null) tvStatValue1.setText("0");
        if (tvStatValue2 != null) tvStatValue2.setText("0");
        if (tvStatValue3 != null) tvStatValue3.setText("0");
        if (tvStatValue4 != null) tvStatValue4.setText("0");
        String cached = prefs.getString("fullname", "");
        if (tvWelcomeName != null && !cached.isEmpty()) tvWelcomeName.setText(cached.toLowerCase());
    }

    // ─────────────────────────────────────────────
    //  API: UPCOMING APPOINTMENTS PREVIEW
    // ─────────────────────────────────────────────
    private void fetchUpcomingAppointments() {
        apiService.getBookingsRaw("list", "true").enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call,
                                   Response<okhttp3.ResponseBody> response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        clearApptPreview();
                        return;
                    }
                    String raw = response.body().string();
                    if (!raw.trim().startsWith("[")) { clearApptPreview(); return; }

                    AppointmentResponse.Appointment[] arr =
                            new com.google.gson.Gson().fromJson(raw, AppointmentResponse.Appointment[].class);

                    if (arr == null || arr.length == 0) { clearApptPreview(); return; }

                    bindApptPreviewCard(1, arr.length > 0 ? arr[0] : null);
                    bindApptPreviewCard(2, arr.length > 1 ? arr[1] : null);

                } catch (Exception e) {
                    Log.w(TAG, "Appt parse error: " + e.getMessage());
                    clearApptPreview();
                }
            }

            @Override public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                clearApptPreview();
            }
        });
    }

    private void bindApptPreviewCard(int cardNum, AppointmentResponse.Appointment appt) {
        if (appt == null) return;

        String dateId1   = cardNum == 1 ? "tvApptDate1"   : "tvApptDate2";
        String monthId1  = cardNum == 1 ? "tvApptMonth1"  : "tvApptMonth2";
        String titleId1  = cardNum == 1 ? "tvApptTitle1"  : "tvApptTitle2";
        String timeId1   = cardNum == 1 ? "tvApptTime1"   : "tvApptTime2";
        String statusId1 = cardNum == 1 ? "tvApptStatus1" : "tvApptStatus2";

        try {
            // Parse date string (yyyy-MM-dd)
            java.text.SimpleDateFormat inFmt  = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = inFmt.parse(appt.date != null ? appt.date : "2026-03-27");

            TextView tvDay    = getTextById(dateId1);
            TextView tvMonth  = getTextById(monthId1);
            TextView tvTitle  = getTextById(titleId1);
            TextView tvTime   = getTextById(timeId1);
            TextView tvStatus = getTextById(statusId1);

            if (tvDay    != null) tvDay.setText(new SimpleDateFormat("dd",  Locale.getDefault()).format(d));
            if (tvMonth  != null) tvMonth.setText(new SimpleDateFormat("MMM", Locale.getDefault()).format(d));
            if (tvTitle  != null) tvTitle.setText(appt.serviceType  != null ? appt.serviceType  : "Appointment");
            if (tvTime   != null) tvTime.setText((appt.time != null ? appt.time : "–") + " · Dr. Cruz");
            if (tvStatus != null) {
                String status = appt.status != null ? appt.status : "Pending";
                tvStatus.setText(status);
                tvStatus.setTextColor("Confirmed".equalsIgnoreCase(status)
                        ? Color.parseColor("#34D399") : Color.parseColor("#FB923C"));
            }
        } catch (Exception e) {
            Log.w(TAG, "appt bind error: " + e.getMessage());
        }
    }

    /** Hardcoded preview if API is unavailable */
    private void clearApptPreview() {
        // Clear Card 1
        setTv("tvApptDate1",   "—");
        setTv("tvApptMonth1",  "Empty");
        setTv("tvApptTitle1",  "No upcoming appts");
        setTv("tvApptTime1",   "Tap to book a schedule");
        setTv("tvApptStatus1", "");

        // Clear Card 2
        setTv("tvApptDate2",   "—");
        setTv("tvApptMonth2",  "Empty");
        setTv("tvApptTitle2",  "No data available");
        setTv("tvApptTime2",   "–");
        setTv("tvApptStatus2", "");
    }

    // ─────────────────────────────────────────────
    //  PREGNANCY TRACKER PROGRESS
    // ─────────────────────────────────────────────
    private void updateTimelineProgress(float progress) {
        if (viewTimelineProgress == null) return;
        viewTimelineProgress.post(() -> {
            android.view.ViewGroup.LayoutParams params = viewTimelineProgress.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) params).weight = progress;
            } else {
                // RelativeLayout: update width as fraction of parent
                View parent = (View) viewTimelineProgress.getParent();
                if (parent != null) {
                    int parentW = parent.getWidth();
                    params.width = (int) (parentW * progress);
                }
            }
            viewTimelineProgress.setLayoutParams(params);
        });
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────
    private String fmt(int value) {
        return String.valueOf(value);
    }

    private TextView getTextById(String name) {
        int id = getResources().getIdentifier(name, "id", getPackageName());
        if (id == 0) return null;
        return findViewById(id);
    }

    private void setTv(String idName, String text) {
        TextView tv = getTextById(idName);
        if (tv != null) tv.setText(text);
    }

    private void setupLivePulse() {
        if (statusPulseLive == null) return;
        android.view.animation.AlphaAnimation pulse = new android.view.animation.AlphaAnimation(1.0f, 0.4f);
        pulse.setDuration(1200);
        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
        statusPulseLive.startAnimation(pulse);
    }
}