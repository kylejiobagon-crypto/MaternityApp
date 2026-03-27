package com.example.alagwaapp;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ConversationDetailActivity extends AppCompatActivity {
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageView btnBackView;
    private TextView tvHeaderName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_view);
        
        initializeViews();
        setupData();
    }

    private void initializeViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnBackView = findViewById(R.id.btnBackView);
        tvHeaderName = findViewById(R.id.tvHeaderName);

        btnBackView.setOnClickListener(v -> finish());
        
        findViewById(R.id.btnSend).setOnClickListener(v -> {
            String msg = etMessage.getText().toString();
            if(!msg.isEmpty()) {
                Toast.makeText(this, "Transmitting to Medical Secure Hub...", Toast.LENGTH_SHORT).show();
                etMessage.setText("");
            }
        });

        // Get extras from Intent
        String contactName = getIntent().getStringExtra("contact_name");
        if (contactName != null) {
            tvHeaderName.setText(contactName);
        }
    }

    private void setupData() {
        // Placeholder for message adapter logic
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        // You would typically set an adapter here for the actual message bubbles
    }
}
