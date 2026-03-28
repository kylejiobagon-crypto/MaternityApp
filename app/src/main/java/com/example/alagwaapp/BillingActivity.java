package com.example.alagwaapp;

import android.app.AlertDialog;
import android.content.SharedPreferences;
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
        apiService.getBillingRaw("summary", "true").enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
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
                        double downpay = safeDouble(data, "downpayment_total");

                        runOnUiThread(() -> {
                            tvTotalRevenue.setText(formatPeso(revenue));
                            tvPendingAmount.setText(formatPeso(pending));
                            tvVerificationAmount.setText(formatPeso(verif));
                            tvDownpaymentTotal.setText(formatPeso(downpay));
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
        apiService.getBillingRaw("list", "true").enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
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

    private String selectedMethod = "gcash";

    private void onPayNow(int paymentId, double amount, String type) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_payment, null);
        dialog.setContentView(view);

        // Ensure background is transparent
        View parent = (View) view.getParent();
        if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);

        TextView bsPayAmount = view.findViewById(R.id.bsPayAmount);
        TextView bsPayService = view.findViewById(R.id.bsPayService);
        EditText etRefNo = view.findViewById(R.id.etRefNo);
        View btnSubmit = view.findViewById(R.id.btnSubmitPayment);
        View methodGcash = view.findViewById(R.id.methodGcash);
        View methodMaya = view.findViewById(R.id.methodMaya);
        View methodCash = view.findViewById(R.id.methodCash);
        View refContainer = view.findViewById(R.id.refContainer);

        bsPayAmount.setText(formatPeso(amount));
        bsPayService.setText(type.equals("downpayment") ? "Downpayment / Deposit" : "Service Payment");

        selectedMethod = "gcash"; // default

        View.OnClickListener methodListener = v -> {
            methodGcash.setBackgroundResource(R.drawable.bg_glass_card_dark_realapp);
            methodMaya.setBackgroundResource(R.drawable.bg_glass_card_dark_realapp);
            methodCash.setBackgroundResource(R.drawable.bg_glass_card_dark_realapp);
            ((TextView)((ViewGroup)methodGcash).getChildAt(0)).setTextColor(Color.parseColor("#6B7280"));
            ((TextView)((ViewGroup)methodMaya).getChildAt(0)).setTextColor(Color.parseColor("#6B7280"));
            ((TextView)((ViewGroup)methodCash).getChildAt(0)).setTextColor(Color.parseColor("#6B7280"));

            if (v.getId() == R.id.methodGcash) {
                selectedMethod = "gcash";
                v.setBackgroundResource(R.drawable.bg_glass_card_flagship);
                ((TextView)((ViewGroup)v).getChildAt(0)).setTextColor(Color.parseColor("#0B0E11"));
                refContainer.setVisibility(View.VISIBLE);
            } else if (v.getId() == R.id.methodMaya) {
                selectedMethod = "maya";
                v.setBackgroundResource(R.drawable.bg_glass_card_flagship);
                ((TextView)((ViewGroup)v).getChildAt(0)).setTextColor(Color.parseColor("#0B0E11"));
                refContainer.setVisibility(View.VISIBLE);
            } else {
                selectedMethod = "cash";
                v.setBackgroundResource(R.drawable.bg_glass_card_flagship);
                ((TextView)((ViewGroup)v).getChildAt(0)).setTextColor(Color.parseColor("#0B0E11"));
                refContainer.setVisibility(View.GONE);
            }
        };

        methodGcash.setOnClickListener(methodListener);
        methodMaya.setOnClickListener(methodListener);
        methodCash.setOnClickListener(methodListener);

        btnSubmit.setOnClickListener(v -> {
            String ref = etRefNo.getText().toString().trim();
            if (!selectedMethod.equals("cash") && ref.isEmpty()) {
                Toast.makeText(this, "Please enter Reference Number", Toast.LENGTH_SHORT).show();
                return;
            }
            submitPaymentToApi(paymentId, selectedMethod, ref);
            dialog.dismiss();
        });

        dialog.show();
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
        
        LinearLayout bsDiscountRow = view.findViewById(R.id.bsDiscountRow);
        LinearLayout bsDownpaymentRow = view.findViewById(R.id.bsDownpaymentRow);

        bsInvoiceId.setText("#PAY-" + String.format("%04d", safeInt(inv, "payment_id")));
        bsTotalAmount.setText(formatPeso(finalTotal));
        bsSubtotal.setText(formatPeso(subtotal));
        bsStatus.setText(friendlyStatus(status));
        bsStatus.setTextColor(statusColors(status)[0]);
        
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
            String status   = safeStr(inv, "status");
            String service  = safeStr(inv, "service_type");
            if (service.isEmpty()) service = safeStr(inv, "payment_type");
            String date     = safeStr(inv, "payment_date");
            double amount   = safeDouble(inv, "amount");
            String payType  = safeStr(inv, "payment_type");
            String patient  = safeStr(inv, "patient_name");

            h.tvInvoiceId.setText("#PAY-" + String.format("%04d", safeInt(inv, "payment_id")));
            h.tvInvoiceStatus.setText(friendlyStatus(status));
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

            // Colors
            int[] colors = statusColors(status);
            h.statusStrip.setBackgroundColor(colors[0]);
            h.tvInvoiceStatus.setTextColor(colors[0]);

            // Visibility
            boolean isDownpay = "downpayment".equals(payType);
            boolean canPay = "pending".equalsIgnoreCase(status) || "unpaid".equalsIgnoreCase(status);
            boolean hasPaid = "paid".equalsIgnoreCase(status) || "for_verification".equalsIgnoreCase(status) || "applied".equalsIgnoreCase(status);

            h.btnPayNow.setVisibility(canPay && !isDownpay ? View.VISIBLE : View.GONE);
            h.btnPayDownpayment.setVisibility(canPay && isDownpay ? View.VISIBLE : View.GONE);
            h.btnViewReceipt.setVisibility(hasPaid ? View.VISIBLE : View.GONE);

            int payId = safeInt(inv, "payment_id");
            h.btnPayNow.setOnClickListener(v -> onPayNow(payId, amount, "checkup"));
            h.btnPayDownpayment.setOnClickListener(v -> onPayNow(payId, amount, "downpayment"));
            h.btnViewReceipt.setOnClickListener(v -> showBottomSheetReceipt(inv));

            // Make the status pill clickable as well for better UX
            h.tvInvoiceStatus.setOnClickListener(v -> {
                if (canPay) {
                    onPayNow(payId, amount, isDownpay ? "downpayment" : "checkup");
                } else if (hasPaid) {
                    showBottomSheetReceipt(inv);
                }
            });
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

    private String friendlyStatus(String raw) {
        if (raw == null) return "Unknown";
        switch (raw.toLowerCase()) {
            case "paid":             return "Payment Complete";
            case "for_verification": return "Under Review";
            case "pending":          return "Pay Now";
            case "unpaid":           return "Pay Now";
            case "applied":          return "Applied to Bill";
            default:                 return raw;
        }
    }

    private int[] statusColors(String status) {
        if (status == null) return new int[]{Color.parseColor("#6B7280"), Color.parseColor("#6B7280")};
        switch (status.toLowerCase()) {
            case "paid":             return new int[]{Color.parseColor("#00F49C"), Color.parseColor("#065F46")};
            case "for_verification": return new int[]{Color.parseColor("#4299F0"), Color.parseColor("#1E3A8A")};
            case "pending":          return new int[]{Color.parseColor("#FFBC00"), Color.parseColor("#78350F")};
            case "unpaid":           return new int[]{Color.parseColor("#F472B6"), Color.parseColor("#BE185D")};
            case "applied":          return new int[]{Color.parseColor("#8B5CF6"), Color.parseColor("#4C1D95")};
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
