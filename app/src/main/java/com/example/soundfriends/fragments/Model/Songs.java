package com.example.soundfriends.fragments.Model;

import java.io.Serializable;

public class Songs implements Serializable {

    public String id, title, artist, category, urlImg, srl, userID;
    public String userName, userAvatar, postContent;
    public String sharedFromUserID, sharedFromUserName, sharedFromUserAvatar, sharedFromPostContent;
    public boolean isShared;
    public long timestamp;
    public long likes;
    int indexSong;

    public Songs() {
    }

    // Constructor dùng cho việc tạo bài đăng mạng xã hội mới
    public Songs(String id, String title, String artist, String category, String urlImg, String srl, String userID, 
                 String userName, String userAvatar, String postContent) {
        this.id = id;
        this.title = (title == null || title.trim().isEmpty()) ? "Không tên" : title;
        this.artist = (artist == null || artist.trim().isEmpty()) ? "Nghệ sĩ" : artist;
        this.category = category;
        this.urlImg = urlImg;
        this.srl = srl;
        this.userID = userID;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.postContent = postContent;
        this.isShared = false;
        this.timestamp = System.currentTimeMillis();
        this.likes = 0;
    }

    // Constructor cho bài chia sẻ (Facebook style)
    public Songs(String id, String title, String artist, String category, String urlImg, String srl, String userID, 
                 String userName, String userAvatar, String postContent, 
                 String sharedFromUserID, String sharedFromUserName, String sharedFromUserAvatar, String sharedFromPostContent) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.category = category;
        this.urlImg = urlImg;
        this.srl = srl;
        this.userID = userID;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.postContent = postContent;
        this.sharedFromUserID = sharedFromUserID;
        this.sharedFromUserName = sharedFromUserName;
        this.sharedFromUserAvatar = sharedFromUserAvatar;
        this.sharedFromPostContent = sharedFromPostContent;
        this.isShared = true;
        this.timestamp = System.currentTimeMillis();
        this.likes = 0;
    }

    public Songs(int indexSong, String id, String title, String artist, String category, String urlImg, String srl, String userID) {
        this.indexSong = indexSong;
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.category = category;
        this.urlImg = urlImg;
        this.srl = srl;
        this.userID = userID;
        this.timestamp = System.currentTimeMillis();
        this.likes = 0;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUrlImg() { return urlImg; }
    public void setUrlImg(String urlImg) { this.urlImg = urlImg; }
    public String getSrl() { return srl; }
    public void setSrl(String srl) { this.srl = srl; }
    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserAvatar() { return userAvatar; }
    public void setUserAvatar(String userAvatar) { this.userAvatar = userAvatar; }
    public String getPostContent() { return postContent; }
    public void setPostContent(String postContent) { this.postContent = postContent; }
    
    public String getSharedFromUserID() { return sharedFromUserID; }
    public void setSharedFromUserID(String sharedFromUserID) { this.sharedFromUserID = sharedFromUserID; }
    public String getSharedFromUserName() { return sharedFromUserName; }
    public void setSharedFromUserName(String sharedFromUserName) { this.sharedFromUserName = sharedFromUserName; }
    public String getSharedFromUserAvatar() { return sharedFromUserAvatar; }
    public void setSharedFromUserAvatar(String sharedFromUserAvatar) { this.sharedFromUserAvatar = sharedFromUserAvatar; }
    public String getSharedFromPostContent() { return sharedFromPostContent; }
    public void setSharedFromPostContent(String sharedFromPostContent) { this.sharedFromPostContent = sharedFromPostContent; }
    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { isShared = shared; }

    public int getIndexSong() { return indexSong; }
    public void setIndexSong(int indexSong) { this.indexSong = indexSong; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }
}