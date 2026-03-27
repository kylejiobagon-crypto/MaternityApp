package com.example.alagwaapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatContact> contactList;
    private Context context;

    public ChatAdapter(List<ChatContact> contactList, Context context) {
        this.contactList = contactList;
        this.context = context;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_contact, parent, false);
        return new ChatViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatContact contact = contactList.get(position);
        holder.tvContactName.setText(contact.name);
        holder.tvLastMessage.setText(contact.lastMessage);
        holder.tvMsgTime.setText(contact.time);

        if (contact.unread > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(String.valueOf(contact.unread));
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
        }

        // Live Status Pulse Logic
        if ("online".equalsIgnoreCase(contact.status)) {
            holder.statusPulse.setBackgroundResource(R.drawable.bg_dot_emerald_glow);
            Animation pulse = AnimationUtils.loadAnimation(context, R.anim.anim_pulse_soft);
            holder.statusPulse.startAnimation(pulse);
        } else {
            holder.statusPulse.setBackgroundResource(R.drawable.bg_dot_3d);
            holder.statusPulse.clearAnimation();
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ConversationDetailActivity.class);
            intent.putExtra("contact_id", contact.id);
            intent.putExtra("contact_name", contact.name);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return contactList != null ? contactList.size() : 0;
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvContactName, tvLastMessage, tvMsgTime, tvUnreadCount;
        View statusPulse;
        ImageView ivContactAvatar;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContactName = itemView.findViewById(R.id.tvContactName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvMsgTime = itemView.findViewById(R.id.tvMsgTime);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            statusPulse = itemView.findViewById(R.id.statusPulse);
            ivContactAvatar = itemView.findViewById(R.id.ivContactAvatar);
        }
    }
}
