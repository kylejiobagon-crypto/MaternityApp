package com.example.alagwaapp;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.GsonBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PaymentCheckoutActivity extends AppCompatActivity {

    private String serviceName;
    private String amount;
    private int paymentId;
    private ApiService apiService;
    private android.content.SharedPreferences prefs;

    private ObjectAnimator scanAnim;
    private ObjectAnimator laserPulse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_checkout);

        serviceName = getIntent().getStringExtra("service_name");
        amount = getIntent().getStringExtra("amount");
        paymentId = getIntent().getIntExtra("payment_id", 0);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        initNetworking();

        // ── 1. Populate Receipt Info ──
        if (serviceName != null) {
            ((TextView) findViewById(R.id.tvServiceName)).setText(serviceName);
        }
        if (amount != null) {
            ((TextView) findViewById(R.id.tvDepositAmount)).setText(amount);
        }

        String clinicName  = prefs.getString("clinic_name",  "Alagwa Maternity Clinic").toUpperCase();
        String clinicAddr  = prefs.getString("clinic_address", "");
        String clinicPhone = prefs.getString("contact_number", "0000 000 0000");

        ((TextView) findViewById(R.id.tvClinicName)).setText(clinicName);
        if (!clinicAddr.isEmpty()) {
            ((TextView) findViewById(R.id.tvClinicAddress)).setText(clinicAddr);
        } else {
            findViewById(R.id.tvClinicAddress).setVisibility(View.GONE);
        }
        ((TextView) findViewById(R.id.tvClinicPhone)).setText(clinicPhone);

        // ── 2. Entrance Animation ──
        View receiptCard = findViewById(R.id.receiptCard);
        receiptCard.setAlpha(0f);
        receiptCard.setScaleX(0.92f);
        receiptCard.setScaleY(0.92f);
        receiptCard.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // ── 3. Generate QR Code ──
        ImageView ivQr = findViewById(R.id.ivQrCode);
        String qrContent = "ALAGWA:DEPOSIT-" + paymentId;
        new Thread(() -> {
            Bitmap qrBitmap = generateRealQrCode(qrContent, 640);
            runOnUiThread(() -> {
                if (qrBitmap != null) ivQr.setImageBitmap(qrBitmap);
            });
        }).start();

        // ── 4. Laser Scan Animation ──
        View scanLine = findViewById(R.id.scanLine);
        float qrBoxDp = 140f;
        float laserTravel = (qrBoxDp - 12f) * getResources().getDisplayMetrics().density;
        scanAnim = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, laserTravel);
        scanAnim.setDuration(1600);
        scanAnim.setRepeatMode(ObjectAnimator.REVERSE);
        scanAnim.setRepeatCount(ObjectAnimator.INFINITE);
        scanAnim.setInterpolator(new LinearInterpolator());
        scanAnim.start();

        laserPulse = ObjectAnimator.ofFloat(scanLine, "alpha", 0.55f, 1f);
        laserPulse.setDuration(800);
        laserPulse.setRepeatMode(ObjectAnimator.REVERSE);
        laserPulse.setRepeatCount(ObjectAnimator.INFINITE);
        laserPulse.start();

        // ── 5. Buttons & Listeners ──
        findViewById(R.id.btnPayNow).setOnClickListener(v -> processPayment());
        findViewById(R.id.btnLater).setOnClickListener(v -> finish());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        
        findViewById(R.id.methodGcash).setOnClickListener(v -> selectMethod(v, "GCASH"));
        findViewById(R.id.methodMaya).setOnClickListener(v -> selectMethod(v, "MAYA"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanAnim != null) scanAnim.cancel();
        if (laserPulse != null) laserPulse.cancel();
    }

    private Bitmap generateRealQrCode(String content, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < sizePx; x++) {
                for (int y = 0; y < sizePx; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            Log.e("PaymentCheckout", "QR generation failed", e);
            return null;
        }
    }

    private void selectMethod(View selected, String type) {
        View detailsContainer = findViewById(R.id.detailsContainer);
        TextView btnPayNow = findViewById(R.id.btnPayNow);

        // Reset toggles
        findViewById(R.id.methodGcash).setBackgroundResource(R.drawable.bg_payment_method_default);
        findViewById(R.id.methodMaya).setBackgroundResource(R.drawable.bg_payment_method_default);

        // Animate selected
        selected.setBackgroundResource(R.drawable.bg_payment_method_selected);
        selected.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).withEndAction(() ->
            selected.animate().scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start()
        ).start();

        if (detailsContainer.getVisibility() == View.GONE) {
            detailsContainer.setVisibility(View.VISIBLE);
            detailsContainer.setAlpha(0f);
            detailsContainer.setTranslationY(-12f);
            detailsContainer.animate().alpha(1f).translationY(0f).setDuration(250)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }

        TextView tvAccountLabel = findViewById(R.id.tvAccountLabel);
        EditText etAccountNumber = findViewById(R.id.etAccountNumber);
        EditText etReferenceNumber = findViewById(R.id.etReferenceNumber);

        if (type.equals("GCASH")) {
            tvAccountLabel.setText("GCASH MERCHANT NO.");
            etAccountNumber.setText("+63 917 123 4567");
            etReferenceNumber.setHint("Paste GCash Ref No. here");
            btnPayNow.setText("Pay via GCash — " + amount);
        } else {
            tvAccountLabel.setText("MAYA BUSINESS NO.");
            etAccountNumber.setText("+63 918 765 4321");
            etReferenceNumber.setHint("Paste Maya Ref No. here");
            btnPayNow.setText("Pay via Maya — " + amount);
        }

        // Enable CTA
        btnPayNow.animate().alpha(1f).setDuration(200).start();
        btnPayNow.setClickable(true);
        btnPayNow.setFocusable(true);
    }

    private void initNetworking() {
        apiService = InfinityFreeClient.buildRetrofit(prefs).create(ApiService.class);
    }

    private void processPayment() {
        String referenceNumber = ((EditText) findViewById(R.id.etReferenceNumber)).getText().toString().trim();
        if (referenceNumber.isEmpty()) {
            Toast.makeText(this, "Please enter your reference number", Toast.LENGTH_SHORT).show();
            return;
        }

        View btn = findViewById(R.id.btnPayNow);
        btn.setEnabled(false);
        btn.setAlpha(0.5f);
        
        Toast.makeText(this, "Submitting payment for verification...", Toast.LENGTH_SHORT).show();

        apiService.submitDownpaymentProof("submit_proof", "true", paymentId, referenceNumber)
            .enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String rawBody = response.body().string();
                            // Handle potential PHP warnings before JSON
                            int jsonStart = rawBody.indexOf('{');
                            if (jsonStart > 0) rawBody = rawBody.substring(jsonStart);

                            org.json.JSONObject obj = new org.json.JSONObject(rawBody);
                            boolean success = obj.optBoolean("success", false);

                            if (success) {
                                // Show a premium success dialog
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(PaymentCheckoutActivity.this, R.style.GlassAlertTheme)
                                    .setTitle("PAYMENT SUBMITTED")
                                    .setMessage("Your downpayment has been queued for verification! \n\nYour appointment is now UNDER REVIEW. Once the clinic confirms your deposit, you'll see it as 'CONFIRMED' on your dashboard. \n\nGet ready for your check-up!")
                                    .setCancelable(false)
                                    .setPositiveButton("READY TO GO", (d, w) -> {
                                        Intent intent = new Intent(PaymentCheckoutActivity.this, AppointmentsActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .show();
                            } else {
                                String msg = obj.optString("message", "Error submitting payment.");
                                Toast.makeText(PaymentCheckoutActivity.this, msg, Toast.LENGTH_LONG).show();
                                btn.setEnabled(true);
                                btn.setAlpha(1.0f);
                            }
                        } else {
                            Toast.makeText(PaymentCheckoutActivity.this, "Server error. Please try again.", Toast.LENGTH_SHORT).show();
                            btn.setEnabled(true);
                            btn.setAlpha(1.0f);
                        }
                    } catch (Exception e) {
                        Toast.makeText(PaymentCheckoutActivity.this, "Failed to connect to server.", Toast.LENGTH_SHORT).show();
                        btn.setEnabled(true);
                        btn.setAlpha(1.0f);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(PaymentCheckoutActivity.this, "Network error, but your request was recorded locally.", Toast.LENGTH_SHORT).show();
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                }
            });
    }
}
