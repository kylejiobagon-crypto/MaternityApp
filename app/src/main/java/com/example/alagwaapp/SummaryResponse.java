package com.example.alagwaapp;

import com.google.gson.annotations.SerializedName;

public class SummaryResponse {
    public boolean success;
    public Data data;

    public static class Data {
        // These field names exactly match the JSON keys from api_dashboard.php
        public String fullname;
        public int patient_id;

        // Backend returns "appointments_today" for the stat card
        @SerializedName("appointments_today")
        public int today_queue;

        public int upcoming_appointments;

        // Backend returns "pending_bills_count" or may return "unpaid_count"
        @SerializedName("pending_bills_count")
        public int pending_bills;
        public int unpaid_count;

        public double total_unpaid;
        public String next_booking;

        // Backend does not return records_count directly; default 0
        public int records_count;

        public PregnancyStats pregnancy_stats;

        // Resolved getter – use whichever field the backend returns
        public int getPendingBills() {
            return pending_bills > 0 ? pending_bills : unpaid_count;
        }
    }

    public static class PregnancyStats {
        public int weeks;
        public double months;
        public String trimester;
        public String edd;
        public double progress_percent;
    }
}
