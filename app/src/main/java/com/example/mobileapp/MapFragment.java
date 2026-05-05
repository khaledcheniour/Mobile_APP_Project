package com.example.mobileapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
    /** Cached avatar bitmap for the current user's own memory markers. */
    private Bitmap userAvatarBitmap = null;
    private String cachedUsername   = null;
    /** Cache: author username → their avatar bitmap (avoids re-decoding Base64 per frame). */
    private final Map<String, Bitmap> friendAvatarCache = new HashMap<>();

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
        map.getController().setCenter(new GeoPoint(48.8566, 2.3522));

        loadAndClusterMemories();

        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
        friendAvatarCache.clear(); // invalidate so avatar changes are picked up
        loadAndClusterMemories();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
    
    private void loadAndClusterMemories() {
        map.getOverlays().clear();

        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences("MemoryAppPrefs", android.content.Context.MODE_PRIVATE);
        String username = prefs.getString("current_user", "Guest");

        // Load / refresh avatar bitmap if the logged-in user changed
        if (!username.equals(cachedUsername)) {
            cachedUsername   = username;
            userAvatarBitmap = null;
            if (!username.equals("Guest")) {
                User me = StorageManager.findUserByUsername(getContext(), username);
                if (me != null && me.getAvatar() != null && me.getAvatar().value != null) {
                    userAvatarBitmap = AvatarUtils.decodeAvatar(me.getAvatar().value);
                } else {
                    String b64 = AvatarUtils.generateInitialsAvatar(username);
                    userAvatarBitmap = AvatarUtils.decodeAvatar(b64);
                }
            }
        }

        List<Memory> memories = StorageManager.getFriendsAndMyMemories(getContext(), username);
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
                boolean isFriend = m.getAuthor() != null && !m.getAuthor().equals(username);
                marker.setTitle(m.getEmotion());
                marker.setSnippet(m.getNote());

                // Own memory  → logged-in user's avatar
                // Friend memory→ that friend's avatar (initials fallback)
                if (!isFriend && userAvatarBitmap != null) {
                    marker.setIcon(createAvatarMarker(userAvatarBitmap, 44));
                } else if (isFriend) {
                    Bitmap friendBmp = getFriendAvatarBitmap(m.getAuthor());
                    marker.setIcon(createAvatarMarker(friendBmp, 44));
                } else {
                    marker.setIcon(getEmotionIcon(m.getEmotion(), false));
                }
                marker.setOnMarkerClickListener((mrk, mapView) -> {
                    String authorTag = isFriend ? " (By " + m.getAuthor() + ")" : "";
                    Toast.makeText(getContext(),
                            m.getEmotion() + authorTag + ": " + m.getNote(),
                            Toast.LENGTH_SHORT).show();
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
    
    private Drawable getEmotionIcon(String emotion, boolean isFriend) {
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
        
        if (isFriend) {
            return createFriendMarker(color, 40);
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
    
    private Drawable createFriendMarker(int color, int radius) {
        Bitmap bitmap = Bitmap.createBitmap(radius * 2, radius * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(radius, radius, radius, paint);
        
        Paint stroke = new Paint();
        stroke.setColor(Color.GREEN);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(12f);
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

    /**
     * Creates a circular map marker from a Bitmap.
     * Uses PorterDuff SRC_IN to clip the scaled image to a circle,
     * then draws a white border ring around it.
     */
    private Drawable createAvatarMarker(Bitmap source, int radius) {
        int size = radius * 2;
        // Scale source to fit
        Bitmap scaled = Bitmap.createScaledBitmap(source, size, size, true);

        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // Draw circle mask
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(radius, radius, radius, maskPaint);

        // Clip source bitmap to circle
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(scaled, 0, 0, maskPaint);

        // White border
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(5f);
        canvas.drawCircle(radius, radius, radius - 3f, border);

        return new BitmapDrawable(getResources(), output);
    }

    /**
     * Returns the avatar bitmap for a given author username.
     * Checks friendAvatarCache first. On miss, loads from StorageManager
     * (or generates initials) and caches the result.
     */
    private Bitmap getFriendAvatarBitmap(String author) {
        if (author == null) return AvatarUtils.decodeAvatar(
                AvatarUtils.generateInitialsAvatar("?"));

        if (friendAvatarCache.containsKey(author)) {
            return friendAvatarCache.get(author);
        }

        Bitmap bmp;
        User friend = StorageManager.findUserByUsername(getContext(), author);
        if (friend != null && friend.getAvatar() != null
                && friend.getAvatar().value != null
                && !friend.getAvatar().value.isEmpty()) {
            bmp = AvatarUtils.decodeAvatar(friend.getAvatar().value);
        } else {
            // Friend not registered locally yet — generate initials avatar
            bmp = AvatarUtils.decodeAvatar(AvatarUtils.generateInitialsAvatar(author));
        }
        friendAvatarCache.put(author, bmp);
        return bmp;
    }

    private static class Cluster {
        double centerLat;
        double centerLng;
        List<Memory> memories = new ArrayList<>();
    }
}
