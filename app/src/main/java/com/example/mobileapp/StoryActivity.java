package com.example.mobileapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StoryActivity extends AppCompatActivity {

    private static final long STORY_DURATION_MS = 3000; // 3 seconds
    private static final long PROGRESS_INTERVAL_MS = 30; // Update progress every 30ms

    private ImageView ivImage;
    private TextView tvEmotion;
    private TextView tvNote;
    private ProgressBar progressStory;

    private List<Memory> storyMemories = new ArrayList<>();
    private int currentIndex = 0;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private long currentStoryStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story);

        ivImage = findViewById(R.id.iv_story_image);
        tvEmotion = findViewById(R.id.tv_story_emotion);
        tvNote = findViewById(R.id.tv_story_note);
        progressStory = findViewById(R.id.progress_story);
        ImageButton btnClose = findViewById(R.id.btn_close_story);

        btnClose.setOnClickListener(v -> finish());

        String place = getIntent().getStringExtra("EXTRA_PLACE");
        if (place == null) {
            finish();
            return;
        }

        loadMemoriesForPlace(place);

        if (storyMemories.isEmpty()) {
            Toast.makeText(this, "No memories found for this place.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startStory();
    }

    private void loadMemoriesForPlace(String place) {
        SharedPreferences prefs = getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
        String currentUser = prefs.getString("current_user", "Guest");

        List<Memory> allMemories = StorageManager.loadMemories(this);
        for (Memory m : allMemories) {
            if (currentUser.equals(m.getAuthor()) && place.equals(m.getPlace())) {
                storyMemories.add(m);
            }
        }
        
        // Sort by timestamp if necessary, but list order is usually fine
    }

    private void startStory() {
        showMemory(currentIndex);

        progressRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - currentStoryStartTime;
                int progress = (int) ((elapsed * 100) / STORY_DURATION_MS);
                
                if (progress >= 100) {
                    progressStory.setProgress(100);
                    advanceStory();
                } else {
                    progressStory.setProgress(progress);
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS);
                }
            }
        };

        currentStoryStartTime = System.currentTimeMillis();
        handler.post(progressRunnable);
    }

    private void advanceStory() {
        handler.removeCallbacks(progressRunnable);
        currentIndex++;
        if (currentIndex < storyMemories.size()) {
            startStory();
        } else {
            finish(); // End of stories
        }
    }

    private void showMemory(int index) {
        Memory m = storyMemories.get(index);
        tvNote.setText(m.getNote());
        tvEmotion.setText(m.getEmotion());

        if (m.getImagePath() != null && !m.getImagePath().isEmpty()) {
            File imgFile = new File(m.getImagePath());
            if (imgFile.exists()) {
                Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                ivImage.setImageBitmap(myBitmap);
                ivImage.setVisibility(View.VISIBLE);
            } else {
                ivImage.setVisibility(View.GONE);
            }
        } else {
            ivImage.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && progressRunnable != null) {
            handler.removeCallbacks(progressRunnable);
        }
    }
}
