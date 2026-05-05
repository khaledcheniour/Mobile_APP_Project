package com.example.mobileapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * Generates a default avatar for users who do not upload a profile picture.
 * The avatar is a coloured circle with the user's initials drawn in white bold text.
 * The result is returned as a Base64-encoded PNG string suitable for storing in JSON / Firebase.
 */
public class AvatarUtils {

    private AvatarUtils() { /* no instances */ }

    /** Palette of vivid Material-Design-ish colours, one is picked deterministically per username. */
    private static final int[] AVATAR_COLORS = {
            Color.parseColor("#E53935"), // Red
            Color.parseColor("#8E24AA"), // Purple
            Color.parseColor("#1E88E5"), // Blue
            Color.parseColor("#00897B"), // Teal
            Color.parseColor("#F4511E"), // Deep Orange
            Color.parseColor("#3949AB"), // Indigo
            Color.parseColor("#039BE5"), // Light Blue
            Color.parseColor("#7CB342"), // Light Green
            Color.parseColor("#FB8C00"), // Orange
            Color.parseColor("#D81B60")  // Pink
    };

    /**
     * Generates a 200×200 px circular avatar bitmap and returns it as a Base64 PNG string.
     *
     * @param username the user's username; used to pick initials and background colour
     * @return Base64-encoded PNG string
     */
    public static String generateInitialsAvatar(String username) {
        String initials = extractInitials(username);
        int color = AVATAR_COLORS[Math.abs(username.hashCode()) % AVATAR_COLORS.length];

        int size = 200;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // --- Background circle ---
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint);

        // --- Initials text ---
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(80f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Vertically centre the text
        float textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(initials, size / 2f, textY, textPaint);

        // --- Encode to Base64 ---
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    /**
     * Decodes a Base64 avatar string back to a Bitmap for display in an ImageView.
     */
    public static Bitmap decodeAvatar(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String extractInitials(String username) {
        if (username == null || username.trim().isEmpty()) return "?";
        String[] parts = username.trim().split("\\s+");
        if (parts.length >= 2) {
            return (String.valueOf(parts[0].charAt(0)) + parts[1].charAt(0)).toUpperCase();
        }
        // Single-word username → first letter only
        return String.valueOf(username.charAt(0)).toUpperCase();
    }
}
