package com.example.soundfriends.adapter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.utils.ImageProcessor;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.annotations.Nullable;
import android.util.Base64;
import android.widget.Toast;


public class UploadSongs extends FirebaseRecyclerAdapter<Songs, UploadSongs.myViewHolder> {

    /**
     * Initialize a {@link RecyclerView.Adapter} that listens to a Firebase query. See
     * {@link FirebaseRecyclerOptions} for configuration options.
     *
     * @param options
     */


    private Context context;
    public UploadSongs(@NonNull FirebaseRecyclerOptions<Songs> options) {
        super(options);

    }

    @Override
    protected void onBindViewHolder(@NonNull myViewHolder holder, int position, @NonNull Songs model) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String userIDLogin = currentUser.getUid();

        onClickHolder(holder, model);

        if (model.getUserID().equals(userIDLogin)) {
            holder.title.setText(model.getTitle());
            holder.artist.setText(model.getArtist());
            holder.category.setText(model.getCategory());

            ImageProcessor imageProcessor = new ImageProcessor();
            imageProcessor.Base64ToImageView(holder.imageView, holder.imageView.getContext(), model.getUrlImg());
        } else {
            holder.itemView.setVisibility(View.GONE);
        }
    }




    @NonNull
    @Override
    public myViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_songs, parent, false);
        return new myViewHolder(view);
    }

    class myViewHolder extends RecyclerView.ViewHolder {
        ImageButton btnPopUp;
        ImageView imageView;

        TextView title, artist, category, tvsrl;
        MediaPlayer mediaPlayer;

        public myViewHolder(@NonNull View itemView) {
            super(itemView);

            imageView = (ImageView) itemView.findViewById(R.id.img2);
            title = (TextView) itemView.findViewById(R.id.tv_song);
            artist = (TextView) itemView.findViewById(R.id.tv_artist);
            category = (TextView) itemView.findViewById(R.id.tv_category);
            btnPopUp = (ImageButton) itemView.findViewById(R.id.btn_pop_up);
            tvsrl = (TextView) itemView.findViewById(R.id.tvsrl);
        }
    }

    private void onClickHolder(myViewHolder holder, Songs model) {

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
        holder.btnPopUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DatabaseReference songRef = getRef(holder.getAbsoluteAdapterPosition());
                String songRefKey = songRef.getKey();

                //show pop up menu
                showPopUpMenu(view, model, songRefKey);
            }
        });
    }
    private void showPopUpMenu(View view, Songs song, String songRefKey){
        PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
        popupMenu.inflate(R.menu.uploaded_songs_popup_menu);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.song_delete_popup){
                    AlertDialog.Builder alertMergeAccounts = new AlertDialog.Builder(context);
                    alertMergeAccounts.setTitle("Thông báo");
                    alertMergeAccounts.setMessage("Bạn có chắc chắn muốn xoá bài hát " + song.title + "?");
                    alertMergeAccounts.setIcon(R.mipmap.ic_launcher_round);
                    alertMergeAccounts.setPositiveButton("Xoá", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            deleteSong(songRefKey);
                        }
                    });
                    alertMergeAccounts.setNegativeButton("Huỷ", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    alertMergeAccounts.show();

                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    private void deleteSong(String songRefKey) {
        DatabaseReference songRef = FirebaseDatabase.getInstance().getReference().child("songs").child(songRefKey);
        songRef.removeValue();

        notifyDataSetChanged();
    }
}

