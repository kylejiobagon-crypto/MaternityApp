package com.example.alagwaapp;

import com.google.gson.annotations.SerializedName;

public class ChatContact {
    public int id;
    public String name;
    @SerializedName("last_message")
    public String lastMessage;
    public String time;
    public int unread;
    public String status; // online, offline
    public String avatar;
}
