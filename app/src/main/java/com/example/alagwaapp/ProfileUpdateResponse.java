package com.example.alagwaapp;

import com.google.gson.annotations.SerializedName;

public class ProfileUpdateResponse {
    @SerializedName("success")
    public boolean success;

    @SerializedName("message")
    public String message;
}
