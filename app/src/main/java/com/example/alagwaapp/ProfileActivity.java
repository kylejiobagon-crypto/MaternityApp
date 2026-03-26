package com.example.alagwaapp;

import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Calendar;

public class ProfileActivity extends AppCompatActivity {

    private EditText etFirstName, etLastName, etEmail, etPhone, etDob, etAge, etPhilHealth, etAddress;
    private EditText etLmp, etBlood, etMonths, etEmergencyName, etEmergencyRel, etEmergencyPhone;
    private TextView btnEdit, btnReset;
    private View btnSave, avatarContainer, syncDot;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_view);

        initializeViews();
        setupClickListeners();
        startFloatingAnimation();
        startPulseAnimation();
        lockFields(); // Initially locked
    }

    private void initializeViews() {
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etDob = findViewById(R.id.etDob);
        etAge = findViewById(R.id.etAge);
        etPhilHealth = findViewById(R.id.etPhilHealth);
        etAddress = findViewById(R.id.etAddress);

        etLmp = findViewById(R.id.etLmp);
        etBlood = findViewById(R.id.etBlood);
        etMonths = findViewById(R.id.etMonths);

        etEmergencyName = findViewById(R.id.etEmergencyName);
        etEmergencyRel = findViewById(R.id.etEmergencyRel);
        etEmergencyPhone = findViewById(R.id.etEmergencyPhone);

        btnEdit = findViewById(R.id.btnEdit);
        btnReset = findViewById(R.id.btnReset);
        btnSave = findViewById(R.id.btnSave);
        avatarContainer = findViewById(R.id.avatarContainer);
        syncDot = findViewById(R.id.syncDot);
    }

    private void setupClickListeners() {
        btnEdit.setOnClickListener(v -> toggleEditMode());
        btnSave.setOnClickListener(v -> saveChanges());
        btnReset.setOnClickListener(v -> showResetBottomSheet());

        etDob.setOnClickListener(v -> {
            if (isEditMode) showDatePicker();
        });

        findViewById(R.id.navHome).setOnClickListener(v -> finish());
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            btnEdit.setText("CANCEL");
            btnSave.setVisibility(View.VISIBLE);
            btnSave.setTranslationY(200f);
            btnSave.animate().translationY(0f).setDuration(400).start();
            unlockFields();
        } else {
            btnEdit.setText("EDIT PROFILE");
            btnSave.animate().translationY(200f).setDuration(300).withEndAction(() -> btnSave.setVisibility(View.GONE)).start();
            lockFields();
        }
    }

    private void saveChanges() {
        // Logic for backend update...
        Toast.makeText(this, "Profile Cloud-Synced Successfully", Toast.LENGTH_SHORT).show();
        toggleEditMode();
    }

    private void showResetBottomSheet() {
        BottomSheetDialog resetSheet = new BottomSheetDialog(this);
        resetSheet.setContentView(R.layout.dialog_password_reset_aura);
        
        View btnUpdate = resetSheet.findViewById(R.id.btnUpdatePass);
        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> {
                Toast.makeText(this, "Security Key Updated. All sessions synchronized.", Toast.LENGTH_SHORT).show();
                resetSheet.dismiss();
            });
        }
        resetSheet.show();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String date = (month + 1) + "/" + dayOfMonth + "/" + year;
            etDob.setText(date);
            calculateAge(year, month, dayOfMonth);
        }, 1995, 0, 1).show();
    }

    private void calculateAge(int year, int month, int day) {
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - year;
        if (today.get(Calendar.DAY_OF_YEAR) < day) age--;
        etAge.setText(String.valueOf(age));
    }

    private void lockFields() {
        setAllEnabled(false);
    }

    private void unlockFields() {
        setAllEnabled(true);
    }

    private void setAllEnabled(boolean enabled) {
        EditText[] fields = {etFirstName, etLastName, etEmail, etPhone, etPhilHealth, etAddress, etLmp, etBlood, etMonths, etEmergencyName, etEmergencyRel, etEmergencyPhone};
        for (EditText f : fields) {
            f.setEnabled(enabled);
            f.setFocusableInTouchMode(enabled);
            f.setAlpha(enabled ? 1.0f : 0.85f);
        }
    }

    private void startFloatingAnimation() {
        ObjectAnimator floating = ObjectAnimator.ofFloat(avatarContainer, "translationY", 0f, -20f, 0f);
        floating.setDuration(3000);
        floating.setRepeatCount(ObjectAnimator.INFINITE);
        floating.setInterpolator(new AccelerateDecelerateInterpolator());
        floating.start();
    }

    private void startPulseAnimation() {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(syncDot, "alpha", 1.0f, 0.3f, 1.0f);
        pulse.setDuration(2000);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
    }
}
