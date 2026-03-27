package com.example.alagwaapp;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PatientListResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("data")
    public List<Patient> data;

    @SerializedName("message")
    public String message;
}
