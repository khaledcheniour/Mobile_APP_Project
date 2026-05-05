package com.example.mobileapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Friends screen.
 *
 * Shows:
 *  - Friend requests (avatar + name, tap to accept)
 *  - Add Friend input
 *  - My Friends list (avatar + name for each friend)
 *
 * Avatars are loaded off the UI thread and rendered into each row.
 */
public class FriendsFragment extends Fragment {

    private LinearLayout llRequestsContainer;
    private LinearLayout llFriendsContainer;
    private EditText     etFriendUsername;

    private final ExecutorService executor  = Executors.newSingleThreadExecutor();
    private final Handler        mainThread = new Handler(Looper.getMainLooper());

    public FriendsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        llRequestsContainer = view.findViewById(R.id.ll_requests_container);
        llFriendsContainer  = view.findViewById(R.id.ll_friends_container);
        etFriendUsername    = view.findViewById(R.id.et_friend_username);
        Button btnSendRequest = view.findViewById(R.id.btn_send_request);

        btnSendRequest.setOnClickListener(v -> {
            String toUser = etFriendUsername.getText().toString().trim();
            if (!toUser.isEmpty()) {
                SharedPreferences prefs = requireContext()
                        .getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
                String myUsername = prefs.getString("current_user", "Guest");
                StorageManager.sendFriendRequest(getContext(), myUsername, toUser);
                Toast.makeText(getContext(), "Request sent!", Toast.LENGTH_SHORT).show();
                etFriendUsername.setText("");
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }

    // =========================================================================
    // Data refresh
    // =========================================================================

    private void refreshData() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("MemoryAppPrefs", Context.MODE_PRIVATE);
        String myUsername = prefs.getString("current_user", "Guest");
        User   me         = StorageManager.getUser(getContext(), myUsername);

        llRequestsContainer.removeAllViews();
        llFriendsContainer.removeAllViews();

        if (me == null) {
            addEmptyLabel(llRequestsContainer, "No new requests");
            addEmptyLabel(llFriendsContainer,  "No friends yet");
            return;
        }

        // ── Friend requests ──────────────────────────────────────────────────
        List<String> requests = me.getFriendRequests();
        if (requests == null || requests.isEmpty()) {
            addEmptyLabel(llRequestsContainer, "No new requests");
        } else {
            for (String requester : requests) {
                addFriendRow(llRequestsContainer, requester, true, myUsername);
            }
        }

        // ── My friends ──────────────────────────────────────────────────────
        List<String> friends = me.getFriends();
        if (friends == null || friends.isEmpty()) {
            addEmptyLabel(llFriendsContainer, "No friends yet");
        } else {
            for (String friend : friends) {
                addFriendRow(llFriendsContainer, friend, false, myUsername);
            }
        }
    }

    // =========================================================================
    // Row builders
    // =========================================================================

    /**
     * Inflates an item_friend.xml row, sets the name, then loads the avatar
     * asynchronously and updates the ImageView when ready.
     *
     * @param isRequest  true → tapping the row accepts the friend request
     */
    private void addFriendRow(LinearLayout container,
                               String username,
                               boolean isRequest,
                               String myUsername) {

        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_friend, container, false);

        ShapeableImageView ivAvatar = row.findViewById(R.id.iv_friend_avatar);
        TextView           tvName   = row.findViewById(R.id.tv_friend_name);

        tvName.setText(username);

        // Accept request on tap
        if (isRequest) {
            tvName.setText(username + "  (tap to accept)");
            tvName.setTextColor(0xFF1B5E20);
            row.setOnClickListener(v -> {
                StorageManager.acceptFriendRequest(getContext(), myUsername, username);
                Toast.makeText(getContext(), "Accepted " + username, Toast.LENGTH_SHORT).show();
                refreshData();
            });
        }

        container.addView(row);

        // Load avatar off the UI thread
        executor.execute(() -> {
            Bitmap bmp = resolveAvatarBitmap(username);
            mainThread.post(() -> {
                if (isAdded()) {          // guard: fragment might be detached
                    ivAvatar.setBackground(null);
                    ivAvatar.setImageBitmap(bmp);
                }
            });
        });
    }

    /** Small grey italic label used when a list is empty. */
    private void addEmptyLabel(LinearLayout container, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(0xFF9E9E9E);
        tv.setTextSize(14f);
        container.addView(tv);
    }

    // =========================================================================
    // Avatar resolution
    // =========================================================================

    /**
     * Returns the stored avatar bitmap for a username, or a generated
     * initials bitmap if none is stored. Safe to call on any thread.
     */
    private Bitmap resolveAvatarBitmap(String username) {
        User u = StorageManager.findUserByUsername(requireContext(), username);
        if (u != null && u.getAvatar() != null
                && u.getAvatar().value != null
                && !u.getAvatar().value.isEmpty()) {
            return AvatarUtils.decodeAvatar(u.getAvatar().value);
        }
        return AvatarUtils.decodeAvatar(AvatarUtils.generateInitialsAvatar(username));
    }
}
