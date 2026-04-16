package com.example.soundfriends.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.soundfriends.ListSongsActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.utils.ImageProcessor;

import java.util.List;

public class Main_BestSingersAdapter extends RecyclerView.Adapter<Main_BestSingersAdapter.MainBestSingerViewHolder> {
    private Context context;
    private List<Songs> listSong;

    public Main_BestSingersAdapter(Context context, List<Songs> listSong) {
        this.context = context;
        this.listSong = listSong;
    }

    @NonNull
    @Override
    public Main_BestSingersAdapter.MainBestSingerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_main_best_singers_adapter, parent, false);
        return new Main_BestSingersAdapter.MainBestSingerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Main_BestSingersAdapter.MainBestSingerViewHolder holder, int position) {
        Songs song = listSong.get(position);

        if (song == null){
            return;
        }
        
        holder.tvSingerRank.setText(String.valueOf(position + 1));
        ImageProcessor imageProcessor = new ImageProcessor();
        imageProcessor.Base64ToImageView(holder.imgSinger, context, song.getUrlImg());
        holder.tvSingerName.setText(song.artist);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ListSongsActivity.class);
            intent.putExtra("type", "artist");
            intent.putExtra("value", song.getArtist());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        if(listSong != null)
            return listSong.size();
        return 0;
    }

    public class MainBestSingerViewHolder extends RecyclerView.ViewHolder {
        ImageView imgSinger;
        TextView tvSingerRank, tvSingerName;
        public MainBestSingerViewHolder(@NonNull View itemView) {
            super(itemView);

            imgSinger = itemView.findViewById(R.id.img_best_singer);
            tvSingerRank = itemView.findViewById(R.id.tv_singer_ranking);
            tvSingerName = itemView.findViewById(R.id.tv_best_singer_name);
        }
    }
}