package com.example.soundfriends.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soundfriends.ProfileActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.utils.ImageProcessor;
import com.example.soundfriends.utils.ImageUtils;

import java.util.List;

public class SearchSongAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<?> itemList;

    private static final int TYPE_SONG = 0;
    private static final int TYPE_USER = 1;

    public SearchSongAdapter(Context context, List<?> itemList) {
        this.context = context;
        this.itemList = itemList;
    }

    @Override
    public int getItemViewType(int position) {
        if (itemList.get(position) instanceof Songs) {
            return TYPE_SONG;
        } else if (itemList.get(position) instanceof User) {
            return TYPE_USER;
        }
        return -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SONG) {
            View view = LayoutInflater.from(context).inflate(R.layout.activity_songs, parent, false);
            return new SongViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = itemList.get(position);
        if (holder instanceof SongViewHolder) {
            Songs song = (Songs) item;
            SongViewHolder songHolder = (SongViewHolder) holder;
            songHolder.title.setText(song.getTitle());
            songHolder.artist.setText(song.getArtist());
            songHolder.category.setText(song.getCategory());

            ImageProcessor imageProcessor = new ImageProcessor();
            imageProcessor.Base64ToImageView(songHolder.imageView, context, song.getUrlImg());

            songHolder.btnPopUp.setVisibility(View.GONE);

            songHolder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, Song.class);
                intent.putExtra("songId", song.getId());
                context.startActivity(intent);
            });
        } else if (holder instanceof UserViewHolder) {
            User user = (User) item;
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.tvName.setText(user.getName());
            userHolder.tvBio.setText(user.getBio() != null && !user.getBio().isEmpty() ? user.getBio() : "Người dùng SoundFriends");
            ImageUtils.loadAvatar(context, user.getAvatar(), userHolder.ivAvatar);

            userHolder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ProfileActivity.class);
                intent.putExtra("userID", user.getUserID());
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return itemList != null ? itemList.size() : 0;
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        public View btnPopUp;
        public ImageView imageView;
        public TextView title, artist, category;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.img2);
            title = itemView.findViewById(R.id.tv_song);
            artist = itemView.findViewById(R.id.tv_artist);
            category = itemView.findViewById(R.id.tv_category);
            btnPopUp = itemView.findViewById(R.id.btn_pop_up);
        }
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvBio;
        ImageView ivAvatar;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvBio = itemView.findViewById(R.id.tvUserBio);
            ivAvatar = itemView.findViewById(R.id.ivUserAvatar);
        }
    }
}
