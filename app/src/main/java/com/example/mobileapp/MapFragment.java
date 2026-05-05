package com.example.mobileapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapFragment extends Fragment {

    private MapView map;
    private DrawerLayout drawerLayout;
    private SidebarFriendsAdapter friendsAdapter;

    private String currentUsername;
    private List<Memory> allMemories = new ArrayList<>();
    private Set<String> selectedFriends = new HashSet<>();

    private static final String PREFS    = "MemoryAppPrefs";
    private static final String KEY_USER = "current_user";
    private static final String KEY_SEL  = "sidebar_selected_friends";

    public MapFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(
                ctx, androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx));

        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // Map
        map = view.findViewById(R.id.map_view);
        map.setMultiTouchControls(true);
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(48.8566, 2.3522));

        // Drawer
        drawerLayout = view.findViewById(R.id.drawer_layout);
        FloatingActionButton fabOpen = view.findViewById(R.id.fab_open_sidebar);
        fabOpen.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        TextView btnClose = view.findViewById(R.id.btn_close_sidebar);
        btnClose.setOnClickListener(v -> drawerLayout.closeDrawer(GravityCompat.START));

        // Current user
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        currentUsername = prefs.getString(KEY_USER, "Guest");

        // Load data
        loadData();

        // Friends RecyclerView
        RecyclerView friendsRv = view.findViewById(R.id.friends_recycler);
        friendsRv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<String> friends = StorageManager.getFriendsList(requireContext(), currentUsername);
        selectedFriends = restoreStringSet(prefs, KEY_SEL, new HashSet<>(friends));
        selectedFriends.add(currentUsername);

        friendsAdapter = new SidebarFriendsAdapter(friends, selectedFriends, sel -> {
            selectedFriends = sel;
            selectedFriends.add(currentUsername);
            persistStringSet(KEY_SEL, selectedFriends);
            refreshMap();
        });
        friendsRv.setAdapter(friendsAdapter);

        refreshMap();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
        loadData();
        if (friendsAdapter != null) friendsAdapter.notifyDataSetChanged();
        refreshMap();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }

    private void loadData() {
        allMemories = StorageManager.getFriendsAndMyMemories(requireContext(), currentUsername);
    }

    /**
     * Only show memories whose author is the current user or a checked friend.
     */
    private void refreshMap() {
        map.getOverlays().clear();

        List<Memory> filtered = new ArrayList<>();
        for (Memory m : allMemories) {
            String author = m.getAuthor();
            boolean visible = (author == null)
                    || author.equals(currentUsername)
                    || selectedFriends.contains(author);
            if (visible) filtered.add(m);
        }

        if (filtered.isEmpty()) { map.invalidate(); return; }

        // Cluster
        List<Cluster> clusters = new ArrayList<>();
        double THRESHOLD = 0.5;
        for (Memory m : filtered) {
            boolean added = false;
            for (Cluster c : clusters) {
                if (distance(c.centerLat, c.centerLng, m.getLatitude(), m.getLongitude()) < THRESHOLD) {
                    c.memories.add(m);
                    added = true;
                    break;
                }
            }
            if (!added) {
                Cluster nc = new Cluster();
                nc.centerLat = m.getLatitude();
                nc.centerLng = m.getLongitude();
                nc.memories.add(m);
                clusters.add(nc);
            }
        }

        for (Cluster c : clusters) {
            GeoPoint gp = new GeoPoint(c.centerLat, c.centerLng);
            Marker marker = new Marker(map);
            marker.setPosition(gp);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            if (c.memories.size() == 1) {
                Memory m = c.memories.get(0);
                boolean isFriend = m.getAuthor() != null && !m.getAuthor().equals(currentUsername);
                marker.setTitle(m.getEmotion());
                marker.setSnippet(m.getNote());
                marker.setIcon(getEmotionIcon(m.getEmotion(), isFriend));
                marker.setOnMarkerClickListener((mrk, mapView) -> {
                    String tag = isFriend ? " (by " + m.getAuthor() + ")" : "";
                    Toast.makeText(getContext(), m.getEmotion() + tag + ": " + m.getNote(), Toast.LENGTH_SHORT).show();
                    mrk.showInfoWindow();
                    return true;
                });
            } else {
                marker.setTitle(c.memories.size() + " Memories");
                marker.setIcon(createClusterIcon(c));
                marker.setOnMarkerClickListener((mrk, mapView) -> {
                    Toast.makeText(getContext(), "Cluster of " + c.memories.size() + " memories", Toast.LENGTH_SHORT).show();
                    return true;
                });
            }
            map.getOverlays().add(marker);
        }

        Memory last = filtered.get(filtered.size() - 1);
        map.getController().setCenter(new GeoPoint(last.getLatitude(), last.getLongitude()));
        map.invalidate();
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

    // ── Drawing helpers ────────────────────────────────────────────────────
    private double distance(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lon1 - lon2, 2));
    }

    private Drawable getEmotionIcon(String emotion, boolean isFriend) {
        int color = Color.parseColor("#5C6BC0");
        if (emotion != null) {
            String e = emotion.toLowerCase();
            if (e.contains("happy") || e.contains("joy"))   color = Color.parseColor("#FDD835");
            else if (e.contains("sad"))                     color = Color.parseColor("#42A5F5");
            else if (e.contains("angry"))                   color = Color.parseColor("#EF5350");
            else if (e.contains("peaceful"))                color = Color.parseColor("#66BB6A");
            else if (e.contains("excited"))                 color = Color.parseColor("#AB47BC");
            else if (e.contains("nostalgic"))               color = Color.parseColor("#FF7043");
            else if (e.contains("love"))                    color = Color.parseColor("#EC407A");
        }
        return isFriend ? createFriendMarker(color, 40) : createSolidMarker(color, 40);
    }

    private Drawable createSolidMarker(int color, int radius) {
        Bitmap bmp = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color); fill.setStyle(Paint.Style.FILL);
        cv.drawCircle(radius, radius, radius, fill);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.WHITE); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(3f);
        cv.drawCircle(radius, radius, radius - 2, stroke);
        return new BitmapDrawable(getResources(), bmp);
    }

    private Drawable createFriendMarker(int color, int radius) {
        Bitmap bmp = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color); fill.setStyle(Paint.Style.FILL);
        cv.drawCircle(radius, radius, radius, fill);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(Color.parseColor("#7C83FD")); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(10f);
        cv.drawCircle(radius, radius, radius - 5, ring);
        return new BitmapDrawable(getResources(), bmp);
    }

    private Drawable createClusterIcon(Cluster cluster) {
        int radius = 50;
        Bitmap bmp = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.parseColor("#CC3F51B5")); bg.setStyle(Paint.Style.FILL);
        cv.drawCircle(radius, radius, radius, bg);

        Map<String, Integer> counts = new HashMap<>();
        for (Memory m : cluster.memories)
            counts.put(m.getEmotion(), counts.getOrDefault(m.getEmotion(), 0) + 1);
        String dominant = "Mixed"; int max = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet())
            if (e.getValue() > max) { max = e.getValue(); dominant = e.getKey(); }

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.WHITE); txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(40f);
        cv.drawText(String.valueOf(cluster.memories.size()), radius, radius + 14, txt);
        txt.setTextSize(18f);
        cv.drawText(dominant != null ? dominant : "Mixed", radius, radius + 34, txt);
        return new BitmapDrawable(getResources(), bmp);
    }

    private static class Cluster {
        double centerLat, centerLng;
        List<Memory> memories = new ArrayList<>();
    }
}
