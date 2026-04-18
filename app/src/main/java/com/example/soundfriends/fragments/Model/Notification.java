package com.example.soundfriends.fragments.Model;

import com.google.firebase.database.PropertyName;

public class Notification {
    private String id;
    private String fromUserId;
    private String fromUserName;
    private String fromUserAvatar;
    private String type; // "like", "comment", "follow", "new_post"
    private String message;
    private String songId;
    private long timestamp;
    private boolean isRead;

    public Notification() {}

    public Notification(String id, String fromUserId, String fromUserName, String fromUserAvatar, String type, String message, String songId, long timestamp, boolean isRead) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.fromUserAvatar = fromUserAvatar;
        this.type = type;
        this.message = message;
        this.songId = songId;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    public String getFromUserName() { return fromUserName; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }
    public String getFromUserAvatar() { return fromUserAvatar; }
    public void setFromUserAvatar(String fromUserAvatar) { this.fromUserAvatar = fromUserAvatar; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSongId() { return songId; }
    public void setSongId(String songId) { this.songId = songId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @PropertyName("isRead")
    public boolean isRead() { return isRead; }
    @PropertyName("isRead")
    public void setRead(boolean read) { isRead = read; }
}
