package com.example.mobileapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileapp.databinding.ActivityLoginBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login screen.
 *
 * Behaviour:
 *  - Both username and password are required.
 *  - Credentials are verified against the local JSON store (SHA-256 hashes).
 *  - Legacy users (no passwordHash) are auto-migrated on first login.
 *  - A loading spinner is shown during the (simulated async) auth check.
 *  - Guest login bypasses credential checks.
 *  - "Register" link opens RegisterActivity.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    // Background executor for the JSON I/O so we never block the UI thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler   = new Handler(Looper.getMainLooper());

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Skip login if a session already exists
        SharedPreferences prefs = getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE);
        if (prefs.getString("current_user", null) != null) {
            goToMain();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        setupListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // =========================================================================
    // UI wiring
    // =========================================================================

    private void setupListeners() {

        // Clear errors as the user types
        clearErrorOnType(binding.etUsername, binding.tilUsername);
        clearErrorOnType(binding.etPassword, binding.tilPassword);

        // IME "Done" on password field triggers login
        binding.etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.btnGuest.setOnClickListener(v -> {
            getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE)
                    .edit().putString("current_user", "Guest").apply();
            goToMain();
        });

        binding.tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    // =========================================================================
    // Login logic
    // =========================================================================

    private void attemptLogin() {
        // --- Collect & trim ---
        String username = text(binding.etUsername);
        String password = text(binding.etPassword);

        // --- Validate empty fields ---
        boolean valid = true;
        if (username.isEmpty()) {
            binding.tilUsername.setError(getString(R.string.error_username_required));
            valid = false;
        }
        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.error_password_required));
            valid = false;
        }
        if (!valid) return;

        // --- Show loading ---
        setLoading(true);

        // --- Verify credentials off the UI thread ---
        executor.execute(() -> {
            User user = StorageManager.verifyLogin(LoginActivity.this, username, password);

            mainHandler.post(() -> {
                setLoading(false);
                if (user != null) {
                    onLoginSuccess(user);
                } else {
                    onLoginFailure();
                }
            });
        });
    }

    private void onLoginSuccess(User user) {
        getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE)
                .edit().putString("current_user", user.getUsername()).apply();
        goToMain();
    }

    private void onLoginFailure() {
        binding.tilUsername.setError(getString(R.string.error_invalid_credentials));
        binding.tilPassword.setError(" "); // shows red underline without text
        Snackbar.make(binding.getRoot(),
                R.string.error_invalid_credentials,
                Snackbar.LENGTH_LONG).show();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setLoading(boolean loading) {
        binding.progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!loading);
        binding.btnGuest.setEnabled(!loading);
        binding.etUsername.setEnabled(!loading);
        binding.etPassword.setEnabled(!loading);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static String text(com.google.android.material.textfield.TextInputEditText et) {
        Editable e = et.getText();
        return e == null ? "" : e.toString().trim();
    }

    private void clearErrorOnType(
            com.google.android.material.textfield.TextInputEditText et,
            com.google.android.material.textfield.TextInputLayout til) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                til.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
}
