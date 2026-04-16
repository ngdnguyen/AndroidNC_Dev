package com.example.soundfriends.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.soundfriends.R;

public class ImageUtils {
    public static void loadAvatar(Context context, String avatarData, ImageView imageView) {
        if (avatarData == null || avatarData.isEmpty()) {
            imageView.setImageResource(R.drawable.empty_avatar);
            return;
        }

        if (avatarData.startsWith("http")) {
            // It's a URL (e.g. from Google)
            Glide.with(context)
                    .load(avatarData)
                    .placeholder(R.drawable.empty_avatar)
                    .into(imageView);
        } else {
            // Assume it's Base64
            try {
                String cleanBase64 = avatarData;
                if (avatarData.contains(",")) {
                    cleanBase64 = avatarData.split(",")[1];
                }
                byte[] decodedString = Base64.decode(cleanBase64, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                if (decodedByte != null) {
                    imageView.setImageBitmap(decodedByte);
                } else {
                    imageView.setImageResource(R.drawable.empty_avatar);
                }
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.empty_avatar);
            }
        }
    }
}