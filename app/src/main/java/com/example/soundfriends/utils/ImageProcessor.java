package com.example.soundfriends.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.soundfriends.R;
import com.google.firebase.database.annotations.Nullable;

public class ImageProcessor {
    public void Base64ToImageView(ImageView imageView, Context context, String base64Image){
        // Lấy chuỗi bitmap từ Firebase (giả sử 'model.getUrlImg()' chứa chuỗi bitmap)

        // Chuyển đổi chuỗi bitmap thành mảng byte
        byte[] imageBytes = Base64.decode(base64Image, Base64.DEFAULT);

        // Kiểm tra xem mảng byte có hợp lệ không
        if (imageBytes == null) {
            Toast.makeText(context, "Không thể xử lý chuỗi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển đổi mảng byte thành bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        // Bitmap đã tải xong, hiển thị nó bằng Glide
        Glide.with(context)
                .as(Bitmap.class)
                .load(bitmap)
                .placeholder(R.mipmap.ic_launcher_background)
                .error(R.drawable.empty_avatar)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        imageView.setImageBitmap(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // Xử lý khi tải bị xóa (nếu cần)
                    }
                });
    }
}
