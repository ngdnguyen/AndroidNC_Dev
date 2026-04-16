package com.example.soundfriends;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.soundfriends.adapter.HomeFeedAdapter;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.utils.uuid;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class UploadActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Button btnPost;
    private de.hdodenhof.circleimageview.CircleImageView ivUserAvatar;
    private TextView tvUserName;
    private EditText etStatus, etSongTitle, etArtist, etCategory;
    private CardView cvMusicInfo;
    private ImageView ivSongThumb;
    private TextView tvFileName;
    private LinearLayout btnRecordMusic, recordingLayout;
    private TextView tvRecordStatus, tvTimer;
    private ProgressBar progressBar;
    private ImageButton btnPauseRecord, btnCancelRecord, btnDoneRecord;
    
    private ImageButton btnPreviewPlay, btnRemoveFile;
    private SeekBar sbPreview;
    private TextView tvPreviewCurrentTime, tvPreviewTotalTime;

    private Uri audioUri;
    private Bitmap songBitmap;
    private String userID, currentUserName, currentUserAvatar;
    private DatabaseReference referenceSongs;
    private StorageReference mStorageRef;
    private StorageTask mUploadTask;

    private MediaRecorder mediaRecorder;
    private String recordFilePath;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Static MediaPlayer để quản lý tập trung
    private static MediaPlayer previewPlayer;
    private boolean isPreviewPlaying = false;
    private Handler previewHandler = new Handler();

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private long timeInMilliseconds = 0L;
    private long timeSwapBuff = 0L;
    private long updatedTime = 0L;

    private Runnable updateTimerThread = new Runnable() {
        public void run() {
            timeInMilliseconds = System.currentTimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
            timerHandler.postDelayed(this, 100);
        }
    };

    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (previewPlayer != null && isPreviewPlaying) {
                try {
                    int currentPos = previewPlayer.getCurrentPosition();
                    sbPreview.setProgress(currentPos);
                    tvPreviewCurrentTime.setText(formatTime(currentPos));
                    previewHandler.postDelayed(this, 500);
                } catch (Exception e) {
                    previewHandler.removeCallbacks(this);
                }
            }
        }
    };

    public static void stopAllPreview() {
        if (previewPlayer != null) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        initViews();
        setupFirebase();
        setupUserData();

        btnBack.setOnClickListener(v -> finish());
        
        btnRecordMusic.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            else if (checkPermissions()) startRecording();
            else requestPermissions();
        });

        btnRecordMusic.setOnLongClickListener(v -> {
            openAudioFiles();
            return true;
        });

        btnPauseRecord.setOnClickListener(v -> {
            if (isRecording) {
                if (!isPaused) pauseRecording();
                else resumeRecording();
            }
        });

        btnCancelRecord.setOnClickListener(v -> cancelRecording());
        btnDoneRecord.setOnClickListener(v -> stopRecording());

        btnPreviewPlay.setOnClickListener(v -> togglePreview());
        btnRemoveFile.setOnClickListener(v -> removeSelectedFile());

        sbPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && previewPlayer != null) {
                    previewPlayer.seekTo(progress);
                    tvPreviewCurrentTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnPost.setOnClickListener(v -> uploadPost());
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnPost = findViewById(R.id.btnPost);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        tvUserName = findViewById(R.id.tvUserName);
        etStatus = findViewById(R.id.etStatus);
        etSongTitle = findViewById(R.id.etSongTitle);
        etArtist = findViewById(R.id.etArtist);
        etCategory = findViewById(R.id.etCategory);
        cvMusicInfo = findViewById(R.id.cvMusicInfo);
        ivSongThumb = findViewById(R.id.ivSongThumb);
        tvFileName = findViewById(R.id.tvFileName);
        btnRecordMusic = findViewById(R.id.btnRecordMusic);
        tvRecordStatus = findViewById(R.id.tvRecordStatus);
        progressBar = findViewById(R.id.progressBar);
        
        recordingLayout = findViewById(R.id.recording_layout);
        tvTimer = findViewById(R.id.tvTimer);
        btnPauseRecord = findViewById(R.id.btnPauseRecord);
        btnCancelRecord = findViewById(R.id.btnCancelRecord);
        btnDoneRecord = findViewById(R.id.btnDoneRecord);

        btnPreviewPlay = findViewById(R.id.btnPreviewPlay);
        btnRemoveFile = findViewById(R.id.btnRemoveFile);
        sbPreview = findViewById(R.id.sbPreview);
        tvPreviewCurrentTime = findViewById(R.id.tvPreviewCurrentTime);
        tvPreviewTotalTime = findViewById(R.id.tvPreviewTotalTime);
    }

    private void setupFirebase() {
        referenceSongs = FirebaseDatabase.getInstance().getReference().child("songs");
        mStorageRef = FirebaseStorage.getInstance().getReference().child("songs");
    }

    private void setupUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userID = user.getUid();
            currentUserName = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
            currentUserAvatar = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
            tvUserName.setText(currentUserName);
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).into(ivUserAvatar);
            }
        } else {
            Toast.makeText(this, "Vui lòng đăng nhập để đăng bài", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void openAudioFiles() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            audioUri = data.getData();
            cvMusicInfo.setVisibility(View.VISIBLE);
            tvFileName.setText(getFileName(audioUri));
            extractMetadata(audioUri);
            stopPreview(); 
        }
    }

    private void startRecording() {
        recordFilePath = getExternalCacheDir().getAbsolutePath() + "/recorded_audio.3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(recordFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            stopPreview();
            HomeFeedAdapter.stopAllMusic();
            Song.stopAllMusic();

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
            Log.e("UploadActivity", "prepare() failed: " + e.getMessage());
        }
    }

    private void pauseRecording() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            mediaRecorder.pause();
            isPaused = true;
            timeSwapBuff += timeInMilliseconds;
            timerHandler.removeCallbacks(updateTimerThread);
            btnPauseRecord.setImageResource(R.drawable.play); 
        }
    }

    private void resumeRecording() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            mediaRecorder.resume();
            isPaused = false;
            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(updateTimerThread, 0);
            btnPauseRecord.setImageResource(R.drawable.ic_pause);
        }
    }

    private void cancelRecording() {
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

    private void stopRecording() {
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
            audioUri = Uri.fromFile(recordedFile);
            cvMusicInfo.setVisibility(View.VISIBLE);
            tvFileName.setText("Bản ghi âm mới");
            etSongTitle.setText("Ghi âm " + System.currentTimeMillis());
            etArtist.setText(currentUserName);
            etCategory.setText("Âm nhạc");
            ivSongThumb.setImageResource(R.drawable.round_mic_24);
        }
    }

    private void togglePreview() {
        if (audioUri == null) return;
        if (isPreviewPlaying) pausePreview();
        else startPreview();
    }

    private void startPreview() {
        // Dừng nhạc từ các nguồn khác
        HomeFeedAdapter.stopAllMusic();
        Song.stopAllMusic();

        if (previewPlayer == null) {
            previewPlayer = new MediaPlayer();
            try {
                previewPlayer.setDataSource(this, audioUri);
                previewPlayer.prepare();
                sbPreview.setMax(previewPlayer.getDuration());
                tvPreviewTotalTime.setText(formatTime(previewPlayer.getDuration()));
                previewPlayer.setOnCompletionListener(mp -> {
                    isPreviewPlaying = false;
                    btnPreviewPlay.setImageResource(R.drawable.play);
                    sbPreview.setProgress(0);
                    tvPreviewCurrentTime.setText("00:00");
                    previewHandler.removeCallbacks(updateSeekBar);
                });
            } catch (IOException e) {
                Toast.makeText(this, "Không thể phát bản nhạc này", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        previewPlayer.start();
        isPreviewPlaying = true;
        btnPreviewPlay.setImageResource(R.drawable.pause);
        previewHandler.post(updateSeekBar);
    }

    private void pausePreview() {
        if (previewPlayer != null && previewPlayer.isPlaying()) {
            previewPlayer.pause();
        }
        isPreviewPlaying = false;
        btnPreviewPlay.setImageResource(R.drawable.play);
        previewHandler.removeCallbacks(updateSeekBar);
    }

    private void stopPreview() {
        if (previewPlayer != null) {
            try {
                if (previewPlayer.isPlaying()) previewPlayer.stop();
                previewPlayer.release();
            } catch (Exception ignored) {}
            previewPlayer = null;
        }
        isPreviewPlaying = false;
        btnPreviewPlay.setImageResource(R.drawable.play);
        previewHandler.removeCallbacks(updateSeekBar);
        sbPreview.setProgress(0);
        tvPreviewCurrentTime.setText("00:00");
    }

    private void removeSelectedFile() {
        stopPreview();
        audioUri = null;
        songBitmap = null;
        cvMusicInfo.setVisibility(View.GONE);
        etSongTitle.setText("");
        etArtist.setText("");
        etCategory.setText("");
        tvFileName.setText("");
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    private void extractMetadata(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            byte[] art = retriever.getEmbeddedPicture();

            if (title != null && !title.isEmpty()) etSongTitle.setText(title);
            else etSongTitle.setText("Bản nhạc mới");

            if (artist != null && !artist.isEmpty()) etArtist.setText(artist);
            else etArtist.setText(currentUserName);

            if (genre != null && !genre.isEmpty()) etCategory.setText(genre);
            else etCategory.setText("Âm nhạc");

            if (art != null) {
                songBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                ivSongThumb.setImageBitmap(songBitmap);
            } else {
                ivSongThumb.setImageResource(R.drawable.logo);
            }
        } catch (Exception e) {
            etArtist.setText(currentUserName);
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void uploadPost() {
        String title = etSongTitle.getText().toString().trim();
        String artist = etArtist.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        String status = etStatus.getText().toString().trim();

        if (audioUri == null) {
            Toast.makeText(this, "Vui lòng chọn hoặc ghi âm một bài hát", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên bài hát", Toast.LENGTH_SHORT).show();
            return;
        }
    }
    
    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
