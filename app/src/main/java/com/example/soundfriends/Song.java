package com.example.soundfriends;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.example.soundfriends.adapter.HomeFeedAdapter;
import com.example.soundfriends.fragments.CommentsFragment;
import com.example.soundfriends.fragments.Model.Songs;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Song extends AppCompatActivity implements SensorEventListener {
    boolean isPlaying = false;
    boolean isDirty = false;
    private static MediaPlayer mediaPlayer;
    private SeekBar seekBar;
    private final Handler handler = new Handler();
    private int currentPosition;
    private String audioURL;

    private String songId = "";
    private ImageView imageView, likeIcon, btnDownload, btnFavorite;
    private TextView textLike, tvSongStatus;
    private NestedScrollView songScrollView;
    private View commentContainer;
    int songIndex;
    long songCount;
    private List<DataSnapshot> songSnapshots = new ArrayList<>();
    ImageButton next, previous, play;

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private boolean isShakeEnabled = false;
    private long lastShakeTime = 0;
    private boolean isChangingSong = false;
    private boolean autoPlayFirstTime = true; // Flag để tự động phát lần đầu

    ImageButton loopBtn, shuffle;
    private boolean isLoop, isShuffling = false;
    private boolean isLiked = false;
    private boolean isFavorited = false;

    private String currentSongTitle, currentSongArtist, currentSongImgUrl, currentSongCategory, currentSongUserID;

    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        currentPosition = mediaPlayer.getCurrentPosition();
                        TextView txtCurrentDuration = findViewById(R.id.txtCurrentDuration);
                        TextView txtDuration = findViewById(R.id.txtDuration);
                        if (txtCurrentDuration != null) {
                            txtCurrentDuration.setText(formatDuration(currentPosition));
                        }
                        if (txtDuration != null) {
                            txtDuration.setText(formatDuration(mediaPlayer.getDuration()));
                        }
                        if (seekBar != null) {
                            seekBar.setProgress(currentPosition);
                        }
                    }
                } catch (IllegalStateException e) {
                    return;
                }
                handler.postDelayed(this, 100);
            }
        }
    };

    public static void stopAllMusic() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song);

        // Khi mở trình phát này, dừng các trình phát khác
        HomeFeedAdapter.stopAllMusic();
        UploadActivity.stopAllPreview();
        stopAllMusic();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(mp -> {
            if (seekBar != null) {
                seekBar.setMax(mediaPlayer.getDuration());
            }
            
            // Tự động phát khi bài hát đã sẵn sàng (cho cả lần đầu vào và khi chuyển bài)
            if (isChangingSong || autoPlayFirstTime) {
                playAudio();
                play.setImageResource(R.drawable.pause);
                isPlaying = true;
                isDirty = true;
                isChangingSong = false;
                autoPlayFirstTime = false;
            }

            handler.removeCallbacks(updateSeekBar);
            handler.postDelayed(updateSeekBar, 100);
        });

        initViews();
        getData();
        setupSensors();
        setupListeners();
    }

    private void initViews() {
        imageView = findViewById(R.id.phonering);
        play = findViewById(R.id.play);
        next = findViewById(R.id.next);
        previous = findViewById(R.id.previous);
        loopBtn = findViewById(R.id.loopBtn);
        shuffle = findViewById(R.id.shuffle);
        seekBar = findViewById(R.id.seekbar);
        likeIcon = findViewById(R.id.like);
        textLike = findViewById(R.id.textlike);
        btnDownload = findViewById(R.id.btnDownload);
        btnFavorite = findViewById(R.id.btnFavorite);
        tvSongStatus = findViewById(R.id.tvSongStatus);
        songScrollView = findViewById(R.id.songScrollView);
        commentContainer = findViewById(R.id.comment_fragment_container);
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private void setupListeners() {
        imageView.setOnClickListener(v -> {
            isShakeEnabled = !isShakeEnabled;
            imageView.setColorFilter(ContextCompat.getColor(getApplicationContext(), 
                    isShakeEnabled ? R.color.primary_pink : R.color.black));
            Toast.makeText(this, isShakeEnabled ? "Bật lắc chuyển bài" : "Tắt lắc chuyển bài", Toast.LENGTH_SHORT).show();
        });

        mediaPlayer.setOnCompletionListener(mp -> playNextSong());

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) {
                        mediaPlayer.seekTo(progress);
                        currentPosition = progress;
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        play.setOnClickListener(v -> {
            if (!isDirty) {
                // Đảm bảo dừng các nguồn khác khi bắt đầu phát ở đây
                HomeFeedAdapter.stopAllMusic();
                UploadActivity.stopAllPreview();
                
                playAudio();
                play.setImageResource(R.drawable.pause);
                isPlaying = true;
                isDirty = true;
            } else {
                if (!isPlaying) {
                    // Đảm bảo dừng các nguồn khác khi bắt đầu phát ở đây
                    HomeFeedAdapter.stopAllMusic();
                    UploadActivity.stopAllPreview();
                    
                    resumeAudio();
                    play.setImageResource(R.drawable.pause);
                } else {
                    if (mediaPlayer != null) {
                        mediaPlayer.pause();
                        currentPosition = mediaPlayer.getCurrentPosition();
                    }
                    play.setImageResource(R.drawable.play);
                }
            }
            isPlaying = !isPlaying;
        });

        next.setOnClickListener(v -> playNextSong());
        previous.setOnClickListener(v -> playPreviousSong());
        findViewById(R.id.imgback).setOnClickListener(v -> finish());

        loopBtn.setOnClickListener(v -> {
            isLoop = !isLoop;
            updateUI_Loop();
            if (mediaPlayer != null) mediaPlayer.setLooping(isLoop);
        });

        shuffle.setOnClickListener(v -> {
            isShuffling = !isShuffling;
            updateUI_Shuffle();
        });

        likeIcon.setOnClickListener(v -> handleLike());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnDownload.setOnClickListener(v -> downloadAudio(audioURL));
    }

    private void getData() {
        Intent intent = getIntent();
        if (intent != null) {
            songId = intent.getStringExtra("songId");
            boolean jumpToComments = intent.getBooleanExtra("showComments", false);
            if (jumpToComments) {
                handler.postDelayed(() -> {
                    if (songScrollView != null && commentContainer != null) {
                        songScrollView.smoothScrollTo(0, commentContainer.getTop());
                    }
                }, 1000);
            }
        }

        DatabaseReference songsRef = FirebaseDatabase.getInstance().getReference().child("songs");
        songsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                songSnapshots.clear();
                int tempIndex = 0;
                boolean found = false;
                
                for (DataSnapshot ds : dataSnapshot.getChildren()) {
                    songSnapshots.add(ds);
                    String id = ds.child("id").getValue(String.class);
                    if (id == null) id = ds.getKey();
                    
                    if (id != null && id.equals(songId)) {
                        songIndex = tempIndex;
                        updateSongUI(ds);
                        audioURL = ds.child("srl").getValue(String.class);
                        prepareMediaPlayer(audioURL);
                        found = true;
                    }
                    tempIndex++;
                }
                
                songCount = songSnapshots.size();
                
                if (!found && !songSnapshots.isEmpty()) {
                    songIndex = 0;
                    DataSnapshot first = songSnapshots.get(0);
                    updateSongUI(first);
                    audioURL = first.child("srl").getValue(String.class);
                    prepareMediaPlayer(audioURL);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(Song.this, "Lỗi tải danh sách bài hát", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSongUI(DataSnapshot songSnapshot) {
        currentSongTitle = songSnapshot.child("title").getValue(String.class);
        currentSongArtist = songSnapshot.child("artist").getValue(String.class);
        currentSongImgUrl = songSnapshot.child("urlImg").getValue(String.class);
        currentSongCategory = songSnapshot.child("category").getValue(String.class);
        currentSongUserID = songSnapshot.child("userID").getValue(String.class);
        String status = songSnapshot.child("postContent").getValue(String.class);
        
        String id = songSnapshot.child("id").getValue(String.class);
        songId = id;

        TextView textViewMusicTitle = findViewById(R.id.txtMusicTitle);
        if (textViewMusicTitle != null) textViewMusicTitle.setText(currentSongTitle);

        TextView txtArtist = findViewById(R.id.txtartist);
        if (txtArtist != null) txtArtist.setText(currentSongArtist);

        if (tvSongStatus != null) {
            if (status != null && !status.isEmpty()) {
                tvSongStatus.setText("\"" + status + "\"");
                tvSongStatus.setVisibility(View.VISIBLE);
            } else {
                tvSongStatus.setVisibility(View.GONE);
            }
        }

        ImageView imgSong = findViewById(R.id.imgsong);
        if (imgSong != null) {
            if (currentSongImgUrl != null && !currentSongImgUrl.isEmpty()) {
                try {
                    byte[] imageBytes = Base64.decode(currentSongImgUrl, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    Glide.with(this).asBitmap().load(bitmap).into(imgSong);
                } catch (Exception e) {
                    imgSong.setImageResource(R.drawable.logo);
                }
            } else {
                imgSong.setImageResource(R.drawable.logo);
            }
        }

        checkIfLiked(id);
        checkIfFavorited(id);
        listenToLikeCount(id);
        sendDataToFragment(id);
    }

    private void listenToLikeCount(String id) {
        DatabaseReference userLikesTotalRef = FirebaseDatabase.getInstance().getReference("user_likes");
        userLikesTotalRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot userLikes : snapshot.getChildren()) {
                    if (userLikes.hasChild(id)) count++;
                }
                if (textLike != null) textLike.setText(String.valueOf(count));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void handleLike() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để thả tim", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference userLikesRef = FirebaseDatabase.getInstance().getReference("user_likes")
                .child(user.getUid()).child(songId);

        // Immediate UI feedback
        isLiked = !isLiked;
        updateLikeIconUI();

        userLikesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    userLikesRef.removeValue().addOnSuccessListener(aVoid -> {
                        updateLikeCountInSongNode(songId, -1);
                    });
                } else {
                    userLikesRef.setValue(true).addOnSuccessListener(aVoid -> {
                        updateLikeCountInSongNode(songId, 1);
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                // Rollback on failure
                isLiked = !isLiked;
                updateLikeIconUI();
            }
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

    private void checkIfLiked(String id) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseDatabase.getInstance().getReference("user_likes")
                .child(user.getUid()).child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isLiked = snapshot.exists();
                updateLikeIconUI();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateLikeIconUI() {
        if (likeIcon != null) {
            if (isLiked) {
                likeIcon.setImageResource(R.drawable.ic_like_selected);
                likeIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else {
                likeIcon.setImageResource(R.drawable.ic_like_unselected);
                likeIcon.clearColorFilter();
            }
        }
    }

    private void checkIfFavorited(String id) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("favorites")
                .child(user.getUid()).child(id);

        favRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isFavorited = snapshot.exists();
                updateFavoriteUI();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void toggleFavorite() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("favorites")
                .child(user.getUid()).child(songId);

        if (isFavorited) {
            favRef.removeValue().addOnSuccessListener(aVoid -> {
                isFavorited = false;
                updateFavoriteUI();
                Toast.makeText(this, "Đã xóa khỏi yêu thích", Toast.LENGTH_SHORT).show();
            });
        } else {
            Map<String, Object> songData = new HashMap<>();
            songData.put("id", songId);
            songData.put("title", currentSongTitle);
            songData.put("artist", currentSongArtist);
            songData.put("urlImg", currentSongImgUrl);
            songData.put("category", currentSongCategory);
            songData.put("userID", currentSongUserID);
            songData.put("srl", audioURL);
            songData.put("indexSong", songIndex);

            favRef.setValue(songData).addOnSuccessListener(aVoid -> {
                isFavorited = true;
                updateFavoriteUI();
                Toast.makeText(this, "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateFavoriteUI() {
        if (btnFavorite != null) {
            btnFavorite.setImageResource(isFavorited ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
            if (isFavorited) {
                btnFavorite.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            } else {
                btnFavorite.clearColorFilter();
            }
        }
    }

    private void prepareMediaPlayer(String url) {
        if (url == null || url.isEmpty()) {
            isChangingSong = false;
            return;
        }
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            isChangingSong = false;
        }
    }

    private void playNextSong() {
        if (isChangingSong || songCount <= 0) return;
        songIndex = isShuffling ? (int) (Math.random() * songCount) : (int) ((songIndex + 1) % songCount);
        changeSong(songIndex);
    }

    private void playPreviousSong() {
        if (isChangingSong || songCount <= 0) return;
        songIndex = (int) ((songIndex - 1 + songCount) % songCount);
        changeSong(songIndex);
    }

    private void changeSong(int index) {
        if (index < 0 || index >= songSnapshots.size()) {
            isChangingSong = false;
            return;
        }
        
        isChangingSong = true;
        DataSnapshot snapshot = songSnapshots.get(index);
        updateSongUI(snapshot);
        audioURL = snapshot.child("srl").getValue(String.class);
        prepareMediaPlayer(audioURL);
    }

    private void updateUI_Loop() {
        if (loopBtn != null) loopBtn.setImageResource(isLoop ? R.drawable.loop_color : R.drawable.loop);
    }

    private void updateUI_Shuffle() {
        if (shuffle != null) shuffle.setImageResource(isShuffling ? R.drawable.shuffle_color : R.drawable.shuffle);
    }

    private void resumeAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(currentPosition);
            mediaPlayer.start();
        }
    }

    private void playAudio() {
        if (mediaPlayer != null) mediaPlayer.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
        // Không release static mediaPlayer ở đây nếu muốn nhạc tiếp tục phát khi thoát activity (tùy logic app)
        // Nhưng ở đây nên release nếu không dùng Service.
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private String formatDuration(long durationMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) 
                         : String.format("%02d:%02d", minutes, seconds);
    }

    private void downloadAudio(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Link tải không khả dụng", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = (currentSongTitle != null ? currentSongTitle : String.valueOf(System.currentTimeMillis())) + ".mp3";
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);

        Toast.makeText(this, "Bắt đầu tải xuống...", Toast.LENGTH_SHORT).show();

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(Song.this, "Lỗi tải xuống: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(Song.this, "Lỗi: " + response.message(), Toast.LENGTH_SHORT).show());
                    return;
                }

                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(file)) {
                    byte[] data = new byte[4096];
                    int count;
                    while ((count = inputStream.read(data)) != -1) {
                        outputStream.write(data, 0, count);
                    }
                    outputStream.flush();
                    
                    runOnUiThread(() -> {
                        Toast.makeText(Song.this, "Đã tải xong: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                        // Thông báo cho hệ thống có file mới để hiện trong thư viện nhạc
                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        intent.setData(Uri.fromFile(file));
                        sendBroadcast(intent);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(Song.this, "Lỗi lưu file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isShakeEnabled) {
            float x = event.values[0], y = event.values[1], z = event.values[2];
            double acceleration = Math.sqrt(x * x + y * y + z * z);
            if (acceleration > 15) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastShakeTime > 1000) {
                    lastShakeTime = currentTime;
                    playNextSong();
                }
            }
        }
    }

    @Override protected void onPause() { super.onPause(); if (sensorManager != null) sensorManager.unregisterListener(this); }
    @Override protected void onResume() { super.onResume(); if (sensorManager != null && accelerometerSensor != null) sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void sendDataToFragment(String id) {
        if (id == null) return;
        CommentsFragment commentsFragment = new CommentsFragment();
        Bundle bundle = new Bundle();
        bundle.putString("key_song_id", id);
        commentsFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().replace(R.id.comment_fragment_container, commentsFragment).commitAllowingStateLoss();
    }
}
