package com.example.mobileapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ListFragment extends Fragment {

    private RecyclerView recyclerView;
    private MemoryAdapter adapter;
    private DrawerLayout drawerLayout;
    private SidebarFriendsAdapter friendsAdapter;

    private String currentUsername;
    private List<Memory> allMemories = new ArrayList<>();
    private Set<String> selectedFriends = new HashSet<>();

    private static final String PREFS    = "MemoryAppPrefs";
    private static final String KEY_USER = "current_user";
    private static final String KEY_SEL  = "sidebar_selected_friends";

    public ListFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view_memories);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Drawer
        drawerLayout = view.findViewById(R.id.drawer_layout);
        FloatingActionButton fabOpen = view.findViewById(R.id.fab_open_sidebar);
        fabOpen.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        
        TextView btnClose = view.findViewById(R.id.btn_close_sidebar);
        btnClose.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));

        // Current user
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        currentUsername = prefs.getString(KEY_USER, "Guest");

        // Load friends and set up sidebar
        RecyclerView friendsRv = view.findViewById(R.id.friends_recycler);
        friendsRv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<String> friends = StorageManager.getFriendsList(requireContext(), currentUsername);
        selectedFriends = restoreStringSet(prefs, KEY_SEL, new HashSet<>(friends));
        selectedFriends.add(currentUsername); // Always show own memories

        friendsAdapter = new SidebarFriendsAdapter(friends, selectedFriends, sel -> {
            selectedFriends = sel;
            selectedFriends.add(currentUsername);
            persistStringSet(KEY_SEL, selectedFriends);
            refreshList();
        });
        friendsRv.setAdapter(friendsAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Refresh memories and friends list on resume
        loadData();
        if (friendsAdapter != null) {
            friendsAdapter.notifyDataSetChanged();
        }
        refreshList();
    }

    private void loadData() {
        allMemories = StorageManager.getFriendsAndMyMemories(requireContext(), currentUsername);
    }

    private void refreshList() {
        List<Memory> filteredMemories = new ArrayList<>();
        for (Memory m : allMemories) {
            String author = m.getAuthor();
            boolean visible = (author == null)
                    || author.equals(currentUsername)
                    || selectedFriends.contains(author);
            if (visible) {
                filteredMemories.add(m);
            }
        }

        if (adapter == null) {
            adapter = new MemoryAdapter(filteredMemories);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.setMemories(filteredMemories);
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────
    private void persistStringSet(String key, Set<String> set) {
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, String.join(",", set)).apply();
    }

    private Set<String> restoreStringSet(SharedPreferences prefs, String key, Set<String> fallback) {
        String raw = prefs.getString(key, null);
        if (raw == null || raw.isEmpty()) return fallback;
        Set<String> result = new HashSet<>();
        for (String s : raw.split(",")) if (!s.isEmpty()) result.add(s);
        return result;
    }
}
