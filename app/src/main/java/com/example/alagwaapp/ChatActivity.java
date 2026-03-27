package com.example.alagwaapp;

import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView rvChatContacts;
    private ChatAdapter chatAdapter;
    private List<ChatContact> chatContactList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefresh;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        
        apiService = RetrofitClient.getClient().create(ApiService.class);
        fetchChatList();
    }

    private void initializeViews() {
        rvChatContacts = findViewById(R.id.rvChatContacts);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        
        swipeRefresh.setOnRefreshListener(this::fetchChatList);
        swipeRefresh.setColorSchemeResources(R.color.primary_hub, R.color.accent_neon);

        ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(chatContactList, this);
        rvChatContacts.setLayoutManager(new LinearLayoutManager(this));
        rvChatContacts.setAdapter(chatAdapter);
    }

    private void setupClickListeners() {
        // Sync button click (index 2 in RelativeLayout)
        android.view.ViewGroup header = findViewById(R.id.chat_header);
        if (header != null && header.getChildCount() > 2) {
            header.getChildAt(2).setOnClickListener(v -> fetchChatList());
        }
    }

    private void fetchChatList() {
        swipeRefresh.setRefreshing(true);
        
        // Mocking user_id = 1 and tenant_id = 1 for now
        apiService.fetchChats("list", 1, 1).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    chatContactList.clear();
                    chatContactList.addAll(response.body().data);
                    chatAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(ChatActivity.this, "Communication Sync Failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(ChatActivity.this, "Network Aura Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
