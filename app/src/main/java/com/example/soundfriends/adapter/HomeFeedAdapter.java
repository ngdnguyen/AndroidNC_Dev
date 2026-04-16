package com.example.soundfriends.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soundfriends.ProfileActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.UploadActivity;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.services.MusicService;
import com.example.soundfriends.utils.ImageUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class HomeFeedAdapter extends RecyclerView.Adapter<HomeFeedAdapter.ViewHolder> {

    private Context context;
    private List<Songs> songsList;
    
    private static MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private static String currentPlayingSongId = "";
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBarRunnable;
    private boolean isUserSeeking = false;
    private RecyclerView recyclerView;

    public HomeFeedAdapter(Context context, List<Songs> songsList) {
        this.context = context;
        this.songsList = songsList;
        initializeController();
    }

    private void initializeController() {
        SessionToken sessionToken = new SessionToken(context, new ComponentName(context, MusicService.class));
        controllerFuture = new MediaController.Builder(context, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        updatePlayPauseIcons();
                    }
                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        if (mediaItem != null && mediaItem.mediaId != null) {
                            currentPlayingSongId = mediaItem.mediaId;
                        }
                        updatePlayPauseIcons();
                    }
                });
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    private void updatePlayPauseIcons() {
        if (recyclerView == null) return;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder rb = recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
            if (rb instanceof ViewHolder) {
                ViewHolder holder = (ViewHolder) rb;
                String songId = (String) holder.itemView.getTag();
                if (songId != null) {
                    updateItemUI(holder, songId);
                }
            }
        }
    }

    private void updateItemUI(ViewHolder holder, String songId) {
        boolean isPlayingThis = songId.equals(currentPlayingSongId);
        boolean isShared = holder.llSharedContainer.getVisibility() == View.VISIBLE;
        ImageButton playBtn = isShared ? holder.ivPlayPauseShared : holder.ivPlayPause;
        SeekBar seek = isShared ? holder.seekBarShared : holder.seekBar;
        TextView current = isShared ? holder.tvCurrentTimeShared : holder.tvCurrentTime;
        TextView total = isShared ? holder.tvTotalTimeShared : holder.tvTotalTime;

        if (isPlayingThis && mediaController != null) {
            playBtn.setImageResource(mediaController.isPlaying() ? R.drawable.pause : R.drawable.play);
            long duration = mediaController.getDuration();
            long currentPos = mediaController.getCurrentPosition();
            
            if (duration > 0) {
                seek.setMax((int) duration);
                if (!isUserSeeking) {
                    seek.setProgress((int) currentPos);
                    current.setText(formatDuration(currentPos));
                    total.setText(formatDuration(duration));
                }
            }
            startSeekBarUpdate(holder, isShared, songId);
        } else {
            playBtn.setImageResource(R.drawable.play);
            if (!isUserSeeking) {
                seek.setProgress(0);
                current.setText("00:00");
            }
        }
    }

    public static void stopAllMusic() {
        if (mediaController != null) {
            mediaController.pause();
            currentPlayingSongId = "";
        }
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
        holder.itemView.setTag(song.getId());

        fetchUserInfo(song.getUserID(), holder);
        holder.tvPostContent.setText(song.getPostContent());
        holder.tvPostContent.setVisibility(song.getPostContent() != null && !song.getPostContent().isEmpty() ? View.VISIBLE : View.GONE);

        if (song.isShared()) {
            holder.llSharedContainer.setVisibility(View.VISIBLE);
            holder.cvPlayerArea.setVisibility(View.GONE);
            holder.tvOriginalUserName.setText(song.getSharedFromUserName());
            holder.tvOriginalPostContent.setText(song.getSharedFromPostContent());
            ImageUtils.loadAvatar(context, song.getSharedFromUserAvatar(), holder.ivOriginalUserAvatar);
            holder.tvSharedSongTitle.setText(song.getTitle());
            holder.tvSharedArtist.setText(song.getArtist());
            loadSongImage(song.getUrlImg(), holder.ivSharedSongThumb);
            holder.tvActionText.setText("đã chia sẻ một bài viết");
            setupPlayerInternal(holder, song, true);
            
            View.OnClickListener openPlayer = v -> openMusicPlayer(song.getId());
            holder.llOriginalHeader.setOnClickListener(openPlayer);
            holder.tvOriginalPostContent.setOnClickListener(openPlayer);
            holder.ivSharedSongThumb.setOnClickListener(openPlayer);
            holder.tvSharedSongTitle.setOnClickListener(openPlayer);
            holder.tvSharedArtist.setOnClickListener(openPlayer);
        } else {
            holder.llSharedContainer.setVisibility(View.GONE);
            holder.cvPlayerArea.setVisibility(View.VISIBLE);
            holder.tvActionText.setText("đã chia sẻ một bài hát");
            holder.tvSongTitle.setText(song.getTitle());
            holder.tvArtist.setText(song.getArtist());
            loadSongImage(song.getUrlImg(), holder.ivSongThumb);
            setupPlayerInternal(holder, song, false);

            View.OnClickListener openPlayer = v -> openMusicPlayer(song.getId());
            holder.ivSongThumb.setOnClickListener(openPlayer);
            holder.tvSongTitle.setOnClickListener(openPlayer);
            holder.tvArtist.setOnClickListener(openPlayer);
            holder.cvPlayerArea.setOnClickListener(null);
        }

        holder.ivUserAvatar.setOnClickListener(v -> navigateToProfile(song.getUserID()));
        holder.tvUserName.setOnClickListener(v -> navigateToProfile(song.getUserID()));
        checkIfLiked(song.getId(), holder.ivLike);
        updateCounts(song.getId(), holder);
        holder.llLike.setOnClickListener(v -> handleLike(song.getId(), holder.ivLike, holder.tvLikeCount));
        holder.llComment.setOnClickListener(v -> openMusicPlayer(song.getId()));
        holder.llShare.setOnClickListener(v -> showShareDialog(song));
        holder.btnMore.setOnClickListener(v -> showMoreOptions(v, song, holder.getBindingAdapterPosition()));
    }

    private void setupPlayerInternal(ViewHolder holder, Songs song, boolean isShared) {
        ImageButton playBtn = isShared ? holder.ivPlayPauseShared : holder.ivPlayPause;
        SeekBar seek = isShared ? holder.seekBarShared : holder.seekBar;
        TextView current = isShared ? holder.tvCurrentTimeShared : holder.tvCurrentTime;
        TextView total = isShared ? holder.tvTotalTimeShared : holder.tvTotalTime;

        updateItemUI(holder, song.getId());

        playBtn.setOnClickListener(v -> {
            if (mediaController == null) return;
            if (song.getId().equals(currentPlayingSongId)) {
                if (mediaController.isPlaying()) mediaController.pause();
                else mediaController.play();
            } else {
                startNewSong(song);
            }
        });

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null && song.getId().equals(currentPlayingSongId)) {
                    current.setText(formatDuration(progress));
                    mediaController.seekTo(progress); // Kéo mượt như Song.java
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isUserSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { isUserSeeking = false; }
        });
    }

    private void startNewSong(Songs song) {
        if (mediaController == null || song.getSrl() == null) return;
        MediaItem currentItem = mediaController.getCurrentMediaItem();
        if (currentItem != null && song.getId().equals(currentItem.mediaId)) {
            if (!mediaController.isPlaying()) mediaController.play();
            currentPlayingSongId = song.getId();
            updatePlayPauseIcons();
            return;
        }
        currentPlayingSongId = song.getId();
        UploadActivity.stopAllPreview();
        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(song.getId()).setUri(song.getSrl())
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(song.getTitle()).setArtist(song.getArtist()).build())
                .build();
        mediaController.setMediaItem(mediaItem);
        mediaController.prepare();
        mediaController.play();
        updatePlayPauseIcons();
    }

    private void startSeekBarUpdate(ViewHolder holder, boolean isShared, String songId) {
        if (updateSeekBarRunnable != null) handler.removeCallbacks(updateSeekBarRunnable);
        SeekBar seek = isShared ? holder.seekBarShared : holder.seekBar;
        TextView current = isShared ? holder.tvCurrentTimeShared : holder.tvCurrentTime;
        TextView total = isShared ? holder.tvTotalTimeShared : holder.tvTotalTime;

        updateSeekBarRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaController != null && songId.equals(currentPlayingSongId) && songId.equals(holder.itemView.getTag())) {
                    long duration = mediaController.getDuration();
                    long currentPos = mediaController.getCurrentPosition();
                    if (duration > 0) {
                        seek.setMax((int) duration);
                        if (!isUserSeeking) {
                            seek.setProgress((int) currentPos);
                            current.setText(formatDuration(currentPos));
                            total.setText(formatDuration(duration));
                        }
                    }
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateSeekBarRunnable);
    }

    private String formatDuration(long durationMs) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void loadSongImage(String base64, ImageView imageView) {
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                imageView.setImageBitmap(bitmap);
            } catch (Exception e) { imageView.setImageResource(R.drawable.logo); }
        } else { imageView.setImageResource(R.drawable.logo); }
    }

    private void openMusicPlayer(String songId) {
        if (songId == null) return;
        Intent intent = new Intent(context, Song.class);
        intent.putExtra("songId", songId);
        context.startActivity(intent);
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

    private void showMoreOptions(View view, Songs song, int position) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && song.getUserID() != null && song.getUserID().equals(currentUser.getUid())) {
            popupMenu.getMenu().add("Chỉnh sửa");
            popupMenu.getMenu().add("Xóa bài viết");
        } else { popupMenu.getMenu().add("Báo cáo"); }
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Xóa bài viết")) showDeleteConfirmDialog(song, position);
            return true;
        });
        popupMenu.show();
    }

    private void showDeleteConfirmDialog(Songs song, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Xóa bài viết").setMessage("Bạn có chắc chắn muốn xóa bài viết này không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    FirebaseDatabase.getInstance().getReference("songs").child(song.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                songsList.remove(position);
                                notifyItemRemoved(position);
                            });
                }).setNegativeButton("Hủy", null).show();
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
        loadSongImage(song.getUrlImg(), ivSongThumb);
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
                String userName = (user != null) ? user.getName() : "Người dùng";
                String userAvatar = (user != null) ? user.getAvatar() : "";
                String shareId = FirebaseDatabase.getInstance().getReference("songs").push().getKey();
                Songs sharedPost = new Songs(shareId, song.getTitle(), song.getArtist(), song.getCategory(), 
                                            song.getUrlImg(), song.getSrl(), currentUser.getUid(), 
                                            userName, userAvatar, extraContent,
                                            song.getUserID(), song.getUserName(), song.getUserAvatar(), song.getPostContent());
                FirebaseDatabase.getInstance().getReference("songs").child(shareId).setValue(sharedPost);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {}

    @Override public int getItemCount() { return songsList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvSongTitle, tvArtist, tvPostContent, tvCurrentTime, tvTotalTime, tvActionText;
        TextView tvLikeCount, tvCommentCount, tvShareCount;
        TextView tvOriginalUserName, tvOriginalPostContent, tvSharedSongTitle, tvSharedArtist, tvCurrentTimeShared, tvTotalTimeShared;
        ImageView ivUserAvatar, ivSongThumb, ivLike, ivOriginalUserAvatar, ivSharedSongThumb;
        ImageButton ivPlayPause, ivPlayPauseShared, btnMore;
        SeekBar seekBar, seekBarShared;
        View llLike, llComment, llShare, llSharedContainer, cvPlayerArea, cvSharedPlayerArea, llOriginalHeader;

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
            llOriginalHeader = itemView.findViewById(R.id.llOriginalHeader);
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
