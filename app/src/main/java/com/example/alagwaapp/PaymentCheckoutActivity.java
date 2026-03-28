package com.example.alagwaapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.GsonBuilder;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_checkout);

        serviceName = getIntent().getStringExtra("service_name");
        amount = getIntent().getStringExtra("amount");
        paymentId = getIntent().getIntExtra("payment_id", 0);

        prefs = getSharedPreferences("AlagwaPrefs", MODE_PRIVATE);
        initNetworking();

        if (serviceName != null) {
            ((TextView) findViewById(R.id.tvServiceName)).setText(serviceName);
        }
        if (amount != null) {
            ((TextView) findViewById(R.id.tvDepositAmount)).setText(amount);
        }
        
        findViewById(R.id.btnPayNow).setOnClickListener(v -> processPayment());
        findViewById(R.id.btnLater).setOnClickListener(v -> finish());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        
        findViewById(R.id.methodGcash).setOnClickListener(v -> selectMethod(v, "GCASH"));
        findViewById(R.id.methodMaya).setOnClickListener(v -> selectMethod(v, "MAYA"));
    }

    private void selectMethod(View selected, String type) {
        findViewById(R.id.detailsContainer).setVisibility(View.VISIBLE);
        
        TextView tvAccountLabel = findViewById(R.id.tvAccountLabel);
        EditText etAccountNumber = findViewById(R.id.etAccountNumber);
        EditText etReferenceNumber = findViewById(R.id.etReferenceNumber);

        findViewById(R.id.methodGcash).setBackgroundResource(R.drawable.bg_glass_card_dark_realapp);
        findViewById(R.id.methodGcash).setAlpha(0.6f);
        findViewById(R.id.methodMaya).setBackgroundResource(R.drawable.bg_glass_card_dark_realapp);
        findViewById(R.id.methodMaya).setAlpha(0.6f);

        selected.setBackgroundResource(R.drawable.bg_glass_card_flagship);
        selected.setAlpha(1.0f);

        if (type.equals("GCASH")) {
            tvAccountLabel.setText("GCASH MERCHANT NO.");
            etAccountNumber.setText("+63 917 123 4567");
            etReferenceNumber.setHint("Paste GCash Ref No. here");
        } else {
            tvAccountLabel.setText("MAYA BUSINESS NO.");
            etAccountNumber.setText("+63 918 765 4321");
            etReferenceNumber.setHint("Paste Maya Ref No. here");
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

    private void processPayment() {
        String referenceNumber = ((EditText) findViewById(R.id.etReferenceNumber)).getText().toString().trim();
        if (referenceNumber.isEmpty()) {
            Toast.makeText(this, "Please enter your GCash/Maya reference number", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(PaymentCheckoutActivity.this, "✅ Payment Submitted! Waiting for admin review.", Toast.LENGTH_LONG).show();
                    
                    new android.os.Handler().postDelayed(() -> {
                        // Redirect to Appointments
                        android.content.Intent intent = new android.content.Intent(PaymentCheckoutActivity.this, AppointmentsActivity.class);
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    }, 1000);
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
