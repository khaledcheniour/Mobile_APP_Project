package com.example.mobileapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class AddMemoryActivity extends AppCompatActivity {

    private ImageView imgPreview;
    private EditText etPlace;
    private EditText etNote;
    private Spinner spinnerEmotion;
    private MapView mapPicker;

    private String currentImagePath = "";
    private GeoPoint selectedLocation = null;
    private Marker locationMarker = null;

    private final ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    compressAndSaveImage(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx));
        
        setContentView(R.layout.activity_add_memory);

        imgPreview = findViewById(R.id.img_preview);
        etPlace = findViewById(R.id.et_place);
        etNote = findViewById(R.id.et_note);
        spinnerEmotion = findViewById(R.id.spinner_emotion);
        mapPicker = findViewById(R.id.map_picker);
        Button btnPhoto = findViewById(R.id.btn_photo);
        Button btnSave = findViewById(R.id.btn_save);

        String[] emotions = {"Happy", "Sad", "Excited", "Peaceful", "Angry", "Nostalgic"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, emotions);
        spinnerEmotion.setAdapter(adapter);

        // Setup Map Picker
        mapPicker.setMultiTouchControls(true);
        mapPicker.getController().setZoom(10.0);
        mapPicker.getController().setCenter(new GeoPoint(48.8566, 2.3522));

        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                selectedLocation = p;
                if (locationMarker == null) {
                    locationMarker = new Marker(mapPicker);
                    mapPicker.getOverlays().add(locationMarker);
                }
                locationMarker.setPosition(p);
                mapPicker.invalidate();
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        };
        mapPicker.getOverlays().add(new MapEventsOverlay(mReceive));

        // Fix map scroll inside scrollview
        mapPicker.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });

        btnPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            photoPickerLauncher.launch(intent);
        });

        btnSave.setOnClickListener(v -> saveMemory());
    }

    private void compressAndSaveImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            
            // Compressor: scaling down 
            int MAX_SIZE = 800;
            float ratio = Math.min((float) MAX_SIZE / bitmap.getWidth(), (float) MAX_SIZE / bitmap.getHeight());
            int width = Math.round(ratio * bitmap.getWidth());
            int height = Math.round(ratio * bitmap.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
            
            File outputDir = getFilesDir();
            File outputFile = new File(outputDir, UUID.randomUUID().toString() + ".jpg");
            FileOutputStream out = new FileOutputStream(outputFile);
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.flush();
            out.close();

            currentImagePath = outputFile.getAbsolutePath();
            imgPreview.setImageBitmap(scaled);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveMemory() {
        if (selectedLocation == null) {
            Toast.makeText(this, "Please pick a location on the map", Toast.LENGTH_SHORT).show();
            return;
        }

        String place = etPlace.getText().toString().trim();
        if (place.isEmpty()) {
            Toast.makeText(this, "Please enter a place name", Toast.LENGTH_SHORT).show();
            return;
        }

        String note = etNote.getText().toString();
        String emotion = spinnerEmotion.getSelectedItem().toString();

        Memory memory = new Memory(selectedLocation.getLatitude(), selectedLocation.getLongitude(), note, emotion, currentImagePath, place);

        android.content.SharedPreferences prefs = getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("current_user", "Guest");
        memory.setAuthor(username);

        StorageManager.addMemory(this, memory);
        
        Toast.makeText(this, "Memory Saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mapPicker.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapPicker.onPause();
    }
}
