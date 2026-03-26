package com.example.alagwaapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class NavigationHelper {

    public static void setupBottomNav(final Activity activity) {
        View navHome = activity.findViewById(R.id.navHome);
        View navAppointments = activity.findViewById(R.id.navAppointments);
        View navRecords = activity.findViewById(R.id.navRecords);
        View navBilling = activity.findViewById(R.id.navBilling);
        View navProfile = activity.findViewById(R.id.navProfile);
        View navRatings = activity.findViewById(R.id.navRatings);

        if (navHome != null) navHome.setOnClickListener(v -> {
            if (!(activity instanceof MainActivity)) {
                Intent intent = new Intent(activity, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        if (navAppointments != null) navAppointments.setOnClickListener(v -> {
            if (!(activity instanceof AppointmentsActivity)) {
                Intent intent = new Intent(activity, AppointmentsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        if (navRecords != null) navRecords.setOnClickListener(v -> {
            if (!(activity instanceof RecordsActivity)) {
                Intent intent = new Intent(activity, RecordsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        if (navBilling != null) navBilling.setOnClickListener(v -> {
            if (!(activity instanceof BillingActivity)) {
                Intent intent = new Intent(activity, BillingActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        if (navProfile != null) navProfile.setOnClickListener(v -> {
            if (!(activity instanceof ProfileActivity)) {
                Intent intent = new Intent(activity, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        if (navRatings != null) navRatings.setOnClickListener(v -> {
            if (!(activity instanceof RatingsActivity)) {
                Intent intent = new Intent(activity, RatingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                activity.overridePendingTransition(0, 0);
            }
        });

        // Use colors from our theme palette
        int activeColor = activity.getResources().getColor(R.color.neo_cyan);
        int inactiveColor = activity.getResources().getColor(R.color.neo_slate);

        if (activity instanceof MainActivity) setActive(activity, R.id.ivNavHome, R.id.tvNavHome, activeColor);
        else if (activity instanceof AppointmentsActivity) setActive(activity, R.id.ivNavAppointments, R.id.tvNavAppointments, activeColor);
        else if (activity instanceof RecordsActivity) setActive(activity, R.id.ivNavRecords, R.id.tvNavRecords, activeColor);
        else if (activity instanceof BillingActivity) setActive(activity, R.id.ivNavBilling, R.id.tvNavBilling, activeColor);
        else if (activity instanceof ProfileActivity) setActive(activity, R.id.ivNavProfile, R.id.tvNavProfile, activeColor);
        else if (activity instanceof RatingsActivity) setActive(activity, R.id.ivNavRatings, R.id.tvNavRatings, activeColor);
    }

    private static void setActive(Activity activity, int imgId, int txtId, int color) {
        ImageView iv = activity.findViewById(imgId);
        TextView tv = activity.findViewById(txtId);
        if (iv != null) {
            iv.setColorFilter(color);
            iv.setScaleX(1.1f);
            iv.setScaleY(1.1f);
        }
        if (tv != null) {
            tv.setTextColor(color);
            tv.setAlpha(1.0f);
            tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
    }
}
