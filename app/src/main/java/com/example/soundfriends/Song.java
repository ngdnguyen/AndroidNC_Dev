package com.example.soundfriends;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.example.soundfriends.adapter.HomeFeedAdapter;
import com.example.soundfriends.fragments.CommentsFragment;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.services.MusicService;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Song extends AppCompatActivity implements SensorEventListener {
    
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    
    private SeekBar seekBar;
    private final Handler handler = new Handler();
    private String audioURL;

    private String songId = "";
    private ImageView imageView, likeIcon, btnDownload, btnFavorite;
    private TextView textLike, tvSongStatus;
    private NestedScrollView songScrollView;
    private View commentContainer;
    int songIndex = -1;
    long songCount;
    private List<DataSnapshot> songSnapshots = new ArrayList<>();
    ImageButton next, previous, play;

    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private boolean isShakeEnabled = false;
    private long lastShakeTime = 0;

    ImageButton loopBtn, shuffle;
    private boolean isLoop, isShuffling = false;
    private boolean isLiked = false;
    private boolean isFavorited = false;

    private String currentSongTitle, currentSongArtist, currentSongImgUrl, currentSongCategory, currentSongUserID;

    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaController != null) {
                long currentPos = mediaController.getCurrentPosition();
                long duration = mediaController.getDuration();
                
                TextView txtCurrentDuration = findViewById(R.id.txtCurrentDuration);
                TextView txtDuration = findViewById(R.id.txtDuration);
                
                if (txtCurrentDuration != null) txtCurrentDuration.setText(formatDuration(currentPos));
                if (txtDuration != null && duration > 0) txtDuration.setText(formatDuration(duration));
                if (seekBar != null && duration > 0) {
                    seekBar.setMax((int) duration);
                    seekBar.setProgress((int) currentPos);
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    public static void stopAllMusic() {
        // Media3 handles this via Service/Controller
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song);

        // Đừng gọi stopAllMusic ở đây để đảm bảo tính liên mạch
        UploadActivity.stopAllPreview();

        initViews();
        setupSensors();
        setupListeners();
        getData();
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializeController();
    }

    private void initializeController() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        play.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play);
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_ENDED) {
                            playNextSong();
                        }
                    }
                });
                
                // Cập nhật giao diện nút play ngay lập tức dựa trên trạng thái hiện tại
                play.setImageResource(mediaController.isPlaying() ? R.drawable.pause : R.drawable.play);
                
                // Nếu dữ liệu đã tải xong trước khi controller sẵn sàng, hãy thực hiện phát/kiểm tra
                if (songIndex != -1) {
                    playSongAt(songIndex);
                }
                
                handler.post(updateSeekBar);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
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

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaController != null) {
                        mediaController.seekTo(progress);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        play.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                } else {
                    mediaController.play();
                }
            }
        });

        next.setOnClickListener(v -> playNextSong());
        previous.setOnClickListener(v -> playPreviousSong());
        findViewById(R.id.imgback).setOnClickListener(v -> finish());

        loopBtn.setOnClickListener(v -> {
            isLoop = !isLoop;
            updateUI_Loop();
            if (mediaController != null) {
                mediaController.setRepeatMode(isLoop ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            }
        });

        shuffle.setOnClickListener(v -> {
            isShuffling = !isShuffling;
            updateUI_Shuffle();
            if (mediaController != null) {
                mediaController.setShuffleModeEnabled(isShuffling);
            }
        });

        likeIcon.setOnClickListener(v -> handleLike());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        btnDownload.setOnClickListener(v -> downloadAudio(audioURL));
    }

    private void getData() {
        Intent intent = getIntent();
        if (intent != null) {
            songId = intent.getStringExtra("songId");
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
                        found = true;
                    }
                    tempIndex++;
                }
                
                songCount = songSnapshots.size();
                if (found && mediaController != null) {
                    playSongAt(songIndex);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });
    }

    private void updateSongUI(DataSnapshot songSnapshot) {
        currentSongTitle = songSnapshot.child("title").getValue(String.class);
        currentSongArtist = songSnapshot.child("artist").getValue(String.class);
        currentSongImgUrl = songSnapshot.child("urlImg").getValue(String.class);
        currentSongCategory = songSnapshot.child("category").getValue(String.class);
        currentSongUserID = songSnapshot.child("userID").getValue(String.class);
        audioURL = songSnapshot.child("srl").getValue(String.class);
        String status = songSnapshot.child("postContent").getValue(String.class);
        songId = songSnapshot.child("id").getValue(String.class);

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

        checkIfLiked(songId);
        checkIfFavorited(songId);
        listenToLikeCount(songId);
        sendDataToFragment(songId);
    }

    private void playSongAt(int index) {
        if (mediaController == null || index < 0 || index >= songSnapshots.size()) return;
        
        DataSnapshot ds = songSnapshots.get(index);
        String url = ds.child("srl").getValue(String.class);
        String title = ds.child("title").getValue(String.class);
        String artist = ds.child("artist").getValue(String.class);
        String id = ds.child("id").getValue(String.class);
        if (id == null) id = ds.getKey();

        if (url == null) return;

        // KIỂM TRA LIÊN MẠCH: Nếu bài đang phát trùng với bài yêu cầu thì KHÔNG phát lại
        MediaItem currentItem = mediaController.getCurrentMediaItem();
        if (currentItem != null && id.equals(currentItem.mediaId)) {
            // Nhạc đang phát đúng bài này rồi, chỉ cần đảm bảo nó đang Play
            if (!mediaController.isPlaying()) mediaController.play();
            return;
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build();

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(id) // Gán mediaId để kiểm tra liên mạch
                .setUri(url)
                .setMediaMetadata(metadata)
                .build();

        mediaController.setMediaItem(mediaItem);
        mediaController.prepare();
        mediaController.play();
    }

    private void playNextSong() {
        if (songCount <= 0) return;
        songIndex = (int) ((songIndex + 1) % songCount);
        DataSnapshot nextSnapshot = songSnapshots.get(songIndex);
        updateSongUI(nextSnapshot);
        playSongAt(songIndex);
    }

    private void playPreviousSong() {
        if (songCount <= 0) return;
        songIndex = (int) ((songIndex - 1 + songCount) % songCount);
        DataSnapshot prevSnapshot = songSnapshots.get(songIndex);
        updateSongUI(prevSnapshot);
        playSongAt(songIndex);
    }

    private void updateUI_Loop() {
        if (loopBtn != null) loopBtn.setImageResource(isLoop ? R.drawable.loop_color : R.drawable.loop);
    }

    private void updateUI_Shuffle() {
        if (shuffle != null) shuffle.setImageResource(isShuffling ? R.drawable.shuffle_color : R.drawable.shuffle);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            mediaController = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBar);
    }

    private String formatDuration(long durationMs) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // --- Firebase functions remain same ---
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
        if (user == null) return;
        DatabaseReference userLikesRef = FirebaseDatabase.getInstance().getReference("user_likes").child(user.getUid()).child(songId);
        isLiked = !isLiked;
        updateLikeIconUI();
        userLikesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) userLikesRef.removeValue();
                else userLikesRef.setValue(true);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkIfLiked(String id) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseDatabase.getInstance().getReference("user_likes").child(user.getUid()).child(id).addListenerForSingleValueEvent(new ValueEventListener() {
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
            likeIcon.setImageResource(isLiked ? R.drawable.ic_like_selected : R.drawable.ic_like_unselected);
            if (isLiked) likeIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            else likeIcon.clearColorFilter();
        }
    }

    private void checkIfFavorited(String id) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseDatabase.getInstance().getReference("favorites").child(user.getUid()).child(id).addListenerForSingleValueEvent(new ValueEventListener() {
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
        if (user == null) return;
        DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("favorites").child(user.getUid()).child(songId);
        if (isFavorited) {
            favRef.removeValue().addOnSuccessListener(aVoid -> { isFavorited = false; updateFavoriteUI(); });
        } else {
            Map<String, Object> songData = new HashMap<>();
            songData.put("id", songId); songData.put("title", currentSongTitle); songData.put("artist", currentSongArtist);
            songData.put("urlImg", currentSongImgUrl); songData.put("srl", audioURL);
            favRef.setValue(songData).addOnSuccessListener(aVoid -> { isFavorited = true; updateFavoriteUI(); });
        }
    }

    private void updateFavoriteUI() {
        if (btnFavorite != null) {
            btnFavorite.setImageResource(isFavorited ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
            if (isFavorited) btnFavorite.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            else btnFavorite.clearColorFilter();
        }
    }

    private void downloadAudio(String url) {
        if (url == null || url.isEmpty()) return;
        Toast.makeText(this, "Bắt đầu tải xuống...", Toast.LENGTH_SHORT).show();
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), currentSongTitle + ".mp3");
                try (InputStream is = response.body().byteStream(); FileOutputStream os = new FileOutputStream(file)) {
                    byte[] data = new byte[4096]; int count;
                    while ((count = is.read(data)) != -1) os.write(data, 0, count);
                    runOnUiThread(() -> Toast.makeText(Song.this, "Đã tải xong", Toast.LENGTH_SHORT).show());
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
