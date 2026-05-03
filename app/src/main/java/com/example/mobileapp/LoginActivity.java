package com.example.mobileapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if user is already logged in
        SharedPreferences prefs = getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE);
        String username = prefs.getString("current_user", null);
        if (username != null) {
            startMainActivity();
            return;
        }

        setContentView(R.layout.activity_login);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextInputEditText etUsername = findViewById(R.id.et_username);
        TextInputEditText etPassword = findViewById(R.id.et_password);
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnGuest = findViewById(R.id.btn_guest);

        btnLogin.setOnClickListener(v -> {
            String user = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
            if (!user.isEmpty()) {
                prefs.edit().putString("current_user", user).apply();
                StorageManager.addUserIfNew(this, user);
                startMainActivity();
            } else {
                etUsername.setError("Please enter a username");
            }
        });

        btnGuest.setOnClickListener(v -> {
            prefs.edit().putString("current_user", "Guest").apply();
            startMainActivity();
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
