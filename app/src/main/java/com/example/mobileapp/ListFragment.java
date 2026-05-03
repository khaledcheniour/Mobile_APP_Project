package com.example.mobileapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ListFragment extends Fragment {

    private RecyclerView recyclerView;
    private MemoryAdapter adapter;

    public ListFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);
        recyclerView = view.findViewById(R.id.recycler_view_memories);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("MemoryAppPrefs", android.content.Context.MODE_PRIVATE);
        String username = prefs.getString("current_user", "Guest");
        
        java.util.List<Memory> memoriesToDisplay = StorageManager.getFriendsAndMyMemories(getContext(), username);
        
        if (adapter == null) {
            adapter = new MemoryAdapter(memoriesToDisplay);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.setMemories(memoriesToDisplay);
        }
    }
}
