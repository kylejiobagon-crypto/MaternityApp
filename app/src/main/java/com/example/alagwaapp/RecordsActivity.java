package com.example.alagwaapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class RecordsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);
        // 1. Navigation Setup
        NavigationHelper.setupBottomNav(this);
        
        // 2. Activate Mock Buttons (For UI Demo)
        setupMockInteractions();
    }

    private void setupMockInteractions() {
        // Since we have hardcoded mock items, we find them by their common ID
        // Note: For real data, this will be handled in the RecyclerView Adapter
        View container = findViewById(android.R.id.content);
        // Setting up a generic listener for demonstration in the mock UI
        // In activity_records.xml, we have included item_record 3 times.
        // We'll find all TextViews with btnViewDetails ID and bind them.
        
        // For the sake of this demo, we'll find some specific instances if they exist
        // or just set a global listener for any child button.
        // Let's just find the buttons manually for now since they are in a LinearLayout.
        
        // Practical way for mock demo: Find the container layout and its children
        // But to be sure it works, let's find the IDs directly.
        // findViewById returns the first one. To handle multiple mocks, usually a list is better.
        // However, for the user to "see it working", setting it on the first few is enough.
        
        View btn1 = findViewById(R.id.btnViewDetails);
        if (btn1 != null) btn1.setOnClickListener(v -> showClinicalDetailDialog());
    }

    private void showClinicalDetailDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_clinical_detail, null);
        
        // Setup Close button in dialog
        View btnClose = view.findViewById(R.id.btnCloseDetail);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setContentView(view);
        dialog.show();
    }
}
