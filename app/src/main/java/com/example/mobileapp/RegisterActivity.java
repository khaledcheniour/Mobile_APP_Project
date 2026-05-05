package com.example.mobileapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobileapp.databinding.ActivityRegisterBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Registration screen.
 *
 * Flow:
 *  1. User fills username, password, confirm password.
 *  2. Optionally taps the avatar circle to pick an image from the gallery.
 *     → If no image is picked, an initials avatar is auto-generated.
 *  3. On submit:
 *     a. Validate all fields (inline TextInputLayout errors).
 *     b. Check username uniqueness.
 *     c. Hash the password (SHA-256).
 *     d. Generate / store the avatar as Base64 PNG in the User JSON.
 *     e. Persist the new User and start MainActivity.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    /** Base64-encoded PNG of the chosen image, or null if none picked yet. */
    private String uploadedAvatarBase64 = null;

    private final ExecutorService executor  = Executors.newSingleThreadExecutor();
    private final Handler mainHandler       = new Handler(Looper.getMainLooper());

    // ── Image picker (modern Activity Result API) ──────────────────────────
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) processPickedImage(uri);
                    });

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge-to-edge padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        setupListeners();
        refreshAvatarPreview(); // Show initials preview immediately
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

        // Back arrow
        binding.btnBack.setOnClickListener(v -> finish());

        // "Already have an account? Login"
        binding.tvGoLogin.setOnClickListener(v -> finish());

        // Avatar / image picker
        binding.flAvatar.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        binding.ivCameraBadge.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Refresh initials preview when username changes
        binding.etRegUsername.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                binding.tilRegUsername.setError(null);
                if (uploadedAvatarBase64 == null) refreshAvatarPreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Clear errors on type
        clearErrorOnType(binding.etRegPassword,  binding.tilRegPassword);
        clearErrorOnType(binding.etRegConfirm,   binding.tilRegConfirm);

        // IME Done on confirm field → register
        binding.etRegConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
    }

    // =========================================================================
    // Avatar helpers
    // =========================================================================

    /**
     * Shows a live initials preview while the user hasn't yet uploaded an image.
     * Called every time the username field changes.
     */
    private void refreshAvatarPreview() {
        String username = text(binding.etRegUsername);
        if (username.isEmpty()) {
            binding.ivAvatar.setImageResource(0);
            binding.ivAvatar.setBackground(
                    getDrawable(R.drawable.bg_avatar_placeholder));
            return;
        }
        // Generate on background thread (Bitmap creation is cheap but keeps UI smooth)
        executor.execute(() -> {
            String b64 = AvatarUtils.generateInitialsAvatar(username);
            Bitmap bmp = AvatarUtils.decodeAvatar(b64);
            mainHandler.post(() -> {
                binding.ivAvatar.setBackground(null);
                binding.ivAvatar.setImageBitmap(bmp);
            });
        });
    }

    /**
     * Compresses the picked image to ≤ 256 KB (JPEG quality 70) and converts to Base64.
     * Runs on the background executor; updates the ImageView when done.
     */
    private void processPickedImage(Uri uri) {
        setLoading(true);
        executor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return;

                Bitmap original = BitmapFactory.decodeStream(is);

                // Scale down to 400×400 max to keep Base64 size manageable
                Bitmap scaled = scaleBitmap(original, 400, 400);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                mainHandler.post(() -> {
                    uploadedAvatarBase64 = b64;
                    binding.ivAvatar.setBackground(null);
                    binding.ivAvatar.setImageBitmap(scaled);
                    setLoading(false);
                });
            } catch (IOException e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    setLoading(false);
                    Snackbar.make(binding.getRoot(),
                            R.string.error_image_load, Snackbar.LENGTH_SHORT).show();
                });
            }
        });
    }

    /** Scales a bitmap so neither dimension exceeds maxW / maxH, preserving aspect ratio. */
    private static Bitmap scaleBitmap(Bitmap src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    // =========================================================================
    // Registration logic
    // =========================================================================

    private void attemptRegister() {
        String username = text(binding.etRegUsername);
        String password = text(binding.etRegPassword);
        String confirm  = text(binding.etRegConfirm);

        // ── Field validation ──────────────────────────────────────────────────
        boolean valid = true;

        if (username.isEmpty()) {
            binding.tilRegUsername.setError(getString(R.string.error_username_required));
            valid = false;
        } else if (username.length() < 3) {
            binding.tilRegUsername.setError(getString(R.string.error_username_short));
            valid = false;
        }

        if (password.isEmpty()) {
            binding.tilRegPassword.setError(getString(R.string.error_password_required));
            valid = false;
        } else if (password.length() < 6) {
            binding.tilRegPassword.setError(getString(R.string.error_password_short));
            valid = false;
        }

        if (confirm.isEmpty()) {
            binding.tilRegConfirm.setError(getString(R.string.error_confirm_required));
            valid = false;
        } else if (!confirm.equals(password)) {
            binding.tilRegConfirm.setError(getString(R.string.error_passwords_no_match));
            valid = false;
        }

        if (!valid) return;

        // ── Async: uniqueness check + persist ────────────────────────────────
        setLoading(true);

        final String finalUsername = username;
        final String finalPassword = password;

        executor.execute(() -> {
            // Uniqueness check
            boolean exists = StorageManager.usernameExists(
                    RegisterActivity.this, finalUsername);

            if (exists) {
                mainHandler.post(() -> {
                    setLoading(false);
                    binding.tilRegUsername.setError(
                            getString(R.string.error_username_taken));
                });
                return;
            }

            // Determine avatar
            String avatarB64;
            String avatarType;
            if (uploadedAvatarBase64 != null) {
                avatarB64  = uploadedAvatarBase64;
                avatarType = "image";
            } else {
                avatarB64  = AvatarUtils.generateInitialsAvatar(finalUsername);
                avatarType = "generated";
            }

            // Persist — password is hashed inside registerUser()
            User newUser = StorageManager.registerUser(
                    RegisterActivity.this,
                    finalUsername,
                    finalPassword,
                    avatarB64,
                    avatarType);

            mainHandler.post(() -> {
                setLoading(false);
                if (newUser != null) {
                    onRegisterSuccess(newUser);
                } else {
                    Snackbar.make(binding.getRoot(),
                            R.string.error_register_failed,
                            Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }

    private void onRegisterSuccess(User user) {
        // Save session
        getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE)
                .edit().putString("current_user", user.getUsername()).apply();

        Snackbar.make(binding.getRoot(),
                getString(R.string.register_success, user.getUsername()),
                Snackbar.LENGTH_SHORT).show();

        // Small delay so the user sees the success Snackbar before navigating
        binding.getRoot().postDelayed(() -> {
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }, 1000);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void setLoading(boolean loading) {
        binding.progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!loading);
        binding.etRegUsername.setEnabled(!loading);
        binding.etRegPassword.setEnabled(!loading);
        binding.etRegConfirm.setEnabled(!loading);
        binding.flAvatar.setEnabled(!loading);
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
