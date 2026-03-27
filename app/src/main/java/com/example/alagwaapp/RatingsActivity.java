package com.example.alagwaapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class RatingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ratings);
        NavigationHelper.setupBottomNav(this);

        // 1. Back Navigation (For the AppBar if available, or just standard finish)
        // Note: For custom 3D headers, we'll check common back icons
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // 2. Activate 'Rate Now' Mock Interaction with Micro-Animations
        View btnRate = findViewById(R.id.btnRateNow);
        if (btnRate != null) {
            btnRate.setOnClickListener(v -> {
                // Premium Scale Animation for 3D Feel
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    showRatingDialog();
                }).start();
            });
        }
    }

    private void showRatingDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_rate_appointment, null);

        // Setup Submit button in dialog
        View btnSubmit = view.findViewById(R.id.btnSubmitRating);
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> {
                Toast.makeText(this, "Thank you for the feedback, Mommy! 🌟", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        dialog.setContentView(view);
        dialog.show();
    }
}
