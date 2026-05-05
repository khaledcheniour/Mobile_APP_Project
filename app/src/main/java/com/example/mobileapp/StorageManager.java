package com.example.mobileapp;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Local JSON storage manager.
 * All data is persisted in the app's private files directory as JSON,
 * matching the Firebase Realtime DB / Firestore document shape defined in User.java.
 *
 * To migrate to Firebase later, replace the methods below with Firestore calls —
 * the User model is already compatible.
 */
public class StorageManager {

    private static final String MEMORIES_FILE = "memories.json";
    private static final String USERS_FILE    = "users.json";

    private StorageManager() {}

    // =========================================================================
    // Memory CRUD
    // =========================================================================

    public static List<Memory> loadMemories(Context context) {
        File file = new File(context.getFilesDir(), MEMORIES_FILE);
        if (!file.exists()) return new ArrayList<>();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ArrayList<Memory>>() {}.getType();
            List<Memory> list = new Gson().fromJson(reader, type);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveMemories(Context context, List<Memory> memories) {
        File file = new File(context.getFilesDir(), MEMORIES_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(memories, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addMemory(Context context, Memory memory) {
        List<Memory> memories = loadMemories(context);
        memories.add(memory);
        saveMemories(context, memories);
    }

    // =========================================================================
    // User CRUD
    // =========================================================================

    public static List<User> loadUsers(Context context) {
        File file = new File(context.getFilesDir(), USERS_FILE);
        if (!file.exists()) return new ArrayList<>();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<ArrayList<User>>() {}.getType();
            List<User> list = new Gson().fromJson(reader, type);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveUsers(Context context, List<User> users) {
        File file = new File(context.getFilesDir(), USERS_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(users, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Finds a user by username (case-insensitive).
     * @return the User object, or null if not found.
     */
    public static User findUserByUsername(Context context, String username) {
        if (username == null) return null;
        for (User u : loadUsers(context)) {
            if (username.equalsIgnoreCase(u.getUsername())) return u;
        }
        return null;
    }

    /** @return true if a user with this username already exists. */
    public static boolean usernameExists(Context context, String username) {
        return findUserByUsername(context, username) != null;
    }

    // =========================================================================
    // Auth — Register
    // =========================================================================

    /**
     * Registers a new user.
     * The caller is responsible for validating uniqueness before calling this.
     *
     * @param context    Android context
     * @param username   chosen username
     * @param plainPassword raw password (will be hashed here)
     * @param avatarBase64  Base64-encoded PNG avatar (generated or uploaded)
     * @param avatarType    "generated" or "image"
     * @return the created User, or null on failure
     */
    public static User registerUser(Context context,
                                    String username,
                                    String plainPassword,
                                    String avatarBase64,
                                    String avatarType) {
        List<User> users = loadUsers(context);

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPasswordHash(PasswordUtils.hashPassword(plainPassword));
        newUser.setAvatar(new User.Avatar(avatarType, avatarBase64));
        newUser.setCreatedAt(new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .format(new java.util.Date()));
        newUser.setFriendRequests(new ArrayList<>());
        newUser.setFriends(new ArrayList<>());

        users.add(newUser);
        saveUsers(context, users);
        return newUser;
    }

    // =========================================================================
    // Auth — Login
    // =========================================================================

    /**
     * Verifies login credentials.
     *
     * Handles two cases:
     *  1. New user (has passwordHash) — compares SHA-256 hashes.
     *  2. Legacy user (no passwordHash, created by old addUserIfNew) —
     *     allows login with any non-empty password and auto-migrates.
     *
     * @return the authenticated User, or null if credentials are wrong.
     */
    public static User verifyLogin(Context context, String username, String plainPassword) {
        User user = findUserByUsername(context, username);
        if (user == null) return null;

        // Legacy user — no password was ever set; auto-migrate
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            migratePasswordForUser(context, user, plainPassword);
            return user;
        }

        // Normal verification
        if (PasswordUtils.verifyPassword(plainPassword, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    /** Sets a password hash on an existing legacy user and persists it. */
    private static void migratePasswordForUser(Context context, User target, String plainPassword) {
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(target.getUsername())) {
                u.setPasswordHash(PasswordUtils.hashPassword(plainPassword));
                // Generate initials avatar if none exists
                if (u.getAvatar() == null) {
                    String b64 = AvatarUtils.generateInitialsAvatar(u.getUsername());
                    u.setAvatar(new User.Avatar("generated", b64));
                }
                break;
            }
        }
        saveUsers(context, users);
    }

    // =========================================================================
    // Legacy helper — kept for backward compatibility
    // =========================================================================

    /** @deprecated Use {@link #registerUser} for new registrations. */
    @Deprecated
    public static void addUserIfNew(Context context, String username) {
        if (username == null || username.equals("Guest")) return;
        if (usernameExists(context, username)) return;
        List<User> users = loadUsers(context);
        users.add(new User(username));
        saveUsers(context, users);
    }

    // =========================================================================
    // Social — friend requests
    // =========================================================================

    public static User getUser(Context context, String username) {
        return findUserByUsername(context, username);
    }

    public static void sendFriendRequest(Context context, String fromUser, String toUser) {
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (u.getUsername().equals(toUser)) {
                if (!u.getFriendRequests().contains(fromUser) &&
                        !u.getFriends().contains(fromUser)) {
                    u.getFriendRequests().add(fromUser);
                }
                break;
            }
        }
        saveUsers(context, users);
    }

    public static void acceptFriendRequest(Context context,
                                           String currentUser,
                                           String newFriend) {
        List<User> users = loadUsers(context);
        User current = null, friend = null;
        for (User u : users) {
            if (u.getUsername().equals(currentUser)) current = u;
            if (u.getUsername().equals(newFriend))   friend  = u;
        }
        if (current != null && friend != null) {
            current.getFriendRequests().remove(newFriend);
            if (!current.getFriends().contains(newFriend)) current.getFriends().add(newFriend);
            if (!friend.getFriends().contains(currentUser)) friend.getFriends().add(currentUser);
            saveUsers(context, users);
        }
    }

    /**
     * Returns the list of accepted friend usernames for the given user.
     * Returns an empty list for Guest accounts.
     */
    public static List<String> getFriendsList(Context context, String username) {
        if (username == null || username.equals("Guest")) return new ArrayList<>();
        User user = getUser(context, username);
        if (user == null) return new ArrayList<>();
        return new ArrayList<>(user.getFriends());
    }

    public static List<Memory> getFriendsAndMyMemories(Context context, String username) {
        List<Memory> all = loadMemories(context);
        if (username == null || username.equals("Guest")) return all;

        User user = findUserByUsername(context, username);
        if (user == null) return new ArrayList<>();

        List<String> visible = new ArrayList<>(user.getFriends());
        visible.add(username);

        List<Memory> result = new ArrayList<>();
        for (Memory m : all) {
            if (m.getAuthor() == null || visible.contains(m.getAuthor())) {
                result.add(m);
            }
        }
        return result;
    }

    // =========================================================================
    // Profile updates
    // =========================================================================

    /**
     * Renames a user.
     * Also updates the author field on every Memory the user has posted.
     *
     * @return true on success, false if newUsername is already taken.
     */
    public static boolean updateUsername(Context context,
                                         String oldUsername,
                                         String newUsername) {
        if (usernameExists(context, newUsername)) return false;

        // Update user record
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(oldUsername)) {
                u.setUsername(newUsername);
                break;
            }
        }
        saveUsers(context, users);

        // Update memory author field
        List<Memory> memories = loadMemories(context);
        for (Memory m : memories) {
            if (oldUsername.equals(m.getAuthor())) {
                m.setAuthor(newUsername);
            }
        }
        saveMemories(context, memories);
        return true;
    }

    /**
     * Changes a user's password after verifying the old one.
     *
     * @return true on success, false if oldPassword is wrong.
     */
    public static boolean updatePassword(Context context,
                                          String username,
                                          String oldPassword,
                                          String newPassword) {
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                // Verify old password
                if (u.getPasswordHash() != null &&
                        !PasswordUtils.verifyPassword(oldPassword, u.getPasswordHash())) {
                    return false;
                }
                u.setPasswordHash(PasswordUtils.hashPassword(newPassword));
                saveUsers(context, users);
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces a user's avatar with a new Base64-encoded PNG.
     *
     * @param avatarBase64 Base64 PNG string
     * @param avatarType   "image" or "generated"
     */
    public static void updateAvatar(Context context,
                                     String username,
                                     String avatarBase64,
                                     String avatarType) {
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                u.setAvatar(new User.Avatar(avatarType, avatarBase64));
                saveUsers(context, users);
                return;
            }
        }
    }
}
