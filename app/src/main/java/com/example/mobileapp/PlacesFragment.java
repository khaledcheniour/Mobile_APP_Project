package com.example.mobileapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlacesFragment extends Fragment {

    private ListView listViewPlaces;
    private List<String> placesList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_places, container, false);
        listViewPlaces = view.findViewById(R.id.list_view_places);

        loadPlaces();

        listViewPlaces.setOnItemClickListener((parent, view1, position, id) -> {
            String selectedPlace = placesList.get(position);
            Intent intent = new Intent(requireContext(), StoryActivity.class);
            intent.putExtra("EXTRA_PLACE", selectedPlace);
            startActivity(intent);
        });

        return view;
    }

    private void loadPlaces() {
        SharedPreferences prefs = requireContext().getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
        String currentUser = prefs.getString("current_user", "Guest");

        List<Memory> allMemories = StorageManager.loadMemories(requireContext());
        Set<String> uniquePlaces = new HashSet<>();

        for (Memory m : allMemories) {
            if (currentUser.equals(m.getAuthor())) {
                String place = m.getPlace();
                if (place != null && !place.trim().isEmpty()) {
                    uniquePlaces.add(place.trim());
                }
            }
        }

        placesList = new ArrayList<>(uniquePlaces);
        
        // Simple adapter with custom item styling if possible, but default is fine for now
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, placesList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                text.setPadding(32, 32, 32, 32);
                text.setTextSize(18);
                return view;
            }
        };

        listViewPlaces.setAdapter(adapter);
    }
}
