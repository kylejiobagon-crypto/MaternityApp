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
    
    // ─── Invoice List ─────────────────────────────
    private RecyclerView rvInvoices;
    private LinearLayout billingLoadingLayout, billingEmptyLayout;
    private TextView tvInvoiceCount;

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

            applyLocalFilters();
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
                applyLocalFilters();
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
        okClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                String cookies = CookieManager.getInstance().getCookie(BASE_URL);
                Request.Builder builder = chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json");
                if (cookies != null) builder.header("Cookie", cookies);

                String token = prefs.getString("token", "");
                if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);

                okhttp3.HttpUrl newUrl = chain.request().url().newBuilder()
                        .addQueryParameter("mobile",    "true")
                        .addQueryParameter("tenant_id", String.valueOf(prefs.getInt("tenantId", 1)))
                        .addQueryParameter("role",      prefs.getString("role", "patient"))
                        .addQueryParameter("user_id",   String.valueOf(prefs.getInt("userId", 0)))
                        .addQueryParameter("email",     prefs.getString("email", ""))
                        .build();
                builder.url(newUrl);
                return chain.proceed(builder.build());
            })
            .build();
    }

    private void fetchBillingSummary() {
        String email = prefs.getString("email", "");
        String url = BASE_URL + "api_billing.php?action=summary&mobile=true&role=patient&email=" + email;

        new Thread(() -> {
            try {
                Request req = new Request.Builder().url(url).build();
                okhttp3.Response resp = okClient.newCall(req).execute();
                String body = resp.body() != null ? resp.body().string() : "";

                JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                if (json.get("success") != null && json.get("success").getAsBoolean()) {
                    JsonObject data = json.getAsJsonObject("data");

                    double revenue    = safeDouble(data, "total_revenue");
                    double pending    = safeDouble(data, "pending_amount");
                    double verif      = safeDouble(data, "verification_amount");
                    double downpay    = safeDouble(data, "downpayment_total");

                    runOnUiThread(() -> {
                        tvTotalRevenue.setText(formatPeso(revenue));
                        tvPendingAmount.setText(formatPeso(pending));
                        tvVerificationAmount.setText(formatPeso(verif));
                        tvDownpaymentTotal.setText(formatPeso(downpay));
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Summary fetch failed: " + e.getMessage());
                runOnUiThread(this::loadFallbackSummary);
            }
        }).start();
    }

    private void loadFallbackSummary() {
        tvTotalRevenue.setText("₱0.00");
        tvPendingAmount.setText("₱0.00");
        tvVerificationAmount.setText("₱0.00");
        tvDownpaymentTotal.setText("₱0.00");
    }

    private void fetchInvoices() {
        showLoading(true);
        String email = prefs.getString("email", "");
        String url = BASE_URL + "api_billing.php?action=list&mobile=true&role=patient&email=" + email;

        new Thread(() -> {
            try {
                Request req = new Request.Builder().url(url).build();
                okhttp3.Response resp = okClient.newCall(req).execute();
                String body = resp.body() != null ? resp.body().string() : "";

                if (body.trim().startsWith("{")) {
                    JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean() && json.has("data")) {
                        JsonArray arr = json.getAsJsonArray("data");
                        loadInvoices(arr);
                        return;
                    }
                }
                if (body.trim().startsWith("[")) {
                    JsonArray arr = new JsonParser().parse(body).getAsJsonArray();
                    loadInvoices(arr);
                    return;
                }
                runOnUiThread(() -> showLoading(false));

            } catch (Exception e) {
                Log.w(TAG, "Invoice fetch failed: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    showEmpty(true);
                });
            }
        }).start();
    }

    private void loadInvoices(JsonArray arr) {
        allInvoices.clear();
        for (JsonElement el : arr) {
            if (el.isJsonObject()) allInvoices.add(el.getAsJsonObject());
        }
        runOnUiThread(() -> {
            showLoading(false);
            applyLocalFilters();
        });
    }

    private void applyLocalFilters() {
        filteredInvoices.clear();
        for (JsonObject inv : allInvoices) {
            String status      = safeStr(inv, "status").toLowerCase();
            String payType     = safeStr(inv, "payment_type").toLowerCase();
            String service     = safeStr(inv, "service_type").toLowerCase();
            String patientName = safeStr(inv, "patient_name").toLowerCase();
            String invId       = safeStr(inv, "payment_id");

            // Filter by "downpayment" which mixes type and status
            if (activeStatus.equals("downpayment")) {
                if (!payType.equals("downpayment")) continue;
            } else if (!activeStatus.equals("all")) {
                if (!status.equals(activeStatus)) continue;
                if (payType.equals("downpayment")) continue; // hide DPs from normal views
            }
            
            // Search filter
            if (!activeSearch.isEmpty() && !patientName.contains(activeSearch) && !invId.contains(activeSearch) && !service.contains(activeSearch)) continue;

            filteredInvoices.add(inv);
        }

        tvInvoiceCount.setText(filteredInvoices.size() + " records");
        showEmpty(filteredInvoices.isEmpty());
        if (!filteredInvoices.isEmpty()) rvInvoices.setVisibility(View.VISIBLE);
        invoiceAdapter.notifyDataSetChanged();
    }

    private void onPayNow(int paymentId, double amount, String type) {
        AlertDialog.Builder dlg = new AlertDialog.Builder(this);
        dlg.setTitle(type.equals("downpayment") ? "Submit Deposit" : "Submit Payment");
        dlg.setMessage("Confirm payment of " + formatPeso(amount) + "?\n\nMethod: Cash / On-Site");
        dlg.setPositiveButton("Confirm", (dialog, which) -> submitPaymentToApi(paymentId, "cash", ""));
        dlg.setNegativeButton("Cancel", null);
        dlg.show();
    }

    private void submitPaymentToApi(int paymentId, String method, String ref) {
        new Thread(() -> {
            try {
                RequestBody form = new FormBody.Builder()
                        .add("action", "submit_payment")
                        .add("payment_id", String.valueOf(paymentId))
                        .add("payment_method", method)
                        .add("reference_number", ref)
                        .add("mobile", "true")
                        .build();

                Request req = new Request.Builder()
                        .url(BASE_URL + "api_billing.php")
                        .post(form)
                        .build();
                okhttp3.Response resp = okClient.newCall(req).execute();
                String body = resp.body() != null ? resp.body().string() : "";
                JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                boolean ok = json.has("success") && json.get("success").getAsBoolean();

                runOnUiThread(() -> {
                    if (ok) {
                        Toast.makeText(this, "✅ Submitted for review!", Toast.LENGTH_LONG).show();
                        fetchBillingSummary();
                        fetchInvoices();
                    } else {
                        String msg = json.has("message") ? json.get("message").getAsString() : "Unknown error";
                        Toast.makeText(this, "❌ " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    //  BOTTOM SHEET RECEIPT
    // ─────────────────────────────────────────────
    private void showBottomSheetReceipt(JsonObject inv) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_receipt, null);
        dialog.setContentView(view);
        
        // Ensure background is transparent so custom drawable shows
        View parent = (View) view.getParent();
        if (parent != null) parent.setBackgroundColor(Color.TRANSPARENT);

        String id       = safeStr(inv, "payment_id");
        double amount   = safeDouble(inv, "amount");
        double discount = safeDouble(inv, "discount_amount");
        double downpay  = safeDouble(inv, "downpayment_applied");
        double total    = Math.max(0, amount - discount - downpay);
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
        bsTotalAmount.setText(formatPeso(total));
        bsSubtotal.setText(formatPeso(amount));
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
        
        bsFinalTotal.setText(formatPeso(total));

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
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            View statusStrip;
            TextView tvInvoiceId, tvInvoiceStatus, tvInvoiceService, tvInvoiceDate, tvInvoiceAmount, tvInitials;
            TextView btnPayNow, btnPayDownpayment;
            LinearLayout btnViewReceipt;

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
            case "pending":          return "Awaiting Payment";
            case "unpaid":           return "Unpaid";
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
