package com.example.soundfriends.fragments.Model;

import android.graphics.Bitmap;

public class Songs {

        public  String id, title, artist, category, urlImg, srl, userID;
        int indexSong;

        public Songs() {
        }

        public Songs(int indexSong, String id, String title, String artist, String category, String urlImg, String srl, String userID) {


            if(title.trim().equals("")){
                title = "No title";
            }

            this.id = id;
            this.title = title;
            this.category = category;
            this.artist = artist;
            this.urlImg = urlImg;
            this.srl = srl;
            this.userID = userID;
            this.indexSong = indexSong;
        }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getId() {return id;}
    public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getUrlImg() {
            return urlImg;
        }

        public void setUrlImg(String urlImg) {
            this.urlImg = urlImg;
        }

        public String getSrl() {
            return srl;
        }

        public void setSrl(String srl) {
            this.srl = srl;
        }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getIndexSong() {
        return indexSong;
    }

    public void setIndexSong(int indexSong) {
        this.indexSong = indexSong;
    }
}
