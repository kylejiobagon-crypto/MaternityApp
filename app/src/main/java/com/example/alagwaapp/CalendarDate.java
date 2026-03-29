package com.example.alagwaapp;

import java.util.Calendar;

public class CalendarDate {
    public int day;
    public boolean isCurrentMonth;
    public Calendar date;
    public boolean hasDots;
    public boolean hasConfirmed;
    public boolean hasPending;

    public CalendarDate(int day, boolean isCurrentMonth, Calendar date) {
        this.day = day;
        this.isCurrentMonth = isCurrentMonth;
        this.date = date;
        this.hasDots = false;
        this.hasConfirmed = false;
        this.hasPending = false;
    }
}
