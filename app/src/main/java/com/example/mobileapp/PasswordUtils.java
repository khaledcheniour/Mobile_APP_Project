package com.example.mobileapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for password hashing using SHA-256.
 * No BCrypt dependency required — SHA-256 is available in the Java standard library.
 * Firebase-compatible: store the hex hash in the "passwordHash" field.
 */
public class PasswordUtils {

    private PasswordUtils() { /* no instances */ }

    /**
     * Hashes a plain-text password using SHA-256.
     * @param password the raw password entered by the user
     * @return a 64-character lowercase hex string
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present on every JVM/Android
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Verifies a plain-text password against a stored SHA-256 hash.
     * @param plainPassword  the password the user typed
     * @param storedHash     the hash stored in the JSON / Firebase document
     * @return true if the passwords match
     */
    public static boolean verifyPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) return false;
        return hashPassword(plainPassword).equals(storedHash);
    }
}
