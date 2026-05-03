package com.example.mobileapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FriendsFragment extends Fragment {

    private TextView tvRequests;
    private TextView tvFriendsList;
    private EditText etFriendUsername;

    public FriendsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);
        
        tvRequests = view.findViewById(R.id.tv_requests);
        tvFriendsList = view.findViewById(R.id.tv_friends_list);
        etFriendUsername = view.findViewById(R.id.et_friend_username);
        Button btnSendRequest = view.findViewById(R.id.btn_send_request);
        Button btnLogout = view.findViewById(R.id.btn_logout);

        btnLogout.setOnClickListener(v -> {
            SharedPreferences prefs = requireContext().getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
            prefs.edit().remove("current_user").apply();
            
            android.content.Intent intent = new android.content.Intent(getActivity(), LoginActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        
        btnSendRequest.setOnClickListener(v -> {
            String toUser = etFriendUsername.getText().toString().trim();
            if (!toUser.isEmpty()) {
                SharedPreferences prefs = requireContext().getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
                String myUsername = prefs.getString("current_user", "Guest");
                StorageManager.sendFriendRequest(getContext(), myUsername, toUser);
                Toast.makeText(getContext(), "Request sent (locally)!", Toast.LENGTH_SHORT).show();
                etFriendUsername.setText("");
            }
        });
        
        tvRequests.setOnClickListener(v -> {
            SharedPreferences prefs = requireContext().getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
            String myUsername = prefs.getString("current_user", "Guest");
            User me = StorageManager.getUser(getContext(), myUsername);
            if (me != null && !me.getFriendRequests().isEmpty()) {
                String firstRequest = me.getFriendRequests().get(0);
                StorageManager.acceptFriendRequest(getContext(), myUsername, firstRequest);
                Toast.makeText(getContext(), "Accepted " + firstRequest, Toast.LENGTH_SHORT).show();
                refreshData();
            }
        });

        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }
    
    private void refreshData() {
        SharedPreferences prefs = requireContext().getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("current_user", "Guest");
        User me = StorageManager.getUser(getContext(), myUsername);
        
        if (me != null) {
            if (me.getFriendRequests().isEmpty()) {
                tvRequests.setText("No new requests");
            } else {
                tvRequests.setText("Tap to accept: " + TextUtils.join(", ", me.getFriendRequests()));
            }
            
            if (me.getFriends().isEmpty()) {
                tvFriendsList.setText("No friends yet");
            } else {
                tvFriendsList.setText(TextUtils.join(", ", me.getFriends()));
            }
        }
    }
}
