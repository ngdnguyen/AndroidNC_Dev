package com.example.soundfriends.fragments.Model;

import java.io.Serializable;

public class User implements Serializable {
    private String userID;
    private String name;
    private String email;
    private String avatar;
    private String bio;

    public User() {
    }

    public User(String userID, String name, String email, String avatar) {
        this.userID = userID;
        this.name = name;
        this.email = email;
        this.avatar = avatar;
        this.bio = "";
    }

    public String getUserID() { return userID; }
    public String getUid() { return userID; } // Alias for getUserID() to match FirebaseUser API and fix build errors
    public void setUserID(String userID) { this.userID = userID; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}