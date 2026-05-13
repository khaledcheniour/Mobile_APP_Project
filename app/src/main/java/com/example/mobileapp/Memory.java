package com.example.mobileapp;

import java.io.Serializable;
import java.util.UUID;

public class Memory implements Serializable {
    private String id;
    private double latitude;
    private double longitude;
    private String note;
    private String emotion;
    private String imagePath;
    private long timestamp;
    private String author;
    private String place;

    public Memory() {}

    public Memory(double latitude, double longitude, String note, String emotion, String imagePath, String place) {
        this.id = UUID.randomUUID().toString();
        this.latitude = latitude;
        this.longitude = longitude;
        this.note = note;
        this.emotion = emotion;
        this.imagePath = imagePath;
        this.place = place;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
}
