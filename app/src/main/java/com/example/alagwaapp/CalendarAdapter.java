package com.example.alagwaapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder> {

    public interface OnDateSelectedListener {
        void onDateSelected(Calendar date);
    }

    private List<CalendarDate> days;
    private OnDateSelectedListener listener;
    private Calendar selectedDate;

    public CalendarAdapter(List<CalendarDate> days, OnDateSelectedListener listener) {
        this.days = days;
        this.listener = listener;
    }

    public List<CalendarDate> getDates() {
        return days;
    }

    public void updateDates(List<CalendarDate> dates, Calendar selectedDate) {
        this.days = dates;
        this.selectedDate = selectedDate;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_date, parent, false);
        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        CalendarDate date = days.get(position);
        holder.tvDate.setText(String.valueOf(date.day));

        boolean isSelected = false;
        if (selectedDate != null) {
            isSelected = date.date.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                         date.date.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                         date.date.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH);
        }

        if (isSelected) {
            holder.bgSelected.setVisibility(View.VISIBLE);
            holder.tvDate.setTextColor(Color.WHITE);
        } else if (date.isCurrentMonth) {
            holder.bgSelected.setVisibility(View.GONE);
            holder.tvDate.setTextColor(holder.itemView.getContext().getColor(R.color.neo_primary));
        } else {
            holder.bgSelected.setVisibility(View.GONE);
            holder.tvDate.setTextColor(Color.parseColor("#401A5276")); // faded
        }

        holder.itemView.setOnClickListener(v -> {
            if (date.isCurrentMonth) {
                listener.onDateSelected(date.date);
            }
        });
        
        // Mock dots or status tracking could be set here
        holder.layoutDots.setVisibility(date.hasDots ? View.VISIBLE : View.GONE);
        holder.dotConfirmed.setVisibility(View.VISIBLE);
        holder.dotPending.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class CalendarViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        View bgSelected;
        View layoutDots;
        View dotConfirmed;
        View dotPending;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDateNumber);
            bgSelected = itemView.findViewById(R.id.viewSelectedBackground);
            layoutDots = itemView.findViewById(R.id.layoutDots);
            dotConfirmed = itemView.findViewById(R.id.dotConfirmed);
            dotPending = itemView.findViewById(R.id.dotPending);
        }
    }
}
