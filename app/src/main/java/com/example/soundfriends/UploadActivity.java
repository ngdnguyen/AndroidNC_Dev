package com.example.soundfriends;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
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

public class UploadActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Button btnPost;
    private de.hdodenhof.circleimageview.CircleImageView ivUserAvatar;
    private TextView tvUserName;
    private EditText etStatus, etSongTitle, etArtist;
    private CardView cvMusicInfo;
    private ImageView ivSongThumb;
    private TextView tvFileName;
    private LinearLayout btnRecordMusic;
    private TextView tvRecordStatus;
    private ProgressBar progressBar;

    private Uri audioUri;
    private Bitmap songBitmap;
    private String userID, currentUserName, currentUserAvatar;
    private DatabaseReference referenceSongs;
    private StorageReference mStorageRef;
    private StorageTask mUploadTask;
    private String musicGenre = "Âm nhạc";

    // Recording variables
    private MediaRecorder mediaRecorder;
    private String recordFilePath;
    private boolean isRecording = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        initViews();
        setupFirebase();
        setupUserData();

        btnBack.setOnClickListener(v -> finish());
        
        btnRecordMusic.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                if (checkPermissions()) {
                    startRecording();
                } else {
                    requestPermissions();
                }
            }
        });

        btnRecordMusic.setOnLongClickListener(v -> {
            openAudioFiles();
            return true;
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
        cvMusicInfo = findViewById(R.id.cvMusicInfo);
        ivSongThumb = findViewById(R.id.ivSongThumb);
        tvFileName = findViewById(R.id.tvFileName);
        btnRecordMusic = findViewById(R.id.btnRecordMusic);
        tvRecordStatus = findViewById(R.id.tvRecordStatus);
        progressBar = findViewById(R.id.progressBar);
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
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            tvRecordStatus.setText("Đang ghi... (Dừng)");
            Toast.makeText(this, "Bắt đầu ghi âm", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e("UploadActivity", "prepare() failed: " + e.getMessage());
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            tvRecordStatus.setText("Chạm để ghi âm / Giữ để chọn file");
            
            File recordedFile = new File(recordFilePath);
            audioUri = Uri.fromFile(recordedFile);
            
            cvMusicInfo.setVisibility(View.VISIBLE);
            tvFileName.setText("Bản ghi âm mới");
            etSongTitle.setText("Ghi âm " + System.currentTimeMillis());
            etArtist.setText(currentUserName);
            ivSongThumb.setImageResource(R.drawable.round_mic_24);
            
            Toast.makeText(this, "Đã lưu bản ghi âm", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Quyền ghi âm bị từ chối", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void extractMetadata(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            byte[] art = retriever.getEmbeddedPicture();

            if (title != null && !title.isEmpty()) {
                etSongTitle.setText(title);
            } else {
                etSongTitle.setText("Bản nhạc mới");
            }

            if (artist != null && !artist.isEmpty()) {
                etArtist.setText(artist);
            } else {
                etArtist.setText(currentUserName);
            }

            if (genre != null) musicGenre = genre;
            
            if (art != null) {
                songBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                ivSongThumb.setImageBitmap(songBitmap);
            } else {
                ivSongThumb.setImageResource(R.drawable.logo);
            }
        } catch (Exception e) {
            Log.e("UploadActivity", "Metadata error: " + e.getMessage());
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
        String status = etStatus.getText().toString().trim();

        if (audioUri == null) {
            Toast.makeText(this, "Vui lòng chọn hoặc ghi âm một bài hát", Toast.LENGTH_SHORT).show();
            return;
        }

        if (title.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên bài hát", Toast.LENGTH_SHORT).show();
            return;
        }

        if (artist.isEmpty()) {
            artist = currentUserName;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnPost.setEnabled(false);

        String extension = getFileExtension(audioUri);
        if (extension == null) extension = "3gp"; // Cho file ghi âm

        StorageReference fileRef = mStorageRef.child(System.currentTimeMillis() + "." + extension);
        String finalArtist = artist;
        mUploadTask = fileRef.putFile(audioUri)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    saveToDatabase(uri.toString(), title, finalArtist, status);
                }))
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnPost.setEnabled(true);
                    Toast.makeText(UploadActivity.this, "Lỗi tải lên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveToDatabase(String audioUrl, String title, String artist, String status) {
        String base64Image = "";
        if (songBitmap != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            songBitmap.compress(Bitmap.CompressFormat.JPEG, 50, bos);
            base64Image = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        }

        String songId = uuid.createTransactionID();

        Songs song = new Songs(songId, title, artist, musicGenre, base64Image, audioUrl, userID,
                               currentUserName, currentUserAvatar, status);

        referenceSongs.child(songId).setValue(song)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(UploadActivity.this, "Đã đăng bài thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnPost.setEnabled(true);
                    Toast.makeText(UploadActivity.this, "Lỗi lưu dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String getFileExtension(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null) {
            return MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    }
}
