package com.example.mobileapp;

import android.content.Context;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment {

    private MapView map;

    public MapFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        
        // Setup OSMDroid configuration
        Context ctx = requireActivity().getApplicationContext();
        Configuration.getInstance().load(ctx, androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx));

        View view = inflater.inflate(R.layout.fragment_map, container, false);
        map = view.findViewById(R.id.map_view);
        map.setMultiTouchControls(true);
        map.getController().setZoom(5.0);
        map.getController().setCenter(new GeoPoint(48.8566, 2.3522)); // Default to Paris

        loadAndClusterMemories();

        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
        loadAndClusterMemories();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
    
    private void loadAndClusterMemories() {
        map.getOverlays().clear();
        List<Memory> memories = StorageManager.loadMemories(getContext());
        if (memories.isEmpty()) return;

        // Simple distance-based clustering algorithm
        List<Cluster> clusters = new ArrayList<>();
        double CLUSTER_THRESHOLD = 0.5; // Roughly 50km threshold

        for (Memory m : memories) {
            boolean addedToCluster = false;
            for (Cluster c : clusters) {
                if (distance(c.centerLat, c.centerLng, m.getLatitude(), m.getLongitude()) < CLUSTER_THRESHOLD) {
                    c.memories.add(m);
                    addedToCluster = true;
                    break;
                }
            }
            if (!addedToCluster) {
                Cluster newCluster = new Cluster();
                newCluster.centerLat = m.getLatitude();
                newCluster.centerLng = m.getLongitude();
                newCluster.memories.add(m);
                clusters.add(newCluster);
            }
        }

        for (Cluster c : clusters) {
            GeoPoint gp = new GeoPoint(c.centerLat, c.centerLng);
            Marker marker = new Marker(map);
            marker.setPosition(gp);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            
            if (c.memories.size() == 1) {
                Memory m = c.memories.get(0);
                marker.setTitle(m.getEmotion());
                marker.setSnippet(m.getNote());
                marker.setIcon(getEmotionIcon(m.getEmotion()));
                marker.setOnMarkerClickListener((mrk, mapView) -> {
                    Toast.makeText(getContext(), m.getEmotion() + ": " + m.getNote(), Toast.LENGTH_SHORT).show();
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
        
        // Center on last memory
        if (!memories.isEmpty()) {
            map.getController().setCenter(new GeoPoint(memories.get(memories.size() - 1).getLatitude(), memories.get(memories.size() - 1).getLongitude()));
        }
        map.invalidate();
    }
    
    private double distance(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lon1 - lon2, 2));
    }
    
    private Drawable getEmotionIcon(String emotion) {
        // Return colored markers based on emotion
        int color = Color.BLUE;
        if (emotion != null) {
            String e = emotion.toLowerCase();
            if (e.contains("happy") || e.contains("joy")) color = Color.YELLOW;
            else if (e.contains("sad")) color = Color.BLUE;
            else if (e.contains("angry")) color = Color.RED;
            else if (e.contains("peaceful")) color = Color.GREEN;
            else if (e.contains("excited")) color = Color.MAGENTA;
        }
        return createSolidMarker(color, 40);
    }

    private Drawable createSolidMarker(int color, int radius) {
        Bitmap bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(radius, radius, radius, paint);
        
        Paint stroke = new Paint();
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3f);
        stroke.setAntiAlias(true);
        canvas.drawCircle(radius, radius, radius, stroke);
        
        return new BitmapDrawable(getResources(), bitmap);
    }
    
    private Drawable createClusterIcon(Cluster cluster) {
        int radius = 50;
        Bitmap bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#80000000")); // semi-transparent black
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(radius, radius, radius, paint);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        
        // Sum emotions
        Map<String, Integer> emotionCounts = new HashMap<>();
        for (Memory m : cluster.memories) {
            emotionCounts.put(m.getEmotion(), emotionCounts.getOrDefault(m.getEmotion(), 0) + 1);
        }
        String dominant = "Mixed";
        int max = 0;
        for (Map.Entry<String, Integer> e : emotionCounts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                dominant = e.getKey();
            }
        }
        
        canvas.drawText(String.valueOf(cluster.memories.size()), radius, radius - 5, textPaint);
        
        textPaint.setTextSize(20f);
        canvas.drawText(dominant, radius, radius + 20, textPaint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private static class Cluster {
        double centerLat;
        double centerLng;
        List<Memory> memories = new ArrayList<>();
    }
}
