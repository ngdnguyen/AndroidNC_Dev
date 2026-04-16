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

import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.utils.ImageProcessor;

import java.util.List;

public class SearchSongAdapter extends RecyclerView.Adapter<SearchSongAdapter.ViewHolder> {
    private Context context;
    private List<Songs> songsList;

    public SearchSongAdapter(Context context, List<Songs> songsList) {
        this.context = context;
        this.songsList = songsList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.activity_songs, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Songs song = songsList.get(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());
        holder.category.setText(song.getCategory());

        ImageProcessor imageProcessor = new ImageProcessor();
        imageProcessor.Base64ToImageView(holder.imageView, context, song.getUrlImg());

        holder.btnPopUp.setVisibility(View.GONE); // Hide popup button for search results if not needed

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, Song.class);
            intent.putExtra("songId", song.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return songsList != null ? songsList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View btnPopUp;
        public ImageView imageView;
        public TextView title, artist, category;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.img2);
            title = itemView.findViewById(R.id.tv_song);
            artist = itemView.findViewById(R.id.tv_artist);
            category = itemView.findViewById(R.id.tv_category);
            btnPopUp = itemView.findViewById(R.id.btn_pop_up);
        }
    }
}