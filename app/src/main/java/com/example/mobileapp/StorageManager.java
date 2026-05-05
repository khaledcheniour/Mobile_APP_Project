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

public class StorageManager {
    private static final String FILE_NAME = "memories.json";

    public static List<Memory> loadMemories(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<Memory>>(){}.getType();
            List<Memory> memories = gson.fromJson(reader, listType);
            if (memories == null) return new ArrayList<>();
            return memories;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveMemories(Context context, List<Memory> memories) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(memories, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void addMemory(Context context, Memory memory) {
        List<Memory> memories = loadMemories(context);
        memories.add(memory);
        saveMemories(context, memories);
    }

    public static List<User> loadUsers(Context context) {
        File file = new File(context.getFilesDir(), "users.json");
        if (!file.exists()) return new ArrayList<>();
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<User>>(){}.getType();
            List<User> users = gson.fromJson(reader, listType);
            return users == null ? new ArrayList<>() : users;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void saveUsers(Context context, List<User> users) {
        File file = new File(context.getFilesDir(), "users.json");
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(users, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static User getUser(Context context, String username) {
        if (username == null) return null;
        for (User u : loadUsers(context)) {
            if (username.equals(u.getUsername())) return u;
        }
        return null;
    }

    public static void addUserIfNew(Context context, String username) {
        if (username == null || username.equals("Guest")) return;
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (username.equals(u.getUsername())) return; // Already exists
        }
        users.add(new User(username));
        saveUsers(context, users);
    }

    public static void sendFriendRequest(Context context, String fromUser, String toUser) {
        List<User> users = loadUsers(context);
        for (User u : users) {
            if (u.getUsername().equals(toUser)) {
                if (!u.getFriendRequests().contains(fromUser) && !u.getFriends().contains(fromUser)) {
                    u.getFriendRequests().add(fromUser);
                }
                break;
            }
        }
        saveUsers(context, users);
    }

    public static void acceptFriendRequest(Context context, String currentUser, String newFriend) {
        List<User> users = loadUsers(context);
        User current = null;
        User friend = null;
        for (User u : users) {
            if (u.getUsername().equals(currentUser)) current = u;
            if (u.getUsername().equals(newFriend)) friend = u;
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
        List<Memory> allMemories = loadMemories(context);
        if (username == null || username.equals("Guest")) return allMemories;

        User user = getUser(context, username);
        if (user == null) return new ArrayList<>();

        List<String> visibleAuthors = new ArrayList<>(user.getFriends());
        visibleAuthors.add(username);

        List<Memory> visibleMemories = new ArrayList<>();
        for (Memory m : allMemories) {
            if (m.getAuthor() != null && visibleAuthors.contains(m.getAuthor())) {
                visibleMemories.add(m);
            } else if (m.getAuthor() == null) {
                visibleMemories.add(m); // Keep old memories without author
            }
        }
        return visibleMemories;
    }
}
