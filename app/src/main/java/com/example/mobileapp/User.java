package com.example.mobileapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * User model — Firebase Realtime DB / Firestore compatible JSON structure.
 *
 * JSON shape:
 * {
 *   "username": "oussama",
 *   "passwordHash": "sha256hexstring",
 *   "avatar": { "type": "generated" | "image", "value": "base64string" },
 *   "createdAt": "ISO-8601 timestamp",
 *   "friendRequests": ["alice", "bob"],
 *   "friends": ["charlie"]
 * }
 */
public class User implements Serializable {

    private String username;
    private String passwordHash;
    private Avatar avatar;
    private String createdAt;
    private List<String> friendRequests;
    private List<String> friends;

    // -----------------------------------------------------------------------
    // Nested Avatar model
    // -----------------------------------------------------------------------

    public static class Avatar implements Serializable {
        /** "generated" (initials circle) or "image" (user-uploaded, Base64). */
        public String type;
        /** Base64-encoded PNG for both types. */
        public String value;

        public Avatar() {}

        public Avatar(String type, String value) {
            this.type  = type;
            this.value = value;
        }
    }

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public User() {
        this.friendRequests = new ArrayList<>();
        this.friends        = new ArrayList<>();
    }

    /** Legacy constructor — kept for backward compatibility. */
    public User(String username) {
        this.username       = username;
        this.friendRequests = new ArrayList<>();
        this.friends        = new ArrayList<>();
    }

    // -----------------------------------------------------------------------
    // Getters & setters
    // -----------------------------------------------------------------------

    public String getUsername()                         { return username; }
    public void   setUsername(String username)          { this.username = username; }

    public String getPasswordHash()                     { return passwordHash; }
    public void   setPasswordHash(String passwordHash)  { this.passwordHash = passwordHash; }

    public Avatar getAvatar()                           { return avatar; }
    public void   setAvatar(Avatar avatar)              { this.avatar = avatar; }

    public String getCreatedAt()                        { return createdAt; }
    public void   setCreatedAt(String createdAt)        { this.createdAt = createdAt; }

    public List<String> getFriendRequests()             { return friendRequests; }
    public void setFriendRequests(List<String> l)       { this.friendRequests = l; }

    public List<String> getFriends()                    { return friends; }
    public void setFriends(List<String> l)              { this.friends = l; }
}
