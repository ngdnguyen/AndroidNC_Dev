package com.example.soundfriends.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soundfriends.ProfileActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.fragments.CommentsFragment;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.utils.ImageUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HomeFeedAdapter extends RecyclerView.Adapter<HomeFeedAdapter.ViewHolder> {

    private Context context;
    private List<Songs> songsList;
    private static MediaPlayer mediaPlayer;
    private static int playingPosition = -1;
    private Handler handler = new Handler();
    private Runnable updateSeekBarRunnable;

    public HomeFeedAdapter(Context context, List<Songs> songsList) {
        this.context = context;
        this.songsList = songsList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_home_feed, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Songs song = songsList.get(position);
        
        // Populate main user info (The person who is currently sharing this post)
        fetchUserInfo(song.getUserID(), holder);
        holder.tvPostContent.setText(song.getPostContent());
        holder.tvPostContent.setVisibility(song.getPostContent() != null && !song.getPostContent().isEmpty() ? View.VISIBLE : View.GONE);

        // HANDLE SHARED CONTENT (NESTING)
        if (song.isShared()) {
            holder.llSharedContainer.setVisibility(View.VISIBLE);
            holder.cvPlayerArea.setVisibility(View.GONE); // Hide external player
            
            holder.tvOriginalUserName.setText(song.getSharedFromUserName());
            holder.tvOriginalPostContent.setText(song.getSharedFromPostContent());
            ImageUtils.loadAvatar(context, song.getSharedFromUserAvatar(), holder.ivOriginalUserAvatar);
            
            holder.tvSharedSongTitle.setText(song.getTitle());
            holder.tvSharedArtist.setText(song.getArtist());
            if (song.getUrlImg() != null && !song.getUrlImg().isEmpty()) {
                try {
                    byte[] imageBytes = Base64.decode(song.getUrlImg(), Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    holder.ivSharedSongThumb.setImageBitmap(bitmap);
                } catch (Exception e) {
                    holder.ivSharedSongThumb.setImageResource(R.drawable.logo);
                }
            } else {
                holder.ivSharedSongThumb.setImageResource(R.drawable.logo);
            }
            
            holder.tvActionText.setText("đã chia sẻ một bài viết");
            
            // Setup shared player
            setupPlayerInternal(holder, position, true);
            
            // Set clicks for original user
            holder.ivOriginalUserAvatar.setOnClickListener(v -> navigateToProfile(song.getSharedFromUserID()));
            holder.tvOriginalUserName.setOnClickListener(v -> navigateToProfile(song.getSharedFromUserID()));
            
            if (holder.cvSharedPlayerArea != null) {
                holder.cvSharedPlayerArea.setOnClickListener(v -> openMusicPlayer(song.getId(), false));
            }
        } else {
            holder.llSharedContainer.setVisibility(View.GONE);
            holder.cvPlayerArea.setVisibility(View.VISIBLE);
            holder.tvActionText.setText("đã chia sẻ một bài hát");
            
            // Populate external player info
            holder.tvSongTitle.setText(song.getTitle());
            holder.tvArtist.setText(song.getArtist());
            if (song.getUrlImg() != null && !song.getUrlImg().isEmpty()) {
                try {
                    byte[] imageBytes = Base64.decode(song.getUrlImg(), Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    holder.ivSongThumb.setImageBitmap(bitmap);
                } catch (Exception e) {
                    holder.ivSongThumb.setImageResource(R.drawable.logo);
                }
            } else {
                holder.ivSongThumb.setImageResource(R.drawable.logo);
            }
            
            setupPlayerInternal(holder, position, false);
            holder.cvPlayerArea.setOnClickListener(v -> openMusicPlayer(song.getId(), false));
        }

        holder.ivUserAvatar.setOnClickListener(v -> navigateToProfile(song.getUserID()));
        holder.tvUserName.setOnClickListener(v -> navigateToProfile(song.getUserID()));

        checkIfLiked(song.getId(), holder.ivLike);
        updateCounts(song.getId(), holder);
        
        holder.llLike.setOnClickListener(v -> handleLike(song.getId(), holder.ivLike, holder.tvLikeCount));
        holder.llComment.setOnClickListener(v -> openMusicPlayer(song.getId(), true));
        holder.llShare.setOnClickListener(v -> showShareDialog(song));

        holder.btnMore.setOnClickListener(v -> showMoreOptions(v, song, holder.getBindingAdapterPosition()));
    }

    private void openMusicPlayer(String songId, boolean showComments) {
        if (songId == null) return;
        Intent intent = new Intent(context, Song.class);
        intent.putExtra("songId", songId);
        intent.putExtra("showComments", showComments);
        context.startActivity(intent);
    }

    private void showMoreOptions(View view, Songs song, int position) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        if (currentUser != null && song.getUserID() != null && song.getUserID().equals(currentUser.getUid())) {
            popupMenu.getMenu().add("Chỉnh sửa");
            popupMenu.getMenu().add("Xóa bài viết");
        } else {
            popupMenu.getMenu().add("Báo cáo");
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Chỉnh sửa")) {
                showEditDialog(song, position);
                return true;
            } else if (item.getTitle().equals("Xóa bài viết")) {
                showDeleteConfirmDialog(song, position);
                return true;
            } else if (item.getTitle().equals("Báo cáo")) {
                handleReport(song, position);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void handleReport(Songs song, int position) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || song.getId() == null) return;

        DatabaseReference reportsRef = FirebaseDatabase.getInstance().getReference("reports").child(song.getId());
        reportsRef.child(currentUser.getUid()).setValue(true).addOnSuccessListener(aVoid -> {
            Toast.makeText(context, "Đã gửi báo cáo", Toast.LENGTH_SHORT).show();
            
            // Check report count
            reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.getChildrenCount() > 3) {
                        // Auto-delete the post if reports > 3
                        FirebaseDatabase.getInstance().getReference("songs").child(song.getId()).removeValue()
                            .addOnSuccessListener(aVoid1 -> {
                                // Clean up reports
                                reportsRef.removeValue();
                                
                                // Remove from local list and update UI
                                if (position != -1 && position < songsList.size()) {
                                    songsList.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, songsList.size());
                                }
                                Toast.makeText(context, "Bài viết đã bị xóa do vi phạm tiêu chuẩn cộng đồng (nhiều báo cáo)", Toast.LENGTH_LONG).show();
                            });
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        });
    }

    private void showEditDialog(Songs song, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_post, null);
        EditText etContent = view.findViewById(R.id.etEditPostContent);
        Button btnSave = view.findViewById(R.id.btnSaveEdit);
        
        etContent.setText(song.getPostContent());
        AlertDialog dialog = builder.setView(view).create();
        
        btnSave.setOnClickListener(v -> {
            String newContent = etContent.getText().toString().trim();
            FirebaseDatabase.getInstance().getReference("songs").child(song.getId())
                    .child("postContent").setValue(newContent)
                    .addOnSuccessListener(aVoid -> {
                        song.setPostContent(newContent);
                        notifyItemChanged(position);
                        dialog.dismiss();
                        Toast.makeText(context, "Đã cập nhật", Toast.LENGTH_SHORT).show();
                    });
        });
        dialog.show();
    }

    private void showDeleteConfirmDialog(Songs song, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Xóa bài viết")
                .setMessage("Bạn có chắc chắn muốn xóa bài viết này không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    FirebaseDatabase.getInstance().getReference("songs").child(song.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                if (position != -1 && position < songsList.size()) {
                                    songsList.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, songsList.size());
                                }
                                Toast.makeText(context, "Đã xóa bài viết", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void updateCounts(String songId, ViewHolder holder) {
        if (songId == null) return;
        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("user_likes");
        likesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot userLikes : snapshot.getChildren()) {
                    if (userLikes.hasChild(songId)) count++;
                }
                holder.tvLikeCount.setText(count + " thích");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        DatabaseReference commentsRef = FirebaseDatabase.getInstance().getReference("comments");
        commentsRef.orderByChild("songId").equalTo(songId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                holder.tvCommentCount.setText(snapshot.getChildrenCount() + " bình luận");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        DatabaseReference sharesRef = FirebaseDatabase.getInstance().getReference("shares").child(songId);
        sharesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                holder.tvShareCount.setText(snapshot.getChildrenCount() + " chia sẻ");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupPlayerInternal(ViewHolder holder, int position, boolean isShared) {
        ImageButton playBtn = isShared ? holder.ivPlayPauseShared : holder.ivPlayPause;
        SeekBar seek = isShared ? holder.seekBarShared : holder.seekBar;
        TextView current = isShared ? holder.tvCurrentTimeShared : holder.tvCurrentTime;
        TextView total = isShared ? holder.tvTotalTimeShared : holder.tvTotalTime;

        if (playingPosition == position && mediaPlayer != null) {
            playBtn.setImageResource(mediaPlayer.isPlaying() ? R.drawable.pause : R.drawable.play);
            seek.setMax(mediaPlayer.getDuration());
            startSeekBarUpdate(holder, isShared);
        } else {
            playBtn.setImageResource(R.drawable.play);
            seek.setProgress(0);
        }

        playBtn.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (playingPosition == currentPos) {
                if (mediaPlayer != null) {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                        playBtn.setImageResource(R.drawable.play);
                        stopSeekBarUpdate();
                    } else {
                        mediaPlayer.start();
                        playBtn.setImageResource(R.drawable.pause);
                        startSeekBarUpdate(holder, isShared);
                    }
                }
            } else {
                startNewSong(currentPos);
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && playingPosition == holder.getBindingAdapterPosition()) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { stopSeekBarUpdate(); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { 
                if (mediaPlayer != null && mediaPlayer.isPlaying()) startSeekBarUpdate(holder, isShared); 
            }
        });
    }

    private void startNewSong(int newPosition) {
        int oldPosition = playingPosition;
        playingPosition = newPosition;
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; }
        stopSeekBarUpdate();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(songsList.get(playingPosition).getSrl());
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                notifyItemChanged(oldPosition);
                notifyItemChanged(playingPosition);
            });
            mediaPlayer.setOnCompletionListener(mp -> { playingPosition = -1; notifyDataSetChanged(); });
        } catch (IOException e) {
            Toast.makeText(context, "Không thể phát bài hát này", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSeekBarUpdate(ViewHolder holder, boolean isShared) {
        stopSeekBarUpdate();
        SeekBar seek = isShared ? holder.seekBarShared : holder.seekBar;
        TextView current = isShared ? holder.tvCurrentTimeShared : holder.tvCurrentTime;
        TextView total = isShared ? holder.tvTotalTimeShared : holder.tvTotalTime;

        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && playingPosition == holder.getBindingAdapterPosition()) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            int currentPos = mediaPlayer.getCurrentPosition();
                            seek.setProgress(currentPos);
                            current.setText(formatDuration(currentPos));
                            total.setText(formatDuration(mediaPlayer.getDuration()));
                        }
                    } catch (Exception ignored) {}
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateSeekBarRunnable);
    }

    private void stopSeekBarUpdate() { if (updateSeekBarRunnable != null) handler.removeCallbacks(updateSeekBarRunnable); }

    private String formatDuration(long durationMs) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void showShareDialog(Songs song) {
        if (!(context instanceof AppCompatActivity)) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_share_post, null);
        EditText etShareContent = view.findViewById(R.id.etShareContent);
        ImageView ivSongThumb = view.findViewById(R.id.ivShareSongThumb);
        TextView tvSongTitle = view.findViewById(R.id.tvShareSongTitle);
        TextView tvArtist = view.findViewById(R.id.tvShareArtist);
        Button btnConfirmShare = view.findViewById(R.id.btnConfirmShare);
        
        tvSongTitle.setText(song.getTitle());
        tvArtist.setText(song.getArtist());
        if (song.getUrlImg() != null && !song.getUrlImg().isEmpty()) {
            try {
                byte[] imageBytes = Base64.decode(song.getUrlImg(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                ivSongThumb.setImageBitmap(bitmap);
            } catch (Exception e) {
                ivSongThumb.setImageResource(R.drawable.logo);
            }
        } else {
            ivSongThumb.setImageResource(R.drawable.logo);
        }
        AlertDialog dialog = builder.setView(view).create();
        btnConfirmShare.setOnClickListener(v -> {
            handleShare(song, etShareContent.getText().toString().trim());
            dialog.dismiss();
        });
        dialog.show();
    }

    private void handleShare(Songs song, String extraContent) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                String userName = (user != null) ? user.getName() : (currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Người dùng");
                String userAvatar = (user != null) ? user.getAvatar() : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
                String shareId = FirebaseDatabase.getInstance().getReference("songs").push().getKey();
                String origUserID = song.isShared() ? song.getSharedFromUserID() : song.getUserID();
                String origUserName = song.isShared() ? song.getSharedFromUserName() : (song.getUserName() != null ? song.getUserName() : "Người dùng");
                String origUserAvatar = song.isShared() ? song.getSharedFromUserAvatar() : song.getUserAvatar();
                String origPostContent = song.isShared() ? song.getSharedFromPostContent() : song.getPostContent();
                Songs sharedPost = new Songs(shareId, song.getTitle(), song.getArtist(), song.getCategory(), 
                                            song.getUrlImg(), song.getSrl(), currentUser.getUid(), 
                                            userName, userAvatar, extraContent,
                                            origUserID, origUserName, origUserAvatar, origPostContent);
                FirebaseDatabase.getInstance().getReference("songs").child(shareId).setValue(sharedPost)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Đã chia sẻ lên trang cá nhân", Toast.LENGTH_SHORT).show();
                            FirebaseDatabase.getInstance().getReference("shares").child(song.getId()).child(currentUser.getUid()).setValue(true);
                        });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchUserInfo(String userId, ViewHolder holder) {
        if (userId == null) return;
        FirebaseDatabase.getInstance().getReference("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    holder.tvUserName.setText(user.getName());
                    ImageUtils.loadAvatar(context, user.getAvatar(), holder.ivUserAvatar);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void navigateToProfile(String userId) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra("userID", userId);
        context.startActivity(intent);
    }

    private void checkIfLiked(String songId, ImageView ivLike) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || songId == null) return;
        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("user_likes").child(user.getUid()).child(songId);
        likeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    ivLike.setImageResource(R.drawable.ic_like_selected);
                    ivLike.setColorFilter(ContextCompat.getColor(context, android.R.color.holo_red_dark));
                } else {
                    ivLike.setImageResource(R.drawable.ic_like_unselected);
                    ivLike.clearColorFilter();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void handleLike(String songId, ImageView ivLike, TextView tvLikeCount) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || songId == null) return;
        DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("user_likes").child(user.getUid()).child(songId);
        favRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    favRef.removeValue().addOnSuccessListener(aVoid -> updateLikeCountInSongNode(songId, -1));
                } else {
                    favRef.setValue(true).addOnSuccessListener(aVoid -> updateLikeCountInSongNode(songId, 1));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateLikeCountInSongNode(String id, int delta) {
        DatabaseReference songRef = FirebaseDatabase.getInstance().getReference("songs");
        songRef.orderByChild("id").equalTo(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    ds.getRef().child("likes").runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Long value = currentData.getValue(Long.class);
                            if (value == null) value = 0L;
                            currentData.setValue(Math.max(0, value + delta));
                            return Transaction.success(currentData);
                        }
                        @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override public int getItemCount() { return songsList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvSongTitle, tvArtist, tvPostContent, tvCurrentTime, tvTotalTime, tvActionText;
        TextView tvLikeCount, tvCommentCount, tvShareCount;
        TextView tvOriginalUserName, tvOriginalPostContent, tvSharedSongTitle, tvSharedArtist, tvCurrentTimeShared, tvTotalTimeShared;
        ImageView ivUserAvatar, ivSongThumb, ivLike, ivOriginalUserAvatar, ivSharedSongThumb;
        ImageButton ivPlayPause, ivPlayPauseShared, btnMore;
        SeekBar seekBar, seekBarShared;
        View llLike, llComment, llShare, llSharedContainer, cvPlayerArea, cvSharedPlayerArea;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvActionText = itemView.findViewById(R.id.tvActionText);
            tvPostContent = itemView.findViewById(R.id.tvPostContent);
            tvSongTitle = itemView.findViewById(R.id.tvSongTitle);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            tvCurrentTime = itemView.findViewById(R.id.tvCurrentTime);
            tvTotalTime = itemView.findViewById(R.id.tvTotalTime);
            ivUserAvatar = itemView.findViewById(R.id.ivUserAvatar);
            ivSongThumb = itemView.findViewById(R.id.ivSongThumb);
            ivPlayPause = itemView.findViewById(R.id.ivPlayPause);
            ivLike = itemView.findViewById(R.id.ivLike);
            seekBar = itemView.findViewById(R.id.seekBar);
            llLike = itemView.findViewById(R.id.llLike);
            llComment = itemView.findViewById(R.id.llComment);
            llShare = itemView.findViewById(R.id.llShare);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
            tvShareCount = itemView.findViewById(R.id.tvShareCount);
            btnMore = itemView.findViewById(R.id.btnMore);
            
            llSharedContainer = itemView.findViewById(R.id.llSharedContainer);
            cvPlayerArea = itemView.findViewById(R.id.cvPlayerArea);
            cvSharedPlayerArea = itemView.findViewById(R.id.cvSharedPlayerArea);
            tvOriginalUserName = itemView.findViewById(R.id.tvOriginalUserName);
            tvOriginalPostContent = itemView.findViewById(R.id.tvOriginalPostContent);
            ivOriginalUserAvatar = itemView.findViewById(R.id.ivOriginalUserAvatar);
            ivSharedSongThumb = itemView.findViewById(R.id.ivSharedSongThumb);
            tvSharedSongTitle = itemView.findViewById(R.id.tvSharedSongTitle);
            tvSharedArtist = itemView.findViewById(R.id.tvSharedArtist);
            ivPlayPauseShared = itemView.findViewById(R.id.ivPlayPauseShared);
            seekBarShared = itemView.findViewById(R.id.seekBarShared);
            tvCurrentTimeShared = itemView.findViewById(R.id.tvCurrentTimeShared);
            tvTotalTimeShared = itemView.findViewById(R.id.tvTotalTimeShared);
        }
    }
}
