package com.example.soundfriends.fragments.Model;

public class Comment {
    private String commentId, body, userId, username;
    private int likeCount;
    private String timestamp, songId, avatarUrl;
    private boolean liked;

    public Comment(String commentId, String body, String userId, int likeCount, String timestamp, String songId, String avatarUrl, String username, boolean isLiked) {
        this.commentId = commentId;
        this.body = body;
        this.userId = userId;
        this.likeCount = likeCount;
        this.timestamp = timestamp;
        this.songId = songId;
        this.avatarUrl = avatarUrl;
        this.username = username;
    }

    public Comment() {
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isLiked() {
        return liked;
    }

    public void setLiked(boolean liked) {
        this.liked = liked;
    }

}
