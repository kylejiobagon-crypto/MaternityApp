package com.example.alagwaapp;

import com.google.gson.annotations.SerializedName;

public class Patient {
    @SerializedName("patient_id")
    public int patientId;

    @SerializedName("first_name")
    public String firstName;

    @SerializedName("last_name")
    public String lastName;

    @SerializedName("email")
    public String email;

    @SerializedName("contact_number")
    public String contactNumber;

    @SerializedName("dob")
    public String dob;

    @SerializedName("address")
    public String address;

    @SerializedName("philhealth_number")
    public String philhealthNumber;

    @SerializedName("philhealth_discount_percent")
    public String philhealthDiscount;

    @SerializedName("lmp")
    public String lmp;

    @SerializedName("blood_type")
    public String bloodType;

    @SerializedName("months_pregnant")
    public Double monthsPregnant;

    @SerializedName("emergency_name")
    public String emergencyName;

    @SerializedName("emergency_relationship")
    public String emergencyRelationship;

    @SerializedName("emergency_number")
    public String emergencyNumber;
    
    @SerializedName("status")
    public String status;

    // Metrics
    public int daysPregnant;
    public int weeksPregnant;
    public int remainingDays;
    public String dueDate;
    public String trimester;
    public int visitCount;
}
