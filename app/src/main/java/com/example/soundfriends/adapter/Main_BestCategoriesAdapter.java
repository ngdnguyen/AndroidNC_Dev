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

public class Main_BestCategoriesAdapter extends RecyclerView.Adapter<Main_BestCategoriesAdapter.MainBestCategoryViewHolder>{
    private Context context;
    private List<Songs> listSong;

    public Main_BestCategoriesAdapter(Context context, List<Songs> listSong) {
        this.context = context;
        this.listSong = listSong;
    }
    @NonNull
    @Override
    public MainBestCategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_main_best_categories_adapter, parent, false);
        return new Main_BestCategoriesAdapter.MainBestCategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MainBestCategoryViewHolder holder, int position) {
        Songs song = listSong.get(position);

        if (song == null){
            return;
        }

        ImageProcessor imageProcessor = new ImageProcessor();
        imageProcessor.Base64ToImageView(holder.imgCategory, context, song.getUrlImg());
        holder.tvCategory.setText(song.getCategory());

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ListSongsActivity.class);
            intent.putExtra("type", "category");
            intent.putExtra("value", song.getCategory());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        if(listSong != null)
            return listSong.size();
        return 0;
    }

    public class MainBestCategoryViewHolder extends RecyclerView.ViewHolder{
        ImageView imgCategory;
        TextView tvCategory;
        public MainBestCategoryViewHolder(@NonNull View itemView) {
            super(itemView);

            imgCategory = itemView.findViewById(R.id.img_best_category);
            tvCategory = itemView.findViewById(R.id.tv_best_category);
        }
    }
}