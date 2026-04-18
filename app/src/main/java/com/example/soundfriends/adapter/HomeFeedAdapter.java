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

import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import com.example.soundfriends.utils.uuid;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

    // For editing posts
    private static final int EDIT_PICK_IMAGE_REQUEST = 201;
    private static final int EDIT_PICK_AUDIO_REQUEST = 202;
    private Uri editAudioUri;
    private Bitmap editSongBitmap;
    private MediaPlayer editPreviewPlayer;
    private boolean isEditPreviewPlaying = false;
    private final Handler editPreviewHandler = new Handler(Looper.getMainLooper());
    private MediaRecorder editMediaRecorder;
    private String editRecordFilePath;
    private boolean isEditRecording = false;
    private boolean isEditPaused = false;
    private final Handler editTimerHandler = new Handler(Looper.getMainLooper());
    private long editStartTime = 0L;
    private long editTimeInMilliseconds = 0L;
    private long editTimeSwapBuff = 0L;

    private ImageView currentEditIvThumb;
    private TextView currentEditTvFileName;
    private EditText currentEditEtTitle, currentEditEtArtist, currentEditEtCategory;
    private SeekBar currentEditSbPreview;
    private TextView currentEditTvCurrentTime, currentEditTvTotalTime;
    private ImageButton currentEditBtnPreviewPlay;
    private LinearLayout currentEditRecordingLayout;
    private TextView currentEditTvTimer;
    private ImageButton currentEditBtnPauseRecord;
    private ProgressBar currentEditPbUpload;

    private final Runnable editUpdateTimerThread = new Runnable() {
        public void run() {
            editTimeInMilliseconds = System.currentTimeMillis() - editStartTime;
            long updatedTime = editTimeSwapBuff + editTimeInMilliseconds;
            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            if (currentEditTvTimer != null)
                currentEditTvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
            editTimerHandler.postDelayed(this, 100);
        }
    };

    private final Runnable editUpdateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (editPreviewPlayer != null && isEditPreviewPlaying) {
                try {
                    int currentPos = editPreviewPlayer.getCurrentPosition();
                    if (currentEditSbPreview != null) {
                        currentEditSbPreview.setProgress(currentPos);
                        currentEditTvCurrentTime.setText(formatDuration(currentPos));
                    }
                    editPreviewHandler.postDelayed(this, 500);
                } catch (Exception e) {
                    editPreviewHandler.removeCallbacks(this);
                }
            }
        }
    };

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
        holder.tvPostTime.setText(getRelativeTime(song.getTimestamp()));
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

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(song.getTitle())
                .setArtist(song.getArtist());

        if (song.getUrlImg() != null && !song.getUrlImg().isEmpty()) {
            try {
                byte[] imageBytes = Base64.decode(song.getUrlImg(), Base64.DEFAULT);
                metadataBuilder.setArtworkData(imageBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
            } catch (Exception ignored) {}
        }

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(song.getId())
                .setUri(song.getSrl())
                .setMediaMetadata(metadataBuilder.build())
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
                    favRef.setValue(true).addOnSuccessListener(aVoid -> {
                        updateLikeCountInSongNode(songId, 1);
                        sendLikeNotification(songId);
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendLikeNotification(String songId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        DatabaseReference songRef = FirebaseDatabase.getInstance().getReference("songs").child(songId);
        songRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Songs song = snapshot.getValue(Songs.class);
                if (song != null && !song.getUserID().equals(currentUser.getUid())) {
                    DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
                    userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                            User user = userSnapshot.getValue(User.class);
                            String userName = (user != null) ? user.getName() : "Ai đó";
                            String userAvatar = (user != null) ? user.getAvatar() : "";

                            DatabaseReference notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(song.getUserID());
                            String notificationId = notificationRef.push().getKey();

                            Map<String, Object> notificationData = new HashMap<>();
                            notificationData.put("id", notificationId);
                            notificationData.put("fromUserId", currentUser.getUid());
                            notificationData.put("fromUserName", userName);
                            notificationData.put("fromUserAvatar", userAvatar);
                            notificationData.put("type", "like");
                            notificationData.put("message", "đã thích bài hát của bạn");
                            notificationData.put("songId", songId);
                            notificationData.put("timestamp", System.currentTimeMillis());
                            notificationData.put("isRead", false);

                            if (notificationId != null) {
                                notificationRef.child(notificationId).setValue(notificationData);
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
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
            if (item.getTitle().equals("Xóa bài viết")) {
                showDeleteConfirmDialog(song, position);
            } else if (item.getTitle().equals("Chỉnh sửa")) {
                showEditPostDialog(song, position);
            } else if (item.getTitle().equals("Báo cáo")) {
                handleReport(song, position);
            }
            return true;
        });
        popupMenu.show();
    }

    private void handleReport(Songs song, int position) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(context, "Vui lòng đăng nhập để báo cáo", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference reportRef = FirebaseDatabase.getInstance().getReference("reports").child(song.getId());
        reportRef.child(currentUser.getUid()).setValue(true).addOnSuccessListener(aVoid -> {
            reportRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    long reportCount = snapshot.getChildrenCount();
                    if (reportCount >= 3) {
                        deleteSongSilently(song, position);
                        Toast.makeText(context, "Bài viết đã bị xóa do vi phạm chính sách (nhiều báo cáo)", Toast.LENGTH_LONG).show();
                    } else {
                        sendReportNotification(song);
                        Toast.makeText(context, "Cảm ơn bạn đã báo cáo. Chúng tôi sẽ xem xét bài viết này.", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(context, "Lỗi khi báo cáo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteSongSilently(Songs song, int position) {
        // Send notification before deleting or during deletion
        sendDeletionNotification(song);

        // Remove from songs node
        FirebaseDatabase.getInstance().getReference("songs").child(song.getId()).removeValue();
        
        // Optionally remove from storage if it's a URL (optional, but good for cleanup)
        if (song.getSrl() != null && song.getSrl().startsWith("http")) {
            try {
                FirebaseStorage.getInstance().getReferenceFromUrl(song.getSrl()).delete();
            } catch (Exception ignored) {}
        }
        
        // Remove from reports
        FirebaseDatabase.getInstance().getReference("reports").child(song.getId()).removeValue();
        
        // Update UI
        songsList.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, songsList.size());
    }

    private void showEditPostDialog(Songs song, int position) {
        editAudioUri = null;
        editSongBitmap = null;
        stopEditPreview();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_post, null);

        EditText etEditPostContent = view.findViewById(R.id.etEditPostContent);
        currentEditEtTitle = view.findViewById(R.id.etEditSongTitle);
        currentEditEtArtist = view.findViewById(R.id.etEditArtist);
        currentEditEtCategory = view.findViewById(R.id.etEditCategory);
        currentEditIvThumb = view.findViewById(R.id.ivEditSongThumb);
        currentEditTvFileName = view.findViewById(R.id.tvEditFileName);
        currentEditBtnPreviewPlay = view.findViewById(R.id.btnEditPreviewPlay);
        currentEditSbPreview = view.findViewById(R.id.sbEditPreview);
        currentEditTvCurrentTime = view.findViewById(R.id.tvEditPreviewCurrentTime);
        currentEditTvTotalTime = view.findViewById(R.id.tvEditPreviewTotalTime);
        currentEditRecordingLayout = view.findViewById(R.id.edit_recording_layout);
        currentEditTvTimer = view.findViewById(R.id.tvEditTimer);
        currentEditBtnPauseRecord = view.findViewById(R.id.btnEditPauseRecord);
        currentEditPbUpload = view.findViewById(R.id.pbEditUpload);

        LinearLayout btnEditRecordMusic = view.findViewById(R.id.btnEditRecordMusic);
        ImageButton btnEditCancelRecord = view.findViewById(R.id.btnEditCancelRecord);
        ImageButton btnEditDoneRecord = view.findViewById(R.id.btnEditDoneRecord);
        Button btnSaveEdit = view.findViewById(R.id.btnSaveEdit);

        etEditPostContent.setText(song.getPostContent());
        currentEditEtTitle.setText(song.getTitle());
        currentEditEtArtist.setText(song.getArtist());
        currentEditEtCategory.setText(song.getCategory());
        loadSongImage(song.getUrlImg(), currentEditIvThumb);
        currentEditTvFileName.setText("Đang sử dụng file hiện tại");

        currentEditIvThumb.setOnClickListener(v -> chooseImage());

        btnEditRecordMusic.setOnClickListener(v -> {
            if (isEditRecording) stopEditRecording();
            else if (checkPermissions()) startEditRecording();
            else requestPermissions();
        });

        btnEditRecordMusic.setOnLongClickListener(v -> {
            chooseAudio();
            return true;
        });

        currentEditBtnPauseRecord.setOnClickListener(v -> {
            if (isEditRecording) {
                if (!isEditPaused) pauseEditRecording();
                else resumeEditRecording();
            }
        });

        btnEditCancelRecord.setOnClickListener(v -> cancelEditRecording());
        btnEditDoneRecord.setOnClickListener(v -> stopEditRecording());

        currentEditBtnPreviewPlay.setOnClickListener(v -> toggleEditPreview(song.getSrl()));

        currentEditSbPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && editPreviewPlayer != null) {
                    editPreviewPlayer.seekTo(progress);
                    currentEditTvCurrentTime.setText(formatDuration(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        AlertDialog dialog = builder.setView(view).create();
        dialog.setOnDismissListener(d -> {
            stopEditPreview();
            if (isEditRecording) cancelEditRecording();
        });

        btnSaveEdit.setOnClickListener(v -> {
            handleSaveEdit(song, position, etEditPostContent.getText().toString().trim(), dialog);
        });

        dialog.show();
    }

    private void handleSaveEdit(Songs song, int position, String newContent, AlertDialog dialog) {
        String newTitle = currentEditEtTitle.getText().toString().trim();
        String newArtist = currentEditEtArtist.getText().toString().trim();
        String newCategory = currentEditEtCategory.getText().toString().trim();

        if (newTitle.isEmpty()) {
            Toast.makeText(context, "Tên bài hát không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }

        currentEditPbUpload.setVisibility(View.VISIBLE);

        if (editAudioUri != null) {
            String extension = "3gp";
            String mimeType = context.getContentResolver().getType(editAudioUri);
            if (mimeType != null) extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

            StorageReference fileRef = FirebaseStorage.getInstance().getReference("songs").child(song.getId() + "." + extension);
            fileRef.putFile(editAudioUri)
                    .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        saveUpdatesToDatabase(song, position, newContent, newTitle, newArtist, newCategory, uri.toString(), dialog);
                    }))
                    .addOnFailureListener(e -> {
                        currentEditPbUpload.setVisibility(View.GONE);
                        Toast.makeText(context, "Lỗi khi tải file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            saveUpdatesToDatabase(song, position, newContent, newTitle, newArtist, newCategory, null, dialog);
        }
    }

    private void saveUpdatesToDatabase(Songs song, int position, String content, String title, String artist, String category, String audioUrl, AlertDialog dialog) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("postContent", content);
        updates.put("title", title);
        updates.put("artist", artist);
        updates.put("category", category);
        if (audioUrl != null) updates.put("srl", audioUrl);

        if (editSongBitmap != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            editSongBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            updates.put("urlImg", base64Image);
            song.setUrlImg(base64Image);
        }

        FirebaseDatabase.getInstance().getReference("songs").child(song.getId())
                .updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    if (currentEditPbUpload != null) currentEditPbUpload.setVisibility(View.GONE);
                    Toast.makeText(context, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
                    song.setPostContent(content);
                    song.setTitle(title);
                    song.setArtist(artist);
                    song.setCategory(category);
                    if (audioUrl != null) song.setSrl(audioUrl);
                    notifyItemChanged(position);
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    if (currentEditPbUpload != null) currentEditPbUpload.setVisibility(View.GONE);
                    Toast.makeText(context, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (context instanceof Activity) ((Activity) context).startActivityForResult(intent, EDIT_PICK_IMAGE_REQUEST);
    }

    private void chooseAudio() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        if (context instanceof Activity) ((Activity) context).startActivityForResult(intent, EDIT_PICK_AUDIO_REQUEST);
    }

    private void toggleEditPreview(String currentUrl) {
        if (isEditPreviewPlaying) pauseEditPreview();
        else startEditPreview(currentUrl);
    }

    private void startEditPreview(String currentUrl) {
        HomeFeedAdapter.stopAllMusic();
        UploadActivity.stopAllPreview();

        if (editPreviewPlayer == null) {
            editPreviewPlayer = new MediaPlayer();
            try {
                if (editAudioUri != null) editPreviewPlayer.setDataSource(context, editAudioUri);
                else if (currentUrl != null && !currentUrl.isEmpty()) editPreviewPlayer.setDataSource(currentUrl);
                else return;

                editPreviewPlayer.prepare();
                currentEditSbPreview.setMax(editPreviewPlayer.getDuration());
                currentEditTvTotalTime.setText(formatDuration(editPreviewPlayer.getDuration()));
                editPreviewPlayer.setOnCompletionListener(mp -> {
                    isEditPreviewPlaying = false;
                    currentEditBtnPreviewPlay.setImageResource(R.drawable.play);
                    currentEditSbPreview.setProgress(0);
                    currentEditTvCurrentTime.setText("00:00");
                    editPreviewHandler.removeCallbacks(editUpdateSeekBar);
                });
            } catch (IOException e) {
                Toast.makeText(context, "Không thể phát bản nhạc này", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        editPreviewPlayer.start();
        isEditPreviewPlaying = true;
        currentEditBtnPreviewPlay.setImageResource(R.drawable.pause);
        editPreviewHandler.post(editUpdateSeekBar);
    }

    private void pauseEditPreview() {
        if (editPreviewPlayer != null && editPreviewPlayer.isPlaying()) {
            editPreviewPlayer.pause();
        }
        isEditPreviewPlaying = false;
        if (currentEditBtnPreviewPlay != null) currentEditBtnPreviewPlay.setImageResource(R.drawable.play);
        editPreviewHandler.removeCallbacks(editUpdateSeekBar);
    }

    private void stopEditPreview() {
        if (editPreviewPlayer != null) {
            try {
                if (editPreviewPlayer.isPlaying()) editPreviewPlayer.stop();
                editPreviewPlayer.release();
            } catch (Exception ignored) {}
            editPreviewPlayer = null;
        }
        isEditPreviewPlaying = false;
        if (currentEditBtnPreviewPlay != null) currentEditBtnPreviewPlay.setImageResource(R.drawable.play);
        editPreviewHandler.removeCallbacks(editUpdateSeekBar);
        if (currentEditSbPreview != null) currentEditSbPreview.setProgress(0);
        if (currentEditTvCurrentTime != null) currentEditTvCurrentTime.setText("00:00");
    }

    private void startEditRecording() {
        editRecordFilePath = context.getExternalCacheDir().getAbsolutePath() + "/edit_recorded_audio.3gp";
        editMediaRecorder = new MediaRecorder();
        editMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        editMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        editMediaRecorder.setOutputFile(editRecordFilePath);
        editMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            stopEditPreview();
            HomeFeedAdapter.stopAllMusic();
            editMediaRecorder.prepare();
            editMediaRecorder.start();
            isEditRecording = true;
            isEditPaused = false;
            currentEditRecordingLayout.setVisibility(View.VISIBLE);
            editStartTime = System.currentTimeMillis();
            editTimeSwapBuff = 0L;
            editTimerHandler.postDelayed(editUpdateTimerThread, 0);
            currentEditBtnPauseRecord.setImageResource(R.drawable.ic_pause);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void stopEditRecording() {
        if (editMediaRecorder != null) {
            try { editMediaRecorder.stop(); } catch (RuntimeException ignored) {}
            editMediaRecorder.release();
            editMediaRecorder = null;
            isEditRecording = false;
            isEditPaused = false;
            editTimerHandler.removeCallbacks(editUpdateTimerThread);
            currentEditRecordingLayout.setVisibility(View.GONE);
            File recordedFile = new File(editRecordFilePath);
            editAudioUri = Uri.fromFile(recordedFile);
            currentEditTvFileName.setText("Bản ghi âm mới");
        }
    }

    private void pauseEditRecording() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && editMediaRecorder != null) {
            editMediaRecorder.pause();
            isEditPaused = true;
            editTimeSwapBuff += editTimeInMilliseconds;
            editTimerHandler.removeCallbacks(editUpdateTimerThread);
            currentEditBtnPauseRecord.setImageResource(R.drawable.play);
        }
    }

    private void resumeEditRecording() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && editMediaRecorder != null) {
            editMediaRecorder.resume();
            isEditPaused = false;
            editStartTime = System.currentTimeMillis();
            editTimerHandler.postDelayed(editUpdateTimerThread, 0);
            currentEditBtnPauseRecord.setImageResource(R.drawable.ic_pause);
        }
    }

    private void cancelEditRecording() {
        if (editMediaRecorder != null) {
            try { editMediaRecorder.stop(); } catch (RuntimeException ignored) {}
            editMediaRecorder.release();
            editMediaRecorder = null;
        }
        isEditRecording = false;
        isEditPaused = false;
        editTimerHandler.removeCallbacks(editUpdateTimerThread);
        currentEditRecordingLayout.setVisibility(View.GONE);
        if (editRecordFilePath != null) {
            File file = new File(editRecordFilePath);
            if (file.exists()) file.delete();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (context instanceof Activity) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
        }
    }

    private void extractMetadata(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            byte[] art = retriever.getEmbeddedPicture();

            if (title != null && !title.isEmpty() && currentEditEtTitle != null) currentEditEtTitle.setText(title);
            if (artist != null && !artist.isEmpty() && currentEditEtArtist != null) currentEditEtArtist.setText(artist);
            if (genre != null && !genre.isEmpty() && currentEditEtCategory != null) currentEditEtCategory.setText(genre);

            if (art != null) {
                editSongBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                if (currentEditIvThumb != null) currentEditIvThumb.setImageBitmap(editSongBitmap);
            }
        } catch (Exception e) { e.printStackTrace(); }
        finally { try { retriever.release(); } catch (IOException ignored) {} }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == EDIT_PICK_IMAGE_REQUEST) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                    editSongBitmap = BitmapFactory.decodeStream(inputStream);
                    if (currentEditIvThumb != null) currentEditIvThumb.setImageBitmap(editSongBitmap);
                } catch (IOException e) { e.printStackTrace(); }
            }
        } else if (requestCode == EDIT_PICK_AUDIO_REQUEST) {
            editAudioUri = data.getData();
            if (editAudioUri != null) {
                if (currentEditTvFileName != null) currentEditTvFileName.setText(getFileName(editAudioUri));
                extractMetadata(editAudioUri);
                stopEditPreview();
            }
        }
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

    private void sendReportNotification(Songs song) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || song.getUserID() == null || song.getUserID().equals(currentUser.getUid())) return;

        DatabaseReference notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(song.getUserID());
        String notificationId = notificationRef.push().getKey();

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("id", notificationId);
        notificationData.put("fromUserId", "system");
        notificationData.put("fromUserName", "Hệ thống");
        notificationData.put("fromUserAvatar", "");
        notificationData.put("type", "report");
        notificationData.put("message", "Bài hát '" + song.getTitle() + "' của bạn đã nhận được một báo cáo vi phạm nội dung.");
        notificationData.put("songId", song.getId());
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("isRead", false);

        if (notificationId != null) {
            notificationRef.child(notificationId).setValue(notificationData);
        }
    }

    private void sendDeletionNotification(Songs song) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || song.getUserID() == null) return;

        DatabaseReference notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(song.getUserID());
        String notificationId = notificationRef.push().getKey();

        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("id", notificationId);
        notificationData.put("fromUserId", "system");
        notificationData.put("fromUserName", "Hệ thống");
        notificationData.put("fromUserAvatar", "");
        notificationData.put("type", "delete_post");
        notificationData.put("message", "Bài hát '" + song.getTitle() + "' của bạn đã bị gỡ bỏ do vi phạm chính sách.");
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("isRead", false);

        if (notificationId != null) {
            notificationRef.child(notificationId).setValue(notificationData);
        }
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
                FirebaseDatabase.getInstance().getReference("songs").child(shareId).setValue(sharedPost).addOnSuccessListener(aVoid -> {
                    sendShareNotification(song);
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendShareNotification(Songs song) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || song.getUserID().equals(currentUser.getUid())) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                User user = userSnapshot.getValue(User.class);
                String userName = (user != null) ? user.getName() : "Ai đó";
                String userAvatar = (user != null) ? user.getAvatar() : "";

                DatabaseReference notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(song.getUserID());
                String notificationId = notificationRef.push().getKey();

                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("id", notificationId);
                notificationData.put("fromUserId", currentUser.getUid());
                notificationData.put("fromUserName", userName);
                notificationData.put("fromUserAvatar", userAvatar);
                notificationData.put("type", "share");
                notificationData.put("message", "đã chia sẻ bài hát của bạn");
                notificationData.put("songId", song.getId());
                notificationData.put("timestamp", System.currentTimeMillis());
                notificationData.put("isRead", false);

                if (notificationId != null) {
                    notificationRef.child(notificationId).setValue(notificationData);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String getRelativeTime(long timestamp) {
        if (timestamp <= 0) return "Gần đây";
        
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) return "Vừa xong";
        if (diff < 3600000) return (diff / 60000) + " phút trước";
        if (diff < 86400000) return (diff / 3600000) + " giờ trước";
        if (diff < 604800000) return (diff / 86400000) + " ngày trước";
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    @Override public int getItemCount() { return songsList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvSongTitle, tvArtist, tvPostContent, tvCurrentTime, tvTotalTime, tvActionText, tvPostTime;
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
            tvPostTime = itemView.findViewById(R.id.tvPostTime);
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
