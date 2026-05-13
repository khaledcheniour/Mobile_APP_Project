package com.example.mobileapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.mobileapp.databinding.ActivityMainBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main screen — hosts the BottomNavigationView, FAB, and the circular
 * profile icon in the top bar that opens the profile bottom sheet.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private String currentUsername;

    private final ExecutorService executor  = Executors.newSingleThreadExecutor();
    private final Handler        mainThread = new Handler(Looper.getMainLooper());

    // Image picker registered at construction time (must be before onCreate)
    private final ActivityResultLauncher<String> pictureLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) handleNewProfilePicture(uri); });

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentUsername = prefs().getString("current_user", "Guest");

        setupInsets();
        setupNavigation(savedInstanceState);
        setupFab();
        setupProfileIcon();
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentUsername = prefs().getString("current_user", "Guest");
        loadProfileIcon(binding.ivProfileIcon);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });
    }

    private void setupNavigation(Bundle savedInstanceState) {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selected = null;
            int id = item.getItemId();
            if      (id == R.id.nav_map)     selected = new MapFragment();
            else if (id == R.id.nav_list)    selected = new ListFragment();
            else if (id == R.id.nav_friends) selected = new FriendsFragment();
            else if (id == R.id.nav_places)  selected = new PlacesFragment();
            if (selected != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selected)
                        .commit();
            }
            return true;
        });
        if (savedInstanceState == null) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_map);
        }
    }

    private void setupFab() {
        binding.fabAddMemory.setOnClickListener(v ->
                startActivity(new Intent(this, AddMemoryActivity.class)));
    }

    private void setupProfileIcon() {
        binding.ivProfileIcon.setOnClickListener(v -> showProfileSheet());
        loadProfileIcon(binding.ivProfileIcon);
    }

    // =========================================================================
    // Avatar loading
    // =========================================================================

    private void loadProfileIcon(ShapeableImageView target) {
        final String user = currentUsername;
        executor.execute(() -> {
            Bitmap bmp = resolveAvatarBitmap(user);
            mainThread.post(() -> {
                target.setBackground(null);
                target.setImageBitmap(bmp);
            });
        });
    }

    private Bitmap resolveAvatarBitmap(String username) {
        if (username != null && !username.equals("Guest")) {
            User u = StorageManager.findUserByUsername(this, username);
            if (u != null && u.getAvatar() != null
                    && u.getAvatar().value != null
                    && !u.getAvatar().value.isEmpty()) {
                return AvatarUtils.decodeAvatar(u.getAvatar().value);
            }
        }
        String fallback = (username == null || username.isEmpty()) ? "?" : username;
        return AvatarUtils.decodeAvatar(AvatarUtils.generateInitialsAvatar(fallback));
    }

    // =========================================================================
    // Profile bottom sheet
    // =========================================================================

    private void showProfileSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.bottom_sheet_profile, null);
        sheet.setContentView(v);

        ShapeableImageView ivAvatar = v.findViewById(R.id.iv_sheet_avatar);
        ((TextView) v.findViewById(R.id.tv_sheet_username)).setText(currentUsername);
        loadProfileIcon(ivAvatar);

        v.findViewById(R.id.btn_change_username).setOnClickListener(x -> {
            sheet.dismiss();
            showChangeUsernameDialog();
        });
        v.findViewById(R.id.btn_change_password).setOnClickListener(x -> {
            sheet.dismiss();
            showChangePasswordDialog();
        });
        v.findViewById(R.id.btn_change_picture).setOnClickListener(x -> {
            sheet.dismiss();
            pictureLauncher.launch("image/*");
        });
        v.findViewById(R.id.btn_logout_sheet).setOnClickListener(x -> {
            sheet.dismiss();
            performLogout();
        });

        sheet.show();
    }

    // =========================================================================
    // Change Username dialog
    // =========================================================================

    private void showChangeUsernameDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_username, null);
        TextInputLayout   til = dialogView.findViewById(R.id.til_new_username);
        TextInputEditText et  = dialogView.findViewById(R.id.et_new_username);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.change_username)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)   // null → manual dismiss
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                    String newName = editable(et);
                    til.setError(null);

                    if (newName.isEmpty()) {
                        til.setError(getString(R.string.error_username_required)); return;
                    }
                    if (newName.length() < 3) {
                        til.setError(getString(R.string.error_username_short)); return;
                    }
                    if (newName.equalsIgnoreCase(currentUsername)) {
                        til.setError(getString(R.string.error_username_same)); return;
                    }

                    btn.setEnabled(false);
                    executor.execute(() -> {
                        boolean ok = StorageManager.updateUsername(this, currentUsername, newName);
                        mainThread.post(() -> {
                            btn.setEnabled(true);
                            if (ok) {
                                prefs().edit().putString("current_user", newName).apply();
                                currentUsername = newName;
                                loadProfileIcon(binding.ivProfileIcon);
                                dialog.dismiss();
                                snack(R.string.username_updated);
                            } else {
                                til.setError(getString(R.string.error_username_taken));
                            }
                        });
                    });
                }));

        dialog.show();
    }

    // =========================================================================
    // Change Password dialog
    // =========================================================================

    private void showChangePasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        TextInputLayout   tilOld  = dialogView.findViewById(R.id.til_old_password);
        TextInputLayout   tilNew  = dialogView.findViewById(R.id.til_new_password);
        TextInputLayout   tilConf = dialogView.findViewById(R.id.til_confirm_password);
        TextInputEditText etOld   = dialogView.findViewById(R.id.et_old_password);
        TextInputEditText etNew   = dialogView.findViewById(R.id.et_new_password);
        TextInputEditText etConf  = dialogView.findViewById(R.id.et_confirm_password);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.change_password)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                    String oldPwd  = editable(etOld);
                    String newPwd  = editable(etNew);
                    String confPwd = editable(etConf);
                    tilOld.setError(null); tilNew.setError(null); tilConf.setError(null);

                    boolean valid = true;
                    if (oldPwd.isEmpty())  { tilOld.setError(getString(R.string.error_password_required)); valid = false; }
                    if (newPwd.isEmpty())  { tilNew.setError(getString(R.string.error_password_required)); valid = false; }
                    else if (newPwd.length() < 6) { tilNew.setError(getString(R.string.error_password_short)); valid = false; }
                    if (!confPwd.equals(newPwd)) { tilConf.setError(getString(R.string.error_passwords_no_match)); valid = false; }
                    if (!valid) return;

                    btn.setEnabled(false);
                    executor.execute(() -> {
                        boolean ok = StorageManager.updatePassword(
                                this, currentUsername, oldPwd, newPwd);
                        mainThread.post(() -> {
                            btn.setEnabled(true);
                            if (ok) {
                                dialog.dismiss();
                                snack(R.string.password_updated);
                            } else {
                                tilOld.setError(getString(R.string.error_wrong_password));
                            }
                        });
                    });
                }));

        dialog.show();
    }

    // =========================================================================
    // Change Profile Picture
    // =========================================================================

    private void handleNewProfilePicture(Uri uri) {
        executor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return;
                Bitmap original = BitmapFactory.decodeStream(is);
                Bitmap scaled   = scaleBitmap(original, 400, 400);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String b64 = android.util.Base64.encodeToString(
                        baos.toByteArray(), android.util.Base64.DEFAULT);

                StorageManager.updateAvatar(this, currentUsername, b64, "image");

                mainThread.post(() -> {
                    loadProfileIcon(binding.ivProfileIcon);
                    snack(R.string.avatar_updated);
                });
            } catch (IOException e) {
                e.printStackTrace();
                mainThread.post(() ->
                        Toast.makeText(this, R.string.error_image_load,
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    // =========================================================================
    // Logout
    // =========================================================================

    private void performLogout() {
        prefs().edit().remove("current_user").apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SharedPreferences prefs() {
        return getSharedPreferences("MemoryAppPrefs", MODE_PRIVATE);
    }

    private void snack(int stringRes) {
        Snackbar.make(binding.getRoot(), stringRes, Snackbar.LENGTH_SHORT).show();
    }

    private static String editable(TextInputEditText et) {
        Editable e = et.getText();
        return e == null ? "" : e.toString().trim();
    }

    private static Bitmap scaleBitmap(Bitmap src, int maxW, int maxH) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxW && h <= maxH) return src;
        float scale = Math.min((float) maxW / w, (float) maxH / h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }
}