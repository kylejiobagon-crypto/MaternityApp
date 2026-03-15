package com.example.alagwaapp;

public class SummaryResponse {
    public boolean success;
    public Data data;

    public static class Data {
        // Admin Fields
        public double total_revenue;
        public int total_invoices;
        
        // Patient Fields
        public int unpaid_count;
        public double total_unpaid;
        public String next_booking;
        public int records_count;
    }
}
