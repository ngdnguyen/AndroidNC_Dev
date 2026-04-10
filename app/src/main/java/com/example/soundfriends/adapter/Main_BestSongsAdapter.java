package com.example.soundfriends.adapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.utils.ImageProcessor;
import com.google.firebase.database.annotations.Nullable;

import java.util.List;

public class Main_BestSongsAdapter extends RecyclerView.Adapter<Main_BestSongsAdapter.MainBestSongViewHolder> {
    private Context context;
    private List<Songs> listSong;

    public Main_BestSongsAdapter(Context context, List<Songs> listSong) {
        this.context = context;
        this.listSong = listSong;
    }

    @NonNull
    @Override
    public MainBestSongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_main_best_songs_adapter, parent, false);
        return new MainBestSongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MainBestSongViewHolder holder, int position) {
        Songs song = listSong.get(position);

        handleItemClick(holder, song);

        if (song == null){
            return;
        }

        ImageProcessor imageProcessor = new ImageProcessor();
        imageProcessor.Base64ToImageView(holder.imgSong, context, song.getUrlImg());
        holder.tvSongName.setText(song.title);
        holder.tvSinger.setText(song.artist);
    }

    @Override
    public int getItemCount() {
        if(listSong != null)
            return listSong.size();

        return 0;
    }

    public class MainBestSongViewHolder extends RecyclerView.ViewHolder{
        ImageView imgSong;
        TextView tvSongName, tvSinger;
        public MainBestSongViewHolder(@NonNull View itemView) {
            super(itemView);

            imgSong = itemView.findViewById(R.id.img_best_song);
            tvSongName = itemView.findViewById(R.id.tv_best_song_name);
            tvSinger = itemView.findViewById(R.id.tv_best_song_singer);
        }
    }
    private void handleItemClick(MainBestSongViewHolder holder, Songs model){
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Create an Intent to open the target Activity
                Intent intent = new Intent(context, Song.class);

                // Pass any necessary data to the SongActivity (e.g., selected item data)
                intent.putExtra("songId", model.getId());

                // Start the target Activity
                context.startActivity(intent);
            }
        });
    }
}