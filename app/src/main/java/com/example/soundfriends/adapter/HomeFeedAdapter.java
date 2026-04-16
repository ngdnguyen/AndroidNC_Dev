package com.example.soundfriends.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
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
import androidx.recyclerview.widget.RecyclerView;

import com.example.soundfriends.ProfileActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.UploadActivity;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HomeFeedAdapter extends RecyclerView.Adapter<HomeFeedAdapter.ViewHolder> {

    private Context context;
    private List<Songs> songsList;
    private static MediaPlayer mediaPlayer;
    private static int playingPosition = -1;
    private Handler handler = new Handler();
    private Runnable updateSeekBarRunnable;

    // For Edit Song File
    private static final int PICK_SONG_REQUEST = 2001;
    private Uri selectedEditSongUri;
    private TextView tvEditFileNameRef;
    private String editingSongId;
    private int editingPosition;

    // Recording variables for Dialog
    private MediaRecorder mediaRecorder;
    private String recordFilePath;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private long timeInMilliseconds = 0L;
    private long timeSwapBuff = 0L;
    private long updatedTime = 0L;
    private TextView tvDialogTimer;
    private Runnable updateTimerThread;

    // Preview variables for Dialog
    private MediaPlayer dialogPreviewPlayer;
    private boolean isDialogPreviewPlaying = false;
    private Handler dialogPreviewHandler = new Handler();
    private Runnable updateDialogSeekBar;
    private SeekBar dialogSeekBar;
    private TextView tvDialogCurrentTime;

    public HomeFeedAdapter(Context context, List<Songs> songsList) {
        this.context = context;
        this.songsList = songsList;
    }

    public static void stopAllMusic() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
            playingPosition = -1;
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
                holder.cvSharedPlayerArea.setOnClickListener(v -> openMusicPlayer(song.getId(), false, holder.getBindingAdapterPosition()));
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
            holder.cvPlayerArea.setOnClickListener(v -> openMusicPlayer(song.getId(), false, holder.getBindingAdapterPosition()));
        }

        holder.ivUserAvatar.setOnClickListener(v -> navigateToProfile(song.getUserID()));
        holder.tvUserName.setOnClickListener(v -> navigateToProfile(song.getUserID()));

        checkIfLiked(song.getId(), holder.ivLike);
        updateCounts(song.getId(), holder);
        
        holder.llLike.setOnClickListener(v -> handleLike(song.getId(), holder.ivLike, holder.tvLikeCount));
        holder.llComment.setOnClickListener(v -> openMusicPlayer(song.getId(), true, holder.getBindingAdapterPosition()));
        holder.llShare.setOnClickListener(v -> showShareDialog(song));

        holder.btnMore.setOnClickListener(v -> showMoreOptions(v, song, holder.getBindingAdapterPosition()));
    }

    private void openMusicPlayer(String songId, boolean showComments, int position) {
        if (songId == null) return;
        
        // Cập nhật giao diện: Nếu bài đang phát ở trang chủ là bài này hoặc bài khác, hãy reset icon
        int oldPlayingPos = playingPosition;
        stopAllMusic();
        if (oldPlayingPos != -1) {
            notifyItemChanged(oldPlayingPos);
        }
        
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
        EditText etTitle = view.findViewById(R.id.etEditSongTitle);
        EditText etArtist = view.findViewById(R.id.etEditArtist);
        EditText etCategory = view.findViewById(R.id.etEditCategory);
        TextView tvFileName = view.findViewById(R.id.tvEditFileName);
        ImageView ivSongThumb = view.findViewById(R.id.ivEditSongThumb);
        CardView cvMusicInfo = view.findViewById(R.id.cvEditMusicInfo);
        
        LinearLayout btnRecordMusic = view.findViewById(R.id.btnEditRecordMusic);
        LinearLayout recordingLayout = view.findViewById(R.id.edit_recording_layout);
        tvDialogTimer = view.findViewById(R.id.tvEditTimer);
        ImageButton btnPauseRecord = view.findViewById(R.id.btnEditPauseRecord);
        ImageButton btnCancelRecord = view.findViewById(R.id.btnEditCancelRecord);
        ImageButton btnDoneRecord = view.findViewById(R.id.btnEditDoneRecord);
        
        ImageButton btnPreviewPlay = view.findViewById(R.id.btnEditPreviewPlay);
        dialogSeekBar = view.findViewById(R.id.sbEditPreview);
        tvDialogCurrentTime = view.findViewById(R.id.tvEditPreviewCurrentTime);
        TextView tvTotalTime = view.findViewById(R.id.tvEditPreviewTotalTime);
        
        ProgressBar pbUpload = view.findViewById(R.id.pbEditUpload);
        Button btnSave = view.findViewById(R.id.btnSaveEdit);

        // Pre-fill data
        etContent.setText(song.getPostContent());
        if (song.isShared()) {
            cvMusicInfo.setVisibility(View.VISIBLE);
            btnRecordMusic.setVisibility(View.GONE);
            etTitle.setText(song.getTitle());
            etArtist.setText(song.getArtist());
            etCategory.setText(song.getCategory());
            etTitle.setEnabled(false);
            etArtist.setEnabled(false);
            etCategory.setEnabled(false);
            tvFileName.setText("Bản nhạc đã chia sẻ");
        } else {
            cvMusicInfo.setVisibility(View.VISIBLE);
            etTitle.setText(song.getTitle());
            etArtist.setText(song.getArtist());
            etCategory.setText(song.getCategory());
            tvFileName.setText("File hiện tại");
        }

        if (song.getUrlImg() != null && !song.getUrlImg().isEmpty()) {
            try {
                byte[] imageBytes = Base64.decode(song.getUrlImg(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                ivSongThumb.setImageBitmap(bitmap);
            } catch (Exception e) {
                ivSongThumb.setImageResource(R.drawable.logo);
            }
        }

        editingSongId = song.getId();
        editingPosition = position;
        tvEditFileNameRef = tvFileName;
        selectedEditSongUri = null;

        // Setup Recording Logic for Dialog
        updateTimerThread = new Runnable() {
            public void run() {
                timeInMilliseconds = System.currentTimeMillis() - startTime;
                updatedTime = timeSwapBuff + timeInMilliseconds;
                int secs = (int) (updatedTime / 1000);
                int mins = secs / 60;
                secs = secs % 60;
                tvDialogTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
                timerHandler.postDelayed(this, 100);
            }
        };

        btnRecordMusic.setOnClickListener(v -> {
            if (isRecording) stopRecordingDialog(recordingLayout, btnRecordMusic, etTitle, etArtist, etCategory, ivSongThumb);
            else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecordingDialog(recordingLayout, btnRecordMusic, btnPauseRecord);
                } else {
                    ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
                }
            }
        });

        btnRecordMusic.setOnLongClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(intent, PICK_SONG_REQUEST);
            }
            return true;
        });

        btnPauseRecord.setOnClickListener(v -> {
            if (isRecording) {
                if (!isPaused) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        mediaRecorder.pause();
                        isPaused = true;
                        timeSwapBuff += timeInMilliseconds;
                        timerHandler.removeCallbacks(updateTimerThread);
                        btnPauseRecord.setImageResource(R.drawable.play);
                    }
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        mediaRecorder.resume();
                        isPaused = false;
                        startTime = System.currentTimeMillis();
                        timerHandler.postDelayed(updateTimerThread, 0);
                        btnPauseRecord.setImageResource(R.drawable.ic_pause);
                    }
                }
            }
        });

        btnCancelRecord.setOnClickListener(v -> cancelRecordingDialog(recordingLayout, btnRecordMusic));
        btnDoneRecord.setOnClickListener(v -> stopRecordingDialog(recordingLayout, btnRecordMusic, etTitle, etArtist, etCategory, ivSongThumb));

        // Preview Logic
        updateDialogSeekBar = new Runnable() {
            @Override
            public void run() {
                if (dialogPreviewPlayer != null && isDialogPreviewPlaying) {
                    try {
                        int currentPos = dialogPreviewPlayer.getCurrentPosition();
                        dialogSeekBar.setProgress(currentPos);
                        tvDialogCurrentTime.setText(formatDuration(currentPos));
                        dialogPreviewHandler.postDelayed(this, 500);
                    } catch (Exception e) {
                        dialogPreviewHandler.removeCallbacks(this);
                    }
                }
            }
        };

        btnPreviewPlay.setOnClickListener(v -> {
            if (selectedEditSongUri == null && (song.getSrl() == null || song.getSrl().isEmpty())) return;
            
            if (isDialogPreviewPlaying) {
                if (dialogPreviewPlayer != null) dialogPreviewPlayer.pause();
                isDialogPreviewPlaying = false;
                btnPreviewPlay.setImageResource(R.drawable.play);
                dialogPreviewHandler.removeCallbacks(updateDialogSeekBar);
            } else {
                stopAllMusic();
                UploadActivity.stopAllPreview();
                if (dialogPreviewPlayer != null) {
                    dialogPreviewPlayer.release();
                    dialogPreviewPlayer = null;
                }
                dialogPreviewPlayer = new MediaPlayer();
                try {
                    if (selectedEditSongUri != null) {
                        dialogPreviewPlayer.setDataSource(context, selectedEditSongUri);
                    } else {
                        dialogPreviewPlayer.setDataSource(song.getSrl());
                    }
                    dialogPreviewPlayer.prepare();
                    dialogPreviewPlayer.start();
                    isDialogPreviewPlaying = true;
                    btnPreviewPlay.setImageResource(R.drawable.pause);
                    dialogSeekBar.setMax(dialogPreviewPlayer.getDuration());
                    tvTotalTime.setText(formatDuration(dialogPreviewPlayer.getDuration()));
                    dialogPreviewHandler.post(updateDialogSeekBar);
                    dialogPreviewPlayer.setOnCompletionListener(mp -> {
                        isDialogPreviewPlaying = false;
                        btnPreviewPlay.setImageResource(R.drawable.play);
                        dialogSeekBar.setProgress(0);
                        tvDialogCurrentTime.setText("00:00");
                        dialogPreviewHandler.removeCallbacks(updateDialogSeekBar);
                    });
                } catch (IOException e) {
                    Toast.makeText(context, "Không thể phát bản nhạc này", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialogSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && dialogPreviewPlayer != null) {
                    dialogPreviewPlayer.seekTo(progress);
                    tvDialogCurrentTime.setText(formatDuration(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        AlertDialog dialog = builder.setView(view).create();
        
        btnSave.setOnClickListener(v -> {
            String newContent = etContent.getText().toString().trim();
            String newTitle = etTitle.getText().toString().trim();
            String newArtist = etArtist.getText().toString().trim();
            String newCategory = etCategory.getText().toString().trim();

            if (newTitle.isEmpty()) {
                Toast.makeText(context, "Tên bài hát không được để trống", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSave.setEnabled(false);
            if (selectedEditSongUri != null) {
                pbUpload.setVisibility(View.VISIBLE);
                StorageReference storageRef = FirebaseStorage.getInstance().getReference("songs").child(System.currentTimeMillis() + ".mp3");
                storageRef.putFile(selectedEditSongUri).addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateSongData(song, position, newContent, newTitle, newArtist, newCategory, uri.toString(), dialog);
                    });
                }).addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    pbUpload.setVisibility(View.GONE);
                    Toast.makeText(context, "Lỗi tải lên file", Toast.LENGTH_SHORT).show();
                });
            } else {
                updateSongData(song, position, newContent, newTitle, newArtist, newCategory, null, dialog);
            }
        });

        dialog.setOnDismissListener(d -> {
            if (isRecording) cancelRecordingDialog(recordingLayout, btnRecordMusic);
            if (dialogPreviewPlayer != null) {
                dialogPreviewPlayer.release();
                dialogPreviewPlayer = null;
            }
            isDialogPreviewPlaying = false;
            dialogPreviewHandler.removeCallbacks(updateDialogSeekBar);
        });

        dialog.show();
    }

    private void startRecordingDialog(LinearLayout recordingLayout, View btnRecordMusic, ImageButton btnPauseRecord) {
        recordFilePath = context.getExternalCacheDir().getAbsolutePath() + "/edit_recorded_audio.3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(recordFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            stopAllMusic();
            UploadActivity.stopAllPreview();
            if (dialogPreviewPlayer != null && dialogPreviewPlayer.isPlaying()) {
                dialogPreviewPlayer.stop();
                dialogPreviewPlayer.release();
                dialogPreviewPlayer = null;
                isDialogPreviewPlaying = false;
            }

            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            isPaused = false;
            recordingLayout.setVisibility(View.VISIBLE);
            btnRecordMusic.setVisibility(View.GONE);
            startTime = System.currentTimeMillis();
            timeSwapBuff = 0L;
            timerHandler.postDelayed(updateTimerThread, 0);
            btnPauseRecord.setImageResource(R.drawable.ic_pause);
        } catch (IOException e) {
            Log.e("HomeFeedAdapter", "prepare() failed");
        }
    }

    private void stopRecordingDialog(LinearLayout recordingLayout, View btnRecordMusic, EditText etTitle, EditText etArtist, EditText etCategory, ImageView ivThumb) {
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (RuntimeException ignored) {}
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            isPaused = false;
            timerHandler.removeCallbacks(updateTimerThread);
            recordingLayout.setVisibility(View.GONE);
            btnRecordMusic.setVisibility(View.VISIBLE);
            File recordedFile = new File(recordFilePath);
            selectedEditSongUri = Uri.fromFile(recordedFile);
            if (tvEditFileNameRef != null) tvEditFileNameRef.setText("Bản ghi âm mới");
            etTitle.setText("Ghi âm " + System.currentTimeMillis());
            etCategory.setText("Âm nhạc");
            ivThumb.setImageResource(R.drawable.round_mic_24);
        }
    }

    private void cancelRecordingDialog(LinearLayout recordingLayout, View btnRecordMusic) {
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (RuntimeException ignored) {}
            mediaRecorder.release();
            mediaRecorder = null;
        }
        isRecording = false;
        isPaused = false;
        timerHandler.removeCallbacks(updateTimerThread);
        recordingLayout.setVisibility(View.GONE);
        btnRecordMusic.setVisibility(View.VISIBLE);
        File file = new File(recordFilePath);
        if (file.exists()) file.delete();
    }

    private void updateSongData(Songs song, int position, String content, String title, String artist, String category, String newUrl, AlertDialog dialog) {
        DatabaseReference songRef = FirebaseDatabase.getInstance().getReference("songs").child(song.getId());
        Map<String, Object> updates = new HashMap<>();
        updates.put("postContent", content);
        updates.put("title", title);
        updates.put("artist", artist);
        updates.put("category", category);
        if (newUrl != null) updates.put("srl", newUrl);

        songRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
            song.setPostContent(content);
            song.setTitle(title);
            song.setArtist(artist);
            song.setCategory(category);
            if (newUrl != null) song.setSrl(newUrl);
            
            notifyItemChanged(position);
            dialog.dismiss();
            Toast.makeText(context, "Đã cập nhật bài viết", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(context, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
            dialog.findViewById(R.id.btnSaveEdit).setEnabled(true);
        });
    }

    // This method should be called from Activity's onActivityResult
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_SONG_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedEditSongUri = data.getData();
            if (tvEditFileNameRef != null) {
                String fileName = getFileName(selectedEditSongUri);
                tvEditFileNameRef.setText(fileName);
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
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
                        // Dừng các nguồn nhạc khác trước khi phát tiếp
                        Song.stopAllMusic();
                        UploadActivity.stopAllPreview();
                        
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
        // Dừng các nguồn nhạc khác
        Song.stopAllMusic();
        UploadActivity.stopAllPreview();

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
            mediaPlayer.setOnCompletionListener(mp -> { 
                int completedPos = playingPosition;
                playingPosition = -1; 
                if (completedPos != -1) notifyItemChanged(completedPos);
            });
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
