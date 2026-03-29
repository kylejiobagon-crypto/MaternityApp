package com.example.alagwaapp;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class BillingActivity extends AppCompatActivity {

    private static final String TAG = "AlagwaBilling";

    // ─── Header Views ─────────────────────────────
    private TextView tvSessionDate, tvBillingUserName;

    // ─── Stat Cards ───────────────────────────────
    private TextView tvTotalRevenue, tvPendingAmount, tvVerificationAmount, tvDownpaymentTotal;

    // ─── Search / Filter ──────────────────────────
    private EditText etBillingSearch;
    private TextView chipAll, chipPaid, chipPending, chipVerif, chipDownpayment;
    
    private RecyclerView rvInvoices;
    private LinearLayout billingLoadingLayout, billingEmptyLayout;
    private TextView tvInvoiceCount;

    private ApiService apiService;
    private InvoiceAdapter invoiceAdapter;
    private List<JsonObject> allInvoices = new ArrayList<>();
    private List<JsonObject> filteredInvoices = new ArrayList<>();

    // ─── Filter state ─────────────────────────────
    private String activeStatus = "all"; // all, paid, pending, for_verification, downpayment
    private String activeSearch = "";

    // ─── Networking ───────────────────────────────
    private SharedPreferences prefs;
    private OkHttpClient okClient;
    private final String BASE_URL = "http://alagawa.ct.ws/";
    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // ─── Auto-Refresh Polling ─────────────────────
    private android.os.Handler pollHandler;
    private Runnable pollRunnable;
    private static final long POLL_INTERVAL_MS = 10_000; // Refresh every 10 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);

        bindViews();
        setupChips();
        setupSearch();
        setupNavigation();
        setupStaticHeader();

        initClient();
        fetchBillingSummary();
        fetchInvoices();
        setupHeroAnimations();
        
        checkAutoOpenPayment();

        // Set up polling runnable
        pollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                fetchBillingSummary();
                fetchInvoices();
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh immediately when screen is visible (e.g. returning from payment)
        fetchBillingSummary();
        fetchInvoices();
        // Start periodic polling
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop polling when screen is hidden to save battery
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void checkAutoOpenPayment() {
        int autoPayId = getIntent().getIntExtra("auto_pay_id", -1);
        if (autoPayId != -1) {
            // We need to wait for invoices to load
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                for (JsonObject inv : allInvoices) {
                    if (safeInt(inv, "payment_id") == autoPayId) {
                        onPayNow(autoPayId, safeDouble(inv, "amount"), safeStr(inv, "payment_type"));
                        break;
                    }
                }
            }, 1200); // Wait for data fetch
        }
    }

    private void bindViews() {
        tvSessionDate        = findViewById(R.id.tvSessionDate);
        tvBillingUserName    = findViewById(R.id.tvBillingUserName);
        tvTotalRevenue       = findViewById(R.id.tvTotalRevenue);
        tvPendingAmount      = findViewById(R.id.tvPendingAmount);
        tvVerificationAmount = findViewById(R.id.tvVerificationAmount);
        tvDownpaymentTotal   = findViewById(R.id.tvDownpaymentTotal);
        etBillingSearch      = findViewById(R.id.etBillingSearch);
        
        chipAll              = findViewById(R.id.chipAll);
        chipPaid             = findViewById(R.id.chipPaid);
        chipPending          = findViewById(R.id.chipPending);
        chipVerif            = findViewById(R.id.chipVerif);
        chipDownpayment      = findViewById(R.id.chipDownpayment);

        rvInvoices           = findViewById(R.id.rvInvoices);
        billingLoadingLayout = findViewById(R.id.billingLoadingLayout);
        billingEmptyLayout   = findViewById(R.id.billingEmptyLayout);
        tvInvoiceCount       = findViewById(R.id.tvInvoiceCount);

        rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        invoiceAdapter = new InvoiceAdapter(filteredInvoices);
        rvInvoices.setAdapter(invoiceAdapter);
    }

    private void setupStaticHeader() {
        String name = prefs.getString("fullname", "Patient Account");
        if (tvBillingUserName != null) tvBillingUserName.setText(name);

        String sessionText = "Fiscal Session: Live";
        if (tvSessionDate != null) tvSessionDate.setText(sessionText);
    }

    private void setupChips() {
        View.OnClickListener listener = v -> {
            // Reset all chips
            chipAll.setBackgroundResource(R.drawable.bg_chip_unselected);
            chipAll.setTextColor(Color.parseColor("#6B7280"));
            chipPaid.setBackgroundResource(R.drawable.bg_chip_unselected);
            chipPaid.setTextColor(Color.parseColor("#6B7280"));
            chipPending.setBackgroundResource(R.drawable.bg_chip_unselected);
            chipPending.setTextColor(Color.parseColor("#6B7280"));
            chipVerif.setBackgroundResource(R.drawable.bg_chip_unselected);
            chipVerif.setTextColor(Color.parseColor("#6B7280"));
            chipDownpayment.setBackgroundResource(R.drawable.bg_chip_unselected);
            chipDownpayment.setTextColor(Color.parseColor("#6B7280"));

            // Select clicked chip
            ((TextView) v).setBackgroundResource(R.drawable.bg_chip_selected);
            ((TextView) v).setTextColor(Color.WHITE);

            // Update state
            if (v.getId() == R.id.chipAll) activeStatus = "all";
            else if (v.getId() == R.id.chipPaid) activeStatus = "paid";
            else if (v.getId() == R.id.chipPending) activeStatus = "pending";
            else if (v.getId() == R.id.chipVerif) activeStatus = "for_verification";
            else if (v.getId() == R.id.chipDownpayment) activeStatus = "downpayment";

            filterAndRefresh();
        };

        chipAll.setOnClickListener(listener);
        chipPaid.setOnClickListener(listener);
        chipPending.setOnClickListener(listener);
        chipVerif.setOnClickListener(listener);
        chipDownpayment.setOnClickListener(listener);
    }

    private void setupSearch() {
        etBillingSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                activeSearch = s.toString().toLowerCase().trim();
                filterAndRefresh();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupHeroAnimations() {
        View smokeLayer = findViewById(R.id.smokeEffectSurface);
        if (smokeLayer == null) return;

        // Infinite Translation (Horizontal movement)
        ObjectAnimator transX = ObjectAnimator.ofFloat(smokeLayer, "translationX", -150f, 150f);
        transX.setDuration(8000);
        transX.setRepeatCount(ObjectAnimator.INFINITE);
        transX.setRepeatMode(ObjectAnimator.REVERSE);
        transX.setInterpolator(new AccelerateDecelerateInterpolator());
        transX.start();

        // Infinite Translation (Vertical movement)
        ObjectAnimator transY = ObjectAnimator.ofFloat(smokeLayer, "translationY", -100f, 100f);
        transY.setDuration(12000);
        transY.setRepeatCount(ObjectAnimator.INFINITE);
        transY.setRepeatMode(ObjectAnimator.REVERSE);
        transY.setInterpolator(new AccelerateDecelerateInterpolator());
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

    private void setupNavigation() {
        NavigationHelper.setupBottomNav(this);
    }

    private void initClient() {
        // Use the centralized InfinityFree client that auto-solves AES challenges
        apiService = InfinityFreeClient.buildRetrofit(prefs).create(ApiService.class);
    }

    private void fetchBillingSummary() {
        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");
        String role = prefs.getString("role", "patient");
        int tenantId = prefs.getInt("tenant_id", 1);
        apiService.getBillingRaw("summary", "true", email, username, role, tenantId).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> resp) {
                try {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        runOnUiThread(() -> loadFallbackSummary());
                        return;
                    }
                    String body = resp.body().string();
                    android.util.Log.d("AlagwaBillingRaw", "Summary Body: " + body);

                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        JsonObject data = json.getAsJsonObject("data");

                        double revenue = safeDouble(data, "total_revenue");
                        double pending = safeDouble(data, "pending_amount");
                        double verif   = safeDouble(data, "verification_amount");
                        double wallet  = safeDouble(data, "wallet_balance");

                        runOnUiThread(() -> {
                            tvTotalRevenue.setText(formatPeso(revenue));
                            tvPendingAmount.setText(formatPeso(pending));
                            tvVerificationAmount.setText(formatPeso(verif));
                            tvDownpaymentTotal.setText(formatPeso(wallet));
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Summary parse error: " + e.getMessage());
                    runOnUiThread(() -> loadFallbackSummary());
                }
            }
            @Override public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                Log.w(TAG, "Summary network error: " + t.getMessage());
                runOnUiThread(() -> loadFallbackSummary());
            }
        });
    }

    private void fetchInvoices() {
        showLoading(true);
        String email = prefs.getString("email", "");
        String username = prefs.getString("username", "");
        String role = prefs.getString("role", "patient");
        int tenantId = prefs.getInt("tenant_id", 1);
        apiService.getBillingRaw("list", "true", email, username, role, tenantId).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> resp) {
                try {
                    showLoading(false);
                    if (!resp.isSuccessful() || resp.body() == null) {
                        showEmpty(true);
                        return;
                    }
                    String body = resp.body().string();
                    android.util.Log.d("AlagwaBillingRaw", "List Body: " + body);

                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        JsonArray data = json.getAsJsonArray("data");
                        allInvoices.clear();
                        for (int i = 0; i < data.size(); i++) {
                            allInvoices.add(data.get(i).getAsJsonObject());
                        }
                        filterAndRefresh();
                    } else {
                        showEmpty(true);
                    }
                } catch (Exception e) {
                    showLoading(false);
                    showEmpty(true);
                    Log.e(TAG, "Invoices parse error: " + e.getMessage());
                }
            }
            @Override public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                showLoading(false);
                showEmpty(true);
                Log.w(TAG, "Invoices network error: " + t.getMessage());
            }
        });
    }

    private void loadFallbackSummary() {
        tvTotalRevenue.setText("₱0.00");
        tvPendingAmount.setText("₱0.00");
        tvVerificationAmount.setText("₱0.00");
        tvDownpaymentTotal.setText("₱0.00");
    }

    private void filterAndRefresh() {
        filteredInvoices.clear();
        for (JsonObject inv : allInvoices) {
            String status  = safeStr(inv, "status").toLowerCase();
            String payType = safeStr(inv, "payment_type").toLowerCase();
            String service = (safeStr(inv, "service_type") + " " + safeStr(inv, "description")).toLowerCase();

            // Filter logic
            boolean matchesStatus = "all".equals(activeStatus)
                    || ("pending".equals(activeStatus) && ("pending".equals(status) || "unpaid".equals(status)))
                    || (activeStatus.equals("downpayment") && "downpayment".equals(payType))
                    || (activeStatus.equals(status));

            boolean matchesSearch = activeSearch.isEmpty() || service.contains(activeSearch.toLowerCase());

            if (matchesStatus && matchesSearch) {
                filteredInvoices.add(inv);
            }
        }

        runOnUiThread(() -> {
            invoiceAdapter.notifyDataSetChanged();
            showEmpty(filteredInvoices.isEmpty());
            tvInvoiceCount.setText(filteredInvoices.size() + " records found");
            if (!filteredInvoices.isEmpty()) rvInvoices.setVisibility(View.VISIBLE);
        });
    }

    private String selectedMethod = ""; // No default — user must pick

    /** Uses ZXing Core to generate a real, scannable QR code bitmap */
    private Bitmap generateRealQrCode(String content, int sizePx) {
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);
            com.google.zxing.common.BitMatrix matrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "QR generation failed", e);
            return null;
        }
    }

    /** Called with service name so receipt can show what the patient is paying for */
    private void onPayNow(int paymentId, double amount, String type, String serviceName) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_payment, null);
        dialog.setContentView(view);
        View parent = (View) view.getParent();
        if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);

        // ── Bind all views ──────────────────────────────────────────
        TextView bsPayAmount   = view.findViewById(R.id.bsPayAmount);
        TextView bsPayService  = view.findViewById(R.id.bsPayService);
        TextView tvClinicName  = view.findViewById(R.id.tvClinicName);
        TextView tvClinicAddr  = view.findViewById(R.id.tvClinicAddress);
        TextView tvClinicPhone = view.findViewById(R.id.tvClinicPhone);
        ImageView ivQr         = view.findViewById(R.id.ivQrCode);
        View scanLine          = view.findViewById(R.id.scanLine);
        View receiptCard       = view.findViewById(R.id.receiptCard);
        EditText etRefNo       = view.findViewById(R.id.etRefNo);
        TextView btnSubmit     = view.findViewById(R.id.btnSubmitPayment);
        View methodGcash       = view.findViewById(R.id.methodGcash);
        View methodMaya        = view.findViewById(R.id.methodMaya);
        View methodCash        = view.findViewById(R.id.methodCash);
        View refContainer      = view.findViewById(R.id.refContainer);
        View cashContainer     = view.findViewById(R.id.cashInfoContainer);

        // ── Populate receipt fields ──────────────────────────────────
        bsPayAmount.setText(formatPeso(amount));
        String displayService = (serviceName != null && !serviceName.isEmpty()) ? serviceName
                : (type.equals("downpayment") ? "Appointment Deposit" : "Medical Service Fee");
        bsPayService.setText(displayService);

        SharedPreferences prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        String clinicName  = prefs.getString("clinic_name",  "Alagwa Maternity Clinic").toUpperCase();
        String clinicAddr  = prefs.getString("clinic_address", "");
        String clinicPhone = prefs.getString("contact_number", "0000 000 0000");
        tvClinicName.setText(clinicName);
        if (!clinicAddr.isEmpty()) tvClinicAddr.setText(clinicAddr); else tvClinicAddr.setVisibility(View.GONE);
        tvClinicPhone.setText(clinicPhone); // already formatted, no "Tel: Tel:" double prefix

        // ── Generate REAL scannable QR via ZXing (background thread) ───
        String qrContent = "ALAGWA:PAY-" + paymentId + ":" + amount;
        new Thread(() -> {
            Bitmap qrBitmap = generateRealQrCode(qrContent, 640);
            runOnUiThread(() -> {
                if (qrBitmap != null) ivQr.setImageBitmap(qrBitmap);
            });
        }).start();

        // ── Entrance animation: receipt card scales + fades in ─────────
        receiptCard.setAlpha(0f);
        receiptCard.setScaleX(0.92f);
        receiptCard.setScaleY(0.92f);
        receiptCard.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // ── Scan laser animation ───────────────────────────────────────
        float qrBoxDp = 160f;
        float laserTravel = (qrBoxDp - 12f) * getResources().getDisplayMetrics().density;
        ObjectAnimator scanAnim = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, laserTravel);
        scanAnim.setDuration(1600);
        scanAnim.setRepeatMode(ObjectAnimator.REVERSE);
        scanAnim.setRepeatCount(ObjectAnimator.INFINITE);
        scanAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        scanAnim.start();
        // Also pulse the laser alpha
        ObjectAnimator laserPulse = ObjectAnimator.ofFloat(scanLine, "alpha", 0.55f, 1f);
        laserPulse.setDuration(800);
        laserPulse.setRepeatMode(ObjectAnimator.REVERSE);
        laserPulse.setRepeatCount(ObjectAnimator.INFINITE);
        laserPulse.start();

        // Stop animations when dialog dismissed
        dialog.setOnDismissListener(d -> { scanAnim.cancel(); laserPulse.cancel(); });

        // ── Initial state: nothing selected, CTA disabled ──────────────
        selectedMethod = "";
        refContainer.setVisibility(View.GONE);
        cashContainer.setVisibility(View.GONE);
        if (type.equals("downpayment")) methodCash.setVisibility(View.GONE);
        btnSubmit.setAlpha(0.38f);
        btnSubmit.setClickable(false);
        btnSubmit.setFocusable(false);

        // ── Method selection ───────────────────────────────────────────
        View.OnClickListener methodListener = v -> {
            // Reset cards
            methodGcash.setBackgroundResource(R.drawable.bg_payment_method_default);
            methodMaya.setBackgroundResource(R.drawable.bg_payment_method_default);
            methodCash.setBackgroundResource(R.drawable.bg_payment_method_default);
            // Scale-bounce selected card
            v.setBackgroundResource(R.drawable.bg_payment_method_selected);
            v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start()
            ).start();

            refContainer.setVisibility(View.GONE);
            cashContainer.setVisibility(View.GONE);

            if (v.getId() == R.id.methodGcash) {
                selectedMethod = "gcash";
                revealView(refContainer);
                btnSubmit.setText("Pay via GCash — " + formatPeso(amount));
            } else if (v.getId() == R.id.methodMaya) {
                selectedMethod = "maya";
                revealView(refContainer);
                btnSubmit.setText("Pay via Maya — " + formatPeso(amount));
            } else {
                selectedMethod = "cash";
                revealView(cashContainer);
                btnSubmit.setText("Confirm Cash Payment — " + formatPeso(amount));
            }

            // Animate CTA enabling
            btnSubmit.animate().alpha(1f).setDuration(200).start();
            btnSubmit.setClickable(true);
            btnSubmit.setFocusable(true);
        };

        methodGcash.setOnClickListener(methodListener);
        methodMaya.setOnClickListener(methodListener);
        methodCash.setOnClickListener(methodListener);

        btnSubmit.setOnClickListener(v -> {
            if (selectedMethod.isEmpty()) return;
            String ref = etRefNo.getText().toString().trim();
            if (type.equals("downpayment")) {
                if (ref.isEmpty()) {
                    Toast.makeText(this, "Please enter your GCash/Maya reference number", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitDownpaymentToApi(selectedMethod, paymentId, ref);
            } else {
                if (!selectedMethod.equals("cash") && ref.isEmpty()) {
                    Toast.makeText(this, "Please enter your reference number", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitPaymentToApi(paymentId, selectedMethod, ref);
            }
            dialog.dismiss();
        });
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheetInternal = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal)
                        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal)
                        .setSkipCollapsed(true);
            }
        });

        dialog.show();
    }

    // Overload to keep backward compatibility with call sites that don't pass a service name
    private void onPayNow(int paymentId, double amount, String type) {
        onPayNow(paymentId, amount, type, "");
    }

    /** Smoothly animate a hidden view into view */
    private void revealView(View v) {
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.setTranslationY(-12f);
        v.animate().alpha(1f).translationY(0f).setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void submitPaymentToApi(int paymentId, String method, String ref) {
        apiService.submitPayment("submit_payment", paymentId, method, ref, "true").enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> resp) {
                try {
                    if (resp.isSuccessful() && resp.body() != null) {
                        String body = resp.body().string();
                        JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                        if (json.has("success") && json.get("success").getAsBoolean()) {
                            runOnUiThread(() -> {
                                Toast.makeText(BillingActivity.this, "✅ Payment Submitted!", Toast.LENGTH_SHORT).show();
                                fetchBillingSummary();
                                fetchInvoices();
                            });
                            return;
                        }
                    }
                    runOnUiThread(() -> Toast.makeText(BillingActivity.this, "❌ Submission Failed", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Log.e(TAG, "Payment submit error: " + e.getMessage());
                }
            }
            @Override public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                runOnUiThread(() -> Toast.makeText(BillingActivity.this, "Network Error", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void submitDownpaymentToApi(String method, int paymentId, String ref) {
        apiService.submitDownpaymentProof("submit_proof", "true", paymentId, ref).enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<okhttp3.ResponseBody> call, retrofit2.Response<okhttp3.ResponseBody> resp) {
                try {
                    String body = resp.body() != null ? resp.body().string() : "";
                    Log.d(TAG, "Downpayment submit response [" + resp.code() + "]: " + body);
                    if (resp.isSuccessful() && !body.isEmpty()) {
                        // Strip any PHP warnings before JSON
                        int jsonStart = body.indexOf('{');
                        if (jsonStart > 0) body = body.substring(jsonStart);
                        JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                        boolean success = json.has("success") && json.get("success").getAsBoolean();
                        if (success) {
                            runOnUiThread(() -> {
                                Toast.makeText(BillingActivity.this, "✅ Downpayment Submitted! Awaiting admin verification.", Toast.LENGTH_LONG).show();
                                fetchBillingSummary();
                                fetchInvoices();
                            });
                            return;
                        } else {
                            String msg = json.has("message") ? json.get("message").getAsString() : "Submission failed";
                            runOnUiThread(() -> Toast.makeText(BillingActivity.this, "❌ " + msg, Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                    runOnUiThread(() -> Toast.makeText(BillingActivity.this, "❌ Submission Failed (HTTP " + resp.code() + ")", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Log.e(TAG, "Downpayment submit error: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(BillingActivity.this, "❌ Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
            @Override public void onFailure(retrofit2.Call<okhttp3.ResponseBody> call, Throwable t) {
                Log.e(TAG, "Downpayment network failure: " + t.getMessage());
                runOnUiThread(() -> Toast.makeText(BillingActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ─────────────────────────────────────────────
    //  BOTTOM SHEET RECEIPT
    // ─────────────────────────────────────────────
    private void showBottomSheetReceipt(JsonObject inv) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_receipt, null);
        dialog.setContentView(view);
        
        // Ensure background is transparent so custom drawable shows
        View parent = (View) view.getParent();
        if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);

        String id       = safeStr(inv, "payment_id");
        double subtotal = safeDouble(inv, "subtotal");
        if (subtotal <= 0) subtotal = safeDouble(inv, "amount"); // Fallback

        double discount = safeDouble(inv, "discount_amount");
        double downpay  = safeDouble(inv, "downpayment_applied");
        double finalTotal = Math.max(0, subtotal - discount - downpay);
        String status   = safeStr(inv, "status");

        TextView bsInvoiceId = view.findViewById(R.id.bsInvoiceId);
        TextView bsTotalAmount = view.findViewById(R.id.bsTotalAmount);
        TextView bsStatus = view.findViewById(R.id.bsStatus);
        TextView bsSubtotal = view.findViewById(R.id.bsSubtotal);
        TextView bsDiscount = view.findViewById(R.id.bsDiscount);
        TextView bsDownpaymentApplied = view.findViewById(R.id.bsDownpaymentApplied);
        TextView bsFinalTotal = view.findViewById(R.id.bsFinalTotal);
        
        TextView bsClinicName = view.findViewById(R.id.bsClinicName);
        TextView bsClinicAddress = view.findViewById(R.id.bsClinicAddress);
        TextView bsServicesList = view.findViewById(R.id.bsServicesList);
        TextView bsMedicationsList = view.findViewById(R.id.bsMedicationsList);
        View rowServices = view.findViewById(R.id.rowServices);
        View rowMedications = view.findViewById(R.id.rowMedications);

        LinearLayout bsDiscountRow = view.findViewById(R.id.bsDiscountRow);
        LinearLayout bsDownpaymentRow = view.findViewById(R.id.bsDownpaymentRow);

        // Populate Branding
        String cName = safeStr(inv, "clinic_name");
        String cAddr = safeStr(inv, "clinic_address");
        if (!cName.isEmpty()) bsClinicName.setText(cName);
        if (!cAddr.isEmpty()) bsClinicAddress.setText(cAddr);

        // Populate Itemized List
        String sList = safeStr(inv, "services_list").replace("; ", "\n• ");
        String mList = safeStr(inv, "medications_list").replace("; ", "\n• ");

        if (!sList.isEmpty()) {
            rowServices.setVisibility(View.VISIBLE);
            bsServicesList.setText("• " + sList);
        }
        if (!mList.isEmpty()) {
            rowMedications.setVisibility(View.VISIBLE);
            bsMedicationsList.setText("• " + mList);
        }

        bsInvoiceId.setText("#PAY-" + String.format("%04d", safeInt(inv, "payment_id")));
        bsTotalAmount.setText(formatPeso(finalTotal));
        bsSubtotal.setText(formatPeso(subtotal));
        boolean isDownpay = "downpayment".equalsIgnoreCase(safeStr(inv, "payment_type"));
        bsStatus.setText(friendlyStatus(status, isDownpay));
        bsStatus.setTextColor(statusColors(status, isDownpay)[0]);
        
        if (discount > 0) {
            bsDiscountRow.setVisibility(View.VISIBLE);
            bsDiscount.setText("- " + formatPeso(discount));
        }
        if (downpay > 0) {
            bsDownpaymentRow.setVisibility(View.VISIBLE);
            bsDownpaymentApplied.setText("- " + formatPeso(downpay));
        }
        
        bsFinalTotal.setText(formatPeso(finalTotal));

        view.findViewById(R.id.btnDownloadPdf).setOnClickListener(v -> {
            Toast.makeText(this, "Downloading PDF Receipt...", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ─────────────────────────────────────────────
    //  UI HELPERS
    // ─────────────────────────────────────────────
    private void showLoading(boolean show) {
        billingLoadingLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            rvInvoices.setVisibility(View.GONE);
            billingEmptyLayout.setVisibility(View.GONE);
        }
    }

    private void showEmpty(boolean show) {
        billingEmptyLayout.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String formatPeso(double value) {
        return "₱" + String.format(Locale.getDefault(), "%,.2f", value);
    }

    private String safeStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private double safeDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try { return obj.get(key).getAsDouble(); } catch (Exception e) { return 0; }
    }

    private int safeInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try { return obj.get(key).getAsInt(); } catch (Exception e) { return 0; }
    }

    // ─────────────────────────────────────────────
    //  ADAPTER
    // ─────────────────────────────────────────────
    private class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.VH> {
        private final List<JsonObject> items;
        InvoiceAdapter(List<JsonObject> items) { this.items = items; }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_invoice, parent, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            JsonObject inv = items.get(pos);

            String id       = safeStr(inv, "payment_id");
            String status   = inv.has("synced_status") && !inv.get("synced_status").isJsonNull() 
                              ? safeStr(inv, "synced_status") 
                              : safeStr(inv, "status");
            String service  = safeStr(inv, "service_type");
            if (service.isEmpty()) service = safeStr(inv, "payment_type");
            String date     = safeStr(inv, "payment_date");
            double amount   = safeDouble(inv, "amount");
            String payType  = safeStr(inv, "payment_type");
            String patient  = safeStr(inv, "patient_name");

            h.tvInvoiceId.setText("#PAY-" + String.format("%04d", safeInt(inv, "payment_id")));
            h.tvInvoiceStatus.setText(friendlyStatus(status, "downpayment".equals(payType)));
            h.tvInvoiceService.setText(service.isEmpty() ? "Check-up" : service);
            h.tvInvoiceDate.setText(formatDate(date));
            h.tvInvoiceAmount.setText(formatPeso(amount));
            
            // Generate initials
            String initials = "PT";
            if (!patient.isEmpty()) {
                String[] parts = patient.split(" ");
                if (parts.length >= 2 && !parts[1].isEmpty()) initials = parts[0].substring(0,1) + parts[1].substring(0,1);
                else initials = parts[0].substring(0, Math.min(2, parts[0].length()));
            }
            h.tvInitials.setText(initials.toUpperCase());

            // Visibility
            boolean isDownpay = "downpayment".equals(payType);
            boolean canPay = ("unpaid".equalsIgnoreCase(status)) || (!isDownpay && "pending".equalsIgnoreCase(status));
            boolean hasPaid = "paid".equalsIgnoreCase(status) || "for_verification".equalsIgnoreCase(status) || "applied".equalsIgnoreCase(status) || 
                              "credited".equalsIgnoreCase(status) || "declined".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status) ||
                              (isDownpay && "pending".equalsIgnoreCase(status));

            h.tvInvoiceStatus.setText(friendlyStatus(status, isDownpay));
            int[] colors = statusColors(status, isDownpay);
            h.statusStrip.setBackgroundColor(colors[0]);
            h.tvInvoiceStatus.setTextColor(colors[0]);

            h.btnPayNow.setVisibility(canPay && !isDownpay ? View.VISIBLE : View.GONE);
            h.btnPayDownpayment.setVisibility(canPay && isDownpay ? View.VISIBLE : View.GONE);
            h.btnViewReceipt.setVisibility(hasPaid ? View.VISIBLE : View.GONE);

            int payId = safeInt(inv, "payment_id");
            h.btnPayNow.setOnClickListener(v -> onPayNow(payId, amount, "checkup"));
            h.btnPayDownpayment.setOnClickListener(v -> onPayNow(payId, amount, "downpayment"));
            h.btnViewReceipt.setOnClickListener(v -> showBottomSheetReceipt(inv));

            // Make the status pill clickable as well for better UX
            // Final status string for comparison (trimmed and lowercase)
            final String finalStatus = status != null ? status.trim().toLowerCase() : "";

            // Unified Action for Rejected/Credited status using a BottomSheet
            java.lang.Runnable showWalletCredit = () -> {
                com.google.android.material.bottomsheet.BottomSheetDialog bsd = new com.google.android.material.bottomsheet.BottomSheetDialog(BillingActivity.this, R.style.BottomSheetDialogTheme);
                View v = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_payment, null);
                View parent = (View) v.getParent();
                if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);

                // Hide generic elements
                View paymentDetails = v.findViewById(R.id.paymentDetailsContainer);
                View dashedSep = v.findViewById(R.id.dashedSeparator);
                View qrBox = v.findViewById(R.id.qrContainer);
                
                if (paymentDetails != null) paymentDetails.setVisibility(View.GONE);
                if (dashedSep != null) dashedSep.setVisibility(View.GONE);
                if (qrBox != null) qrBox.setVisibility(View.GONE);

                // Also hide the "Payment Method" selection blocks
                for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
                    View child = ((ViewGroup)v).getChildAt(i);
                    if (child instanceof TextView) {
                        String txt = ((TextView)child).getText().toString().toUpperCase();
                        if (txt.contains("PAYMENT METHOD") || txt.contains("SECURE CHECKOUT") || txt.contains("CANCEL CHECKOUT")) {
                            child.setVisibility(View.GONE);
                        }
                    }
                }
                View methodG = v.findViewById(R.id.methodGcash);
                if (methodG != null && methodG.getParent() instanceof View) {
                    ((View)methodG.getParent()).setVisibility(View.GONE);
                }
                View btnSubmit = v.findViewById(R.id.btnSubmitPayment);
                if (btnSubmit != null) btnSubmit.setVisibility(View.GONE);

                // Show and construct the premium Wallet Message Container
                View walletContainer = v.findViewById(R.id.walletMessageContainer);
                if (walletContainer != null) {
                    walletContainer.setVisibility(View.VISIBLE);
                    TextView tvNotice = v.findViewById(R.id.tvWalletNotice);
                    if (tvNotice != null) {
                        tvNotice.setText("This appointment was rejected. Your ₱" + formatPeso(amount) + " deposit was safely credited to your wallet to be used for your next booking without needing to pay again.");
                    }
                    TextView btnReschedule = v.findViewById(R.id.btnReschedule);
                    if (btnReschedule != null) {
                        btnReschedule.setOnClickListener(v2 -> {
                            bsd.dismiss();
                            android.content.Intent nIntent = new android.content.Intent(BillingActivity.this, BookingActivity.class);
                            startActivity(nIntent);
                        });
                    }
                }

                bsd.setContentView(v);
                bsd.show();
            };

            // Master Click Listener
            android.view.View.OnClickListener masterListener = v -> {
                if (canPay) {
                    onPayNow(payId, amount, isDownpay ? "downpayment" : "checkup");
                } else if (hasPaid) {
                    if ("credited".equals(finalStatus) || "declined".equals(finalStatus) || "rejected".equals(finalStatus)) {
                        showWalletCredit.run();
                    } else {
                        showBottomSheetReceipt(inv);
                    }
                }
            };

            // Force clickable/focusable and attach
            h.itemView.setClickable(true);
            h.itemView.setFocusable(true);
            h.itemView.setOnClickListener(masterListener);
            
            h.tvInvoiceStatus.setClickable(true);
            h.tvInvoiceStatus.setFocusable(true);
            h.tvInvoiceStatus.setOnClickListener(masterListener);
            
            h.tvInvoiceService.setOnClickListener(masterListener);
            h.tvInvoiceDate.setOnClickListener(masterListener);
            h.tvInvoiceAmount.setOnClickListener(masterListener);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            View statusStrip;
            TextView tvInvoiceId, tvInvoiceStatus, tvInvoiceService, tvInvoiceDate, tvInvoiceAmount, tvInitials;
            TextView btnPayNow, btnPayDownpayment, btnViewReceipt;

            VH(View v) {
                super(v);
                statusStrip       = v.findViewById(R.id.statusStrip);
                tvInitials        = v.findViewById(R.id.tvInitials);
                tvInvoiceId       = v.findViewById(R.id.tvInvoiceId);
                tvInvoiceStatus   = v.findViewById(R.id.tvInvoiceStatus);
                tvInvoiceService  = v.findViewById(R.id.tvInvoiceService);
                tvInvoiceDate     = v.findViewById(R.id.tvInvoiceDate);
                tvInvoiceAmount   = v.findViewById(R.id.tvInvoiceAmount);
                btnPayNow         = v.findViewById(R.id.btnPayNow);
                btnPayDownpayment = v.findViewById(R.id.btnPayDownpayment);
                btnViewReceipt    = v.findViewById(R.id.btnViewReceipt);
            }
        }
    }

    private String friendlyStatus(String raw, boolean isDownpay) {
        if (raw == null) return "Unknown";
        switch (raw.toLowerCase()) {
            case "paid":             return "Payment Complete";
            case "for_verification": return "Under Review";
            case "pending":          return isDownpay ? "Under Review" : "Pay Now";
            case "unpaid":           return "Pay Now";
            case "applied":          return "Applied from Wallet";
            case "credited":
            case "declined":         return "Rejected";
            case "cancelled":        return "Cancelled";
            default:                 return raw;
        }
    }

    private int[] statusColors(String status, boolean isDownpay) {
        if (status == null) return new int[]{Color.parseColor("#6B7280"), Color.parseColor("#6B7280")};
        switch (status.toLowerCase()) {
            case "paid":             return new int[]{Color.parseColor("#00F49C"), Color.parseColor("#065F46")};
            case "for_verification": return new int[]{Color.parseColor("#4299F0"), Color.parseColor("#1E3A8A")};
            case "pending":          return isDownpay ? new int[]{Color.parseColor("#4299F0"), Color.parseColor("#1E3A8A")} : new int[]{Color.parseColor("#FFBC00"), Color.parseColor("#78350F")};
            case "unpaid":           return new int[]{Color.parseColor("#F472B6"), Color.parseColor("#BE185D")};
            case "applied":          return new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#8B5CF6")};
            case "credited":
            case "declined":
            case "cancelled":        return new int[]{Color.parseColor("#FF3B30"), Color.parseColor("#7F1D1D")};
            default:                 return new int[]{Color.parseColor("#6B7280"), Color.parseColor("#6B7280")};
        }
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(raw);
            return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d);
        } catch (ParseException e) { return raw; }
    }
}
