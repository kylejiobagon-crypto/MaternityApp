package com.example.alagwaapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RecordsActivity extends AppCompatActivity implements CheckupAdapter.OnCheckupClickListener {
    private static final String TAG = "RecordsActivity";
    private RecyclerView recyclerView;
    private CheckupAdapter adapter;
    private List<Checkup> checkupList = new ArrayList<>();
    
    // Top Vitals Card
    private TextView tvLatestWeight, tvLatestBP, tvLatestFHT, tvLatestAOG;
    
    private ApiService apiService;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);
        
        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        
        // 1. Navigation Setup
        NavigationHelper.setupBottomNav(this);
        
        // 2. Bind Views
        bindViews();
        
        // 3. Initialize Networking (Automatic AES challenge solver)
        apiService = InfinityFreeClient.buildRetrofit(prefs).create(ApiService.class);
        
        // 4. Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CheckupAdapter(checkupList, this);
        recyclerView.setAdapter(adapter);
        
        // 5. Fetch Real Data
        fetchCheckupHistory();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recyclerViewRecords);
        tvLatestWeight = findViewById(R.id.tvLatestWeight);
        tvLatestBP = findViewById(R.id.tvLatestBP);
        tvLatestFHT = findViewById(R.id.tvLatestFHT);
        tvLatestAOG = findViewById(R.id.tvLatestAOG);
    }

    private void fetchCheckupHistory() {
        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");
        String role = prefs.getString("role", "patient");
        int tenantId = prefs.getInt("tenant_id", 1);
        apiService.getCheckupHistoryRaw("get_checkup_history", "true", email, username, role, tenantId)
                .enqueue(new Callback<okhttp3.ResponseBody>() {
                    @Override
                    public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                        try {
                            if (!response.isSuccessful() || response.body() == null) {
                                Log.e(TAG, "API Response Fail: " + response.code());
                                return;
                            }
                            
                            String raw = response.body().string();
                            
                            // ── SENSITIVE RESPONSE CLEANING ────────────────────
                            // If we still see common HTML/JS artifacts, it means the challenge was missed
                            if (raw.contains("<html>") || raw.contains("slowAES")) {
                                Log.e(TAG, "Network blocked by host (Challenge not solved).");
                                return;
                            }

                            int start = raw.indexOf('{');
                            if (start < 0) {
                                Log.e(TAG, "No JSON found: " + raw.substring(0, Math.min(100, raw.length())));
                                return;
                            }
                            raw = raw.substring(start);
                            Log.d(TAG, "CLEANED RAW: " + raw);
                            
                            CheckupHistoryResponse history = new com.google.gson.Gson().fromJson(raw, CheckupHistoryResponse.class);
                            if (history != null && history.success && history.data != null) {
                                checkupList.clear();
                                checkupList.addAll(history.data);
                                adapter.notifyDataSetChanged();
                                
                                // Update Top Card with latest record
                                if (!checkupList.isEmpty()) {
                                    updateLatestVitals(checkupList.get(0));
                                }
                            } else {
                                Log.e(TAG, "API Success False: " + (history != null ? history.message : "null"));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Parse Error: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                        Log.e(TAG, "API Connect Fail: " + t.getMessage());
                        Toast.makeText(RecordsActivity.this, "Connection Error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateLatestVitals(Checkup latest) {
        if (tvLatestWeight != null) tvLatestWeight.setText(latest.weight != null ? latest.weight + " kg" : "--");
        if (tvLatestBP != null) tvLatestBP.setText(latest.bloodPressure != null ? latest.bloodPressure : "--/--");
        if (tvLatestFHT != null) tvLatestFHT.setText(latest.fht != null ? latest.fht + " bpm" : "--");
        
        // For AOG, we use BPD as a placeholder if AOG field isn't explicitly in the Checkup yet
        if (tvLatestAOG != null) {
            tvLatestAOG.setText(latest.aogWeeks != null ? latest.aogWeeks + " Weeks" : "--");
        }
    }

    @Override
    public void onCheckupClick(Checkup checkup) {
        showClinicalDetailDialog(checkup);
    }

    private void showClinicalDetailDialog(Checkup checkup) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_clinical_detail, null);
        
        // Bind Dialog Views
        TextView tvDetailWeight = view.findViewById(R.id.tvDetailWeight);
        TextView tvDetailBP = view.findViewById(R.id.tvDetailBP);
        TextView tvDetailFHT = view.findViewById(R.id.tvDetailFHT);
        TextView tvDetailAOG = view.findViewById(R.id.tvDetailAOG);
        TextView tvBPD = view.findViewById(R.id.tvBPD);
        TextView tvAC = view.findViewById(R.id.tvAC);
        TextView tvFL = view.findViewById(R.id.tvFL);
        TextView tvDiagnosis = view.findViewById(R.id.tvDiagnosis);
        TextView tvAdvice = view.findViewById(R.id.tvAdvice);
        TextView tvPrescriptions = view.findViewById(R.id.tvPrescriptions);
        TextView tvAttendingDr = view.findViewById(R.id.tvAttendingDr);
        
        // Set Data
        if (tvDetailWeight != null) tvDetailWeight.setText(checkup.weight != null ? checkup.weight + " kg" : "--");
        if (tvDetailBP != null) tvDetailBP.setText(checkup.bloodPressure != null ? checkup.bloodPressure : "--/--");
        if (tvDetailFHT != null) tvDetailFHT.setText(checkup.fht != null ? checkup.fht + " bpm" : "--");
        if (tvDetailAOG != null) tvDetailAOG.setText(checkup.aogWeeks != null ? checkup.aogWeeks + " Weeks" : "N/A");
        
        if (tvBPD != null) tvBPD.setText(checkup.bpd != null && !checkup.bpd.isEmpty() ? checkup.bpd + " mm" : "N/A");
        if (tvAC != null) tvAC.setText(checkup.ac != null && !checkup.ac.isEmpty() ? checkup.ac + " mm" : "N/A");
        if (tvFL != null) tvFL.setText(checkup.fl != null && !checkup.fl.isEmpty() ? checkup.fl + " mm" : "N/A");
        
        if (tvDiagnosis != null) tvDiagnosis.setText(checkup.diagnosis != null && !checkup.diagnosis.isEmpty() ? checkup.diagnosis : "Patient in normal condition.");
        if (tvAdvice != null) tvAdvice.setText(checkup.notes != null && !checkup.notes.isEmpty() ? checkup.notes : "No specific advice given.");
        
        // Prescriptions (Mocking list format from text)
        if (tvPrescriptions != null) {
            tvPrescriptions.setText("• Vital Vitamins (Prescribed)\n• Iron Supplement\n• Periodic Rest");
        }
        
        // Header
        if (tvAttendingDr != null) {
            tvAttendingDr.setText("Assessment Date: " + checkup.checkupDate + " " + checkup.checkupTime);
        }

        View btnClose = view.findViewById(R.id.btnCloseDetail);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(view);
        dialog.show();
    }
}
