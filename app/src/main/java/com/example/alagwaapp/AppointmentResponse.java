package com.example.alagwaapp;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AppointmentResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public List<Appointment> data;

    @SerializedName("message")
    public String message;

    public static class Appointment {
        @SerializedName("booking_id")
        public int bookingId;

        @SerializedName("patient_id")
        public int patientId;

        @SerializedName("booking_date")
        public String date;

        @SerializedName("booking_time")
        public String time;

        @SerializedName("service_type")
        public String serviceType;

        @SerializedName("status")
        public String status;

        @SerializedName("downpayment_status")
        public String downpaymentStatus;

        @SerializedName("downpayment_id")
        public Integer downpaymentId;

        @SerializedName("notes")
        public String notes;

        @SerializedName("patient_name")
        public String patientName;

        @SerializedName("email")
        public String email;

        @SerializedName("first_name")
        public String firstName;

        @SerializedName("last_name")
        public String lastName;

        public String getFullName() {
            if (patientName != null && !patientName.isEmpty()) return patientName;
            if (firstName != null && lastName != null) return firstName + " " + lastName;
            if (firstName != null) return firstName;
            return "Unknown Patient";
        }

        @SerializedName("next_visit_date")
        public String nextVisitDate;

        @SerializedName("checkup_notes")
        public String checkupNotes;
    }
}
