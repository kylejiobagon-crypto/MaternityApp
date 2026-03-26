package com.example.alagwaapp;

public class SummaryResponse {
    public boolean success;
    public Data data;

    public static class Data {
        public String fullname;
        public int patient_id;
        public int today_queue;
        public int upcoming_appointments;
        public int unpaid_count;
        public int pending_bills;       // alias – backend may return either name
        public double total_unpaid;
        public String next_booking;
        public int records_count;
        public PregnancyStats pregnancy_stats;

        // Resolved getter – use whichever field the backend returns
        public int getPendingBills() {
            return pending_bills > 0 ? pending_bills : unpaid_count;
        }
    }

    public static class PregnancyStats {
        public int weeks_pregnant;
        public double months;
        public String trimester_status;
        public String edd;
        public double progress_percent;
    }

}
