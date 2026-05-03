package com.example.mobileapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class User implements Serializable {
    private String username;
    private List<String> friendRequests;
    private List<String> friends;

    public User() {
        this.friendRequests = new ArrayList<>();
        this.friends = new ArrayList<>();
    }

    public User(String username) {
        this.username = username;
        this.friendRequests = new ArrayList<>();
        this.friends = new ArrayList<>();
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public List<String> getFriendRequests() { return friendRequests; }
    public void setFriendRequests(List<String> friendRequests) { this.friendRequests = friendRequests; }

    public List<String> getFriends() { return friends; }
    public void setFriends(List<String> friends) { this.friends = friends; }
}
