package com.example.soundfriends.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.soundfriends.R;
import com.example.soundfriends.auth.Login;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.adapter.UploadSongs;
import com.example.soundfriends.utils.ToggleShowHideUI;
import com.example.soundfriends.utils.WrapContentLinearLayoutManager;
import com.example.soundfriends.utils.uuid;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.io.ByteArrayOutputStream;


public class SettingsFragment extends Fragment implements AdapterView.OnItemSelectedListener {

    TextView textViewImage;
    ProgressBar progressBar;
    RelativeLayout rlUploadingSong;
    Uri audioUri ;
    StorageReference mStorageref;
    StorageTask mUploadsTask ;
    DatabaseReference referenceSongs ;
    MediaMetadataRetriever metadataRetriever;
    byte [] art ;
    String title1, artist1, category1, userID;
    EditText title,artist,category;
    ImageView imageView ;
    UploadSongs uploadSongs, favoriteSongsAdapter;
    RecyclerView rcvlist_song_uploaded, rcvlist_song_favorite;

    FirebaseAuth auth;
    FirebaseUser user;
    TextView textView;
    ImageView settingsAvatar;
    Button btnLogout, btnUpload, buttonUpload;
    Bitmap bitmap;
    int songIndex;

    public SettingsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        textView = view.findViewById(R.id.textView);
        btnLogout = view.findViewById(R.id.logout);
        settingsAvatar = view.findViewById(R.id.settingsAvatar);
        textViewImage = view.findViewById(R.id.tvsrl);
        progressBar = view.findViewById(R.id.progressbar);
        rlUploadingSong = view.findViewById(R.id.rl_layout);
        title = view.findViewById(R.id.tvSong);
        artist = view.findViewById(R.id.tvArtist);
        category = view.findViewById(R.id.tvCategory);
        imageView = view.findViewById(R.id.img1);
        rcvlist_song_uploaded = view.findViewById(R.id.rcvlist_song_uploaded);
        rcvlist_song_favorite = view.findViewById(R.id.rcvlist_song_favorite);

        rcvlist_song_uploaded.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        rcvlist_song_favorite.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));

        if(user == null) {
            goAuthActivity();
        } else {
            userID = user.getUid();
            textView.setText("Xin chào " + (user.getEmail() != null ? user.getEmail() : user.getDisplayName()));
            if(user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).into(settingsAvatar);
            }
            setupUploadedSongs();
            setupFavoriteSongs();
        }

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            goAuthActivity();
        });

        buttonUpload = view.findViewById(R.id.buttonUplaod);
        btnUpload = view.findViewById(R.id.bt_upload);
        btnUpload.setOnClickListener(this::openAudioFiles);
        buttonUpload.setOnClickListener(this::uploadFileToFirebase);

        metadataRetriever = new MediaMetadataRetriever();
        referenceSongs = FirebaseDatabase.getInstance().getReference().child("songs");
        mStorageref = FirebaseStorage.getInstance().getReference().child("songs");

        return view;
    }

    private void setupUploadedSongs() {
        Query query = FirebaseDatabase.getInstance().getReference("songs").orderByChild("userID").equalTo(userID);
        FirebaseRecyclerOptions<Songs> options = new FirebaseRecyclerOptions.Builder<Songs>()
                .setQuery(query, Songs.class)
                .build();
        uploadSongs = new UploadSongs(options);
        rcvlist_song_uploaded.setAdapter(uploadSongs);
        uploadSongs.startListening();
    }

    private void setupFavoriteSongs() {
        DatabaseReference favRef = FirebaseDatabase.getInstance().getReference("favorites").child(userID);
        FirebaseRecyclerOptions<Songs> options = new FirebaseRecyclerOptions.Builder<Songs>()
                .setQuery(favRef, Songs.class)
                .build();
        
        favoriteSongsAdapter = new UploadSongs(options, true);
        rcvlist_song_favorite.setAdapter(favoriteSongsAdapter);
        favoriteSongsAdapter.startListening();
    }

    private void goAuthActivity(){
        startActivity(new Intent(getContext(), Login.class));
        getActivity().finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (uploadSongs != null) uploadSongs.startListening();
        if (favoriteSongsAdapter != null) favoriteSongsAdapter.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (uploadSongs != null) uploadSongs.stopListening();
        if (favoriteSongsAdapter != null) favoriteSongsAdapter.stopListening();
    }

    private void openAudioFiles(View v ){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        startActivityForResult(i,101);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 101 && resultCode  == Activity.RESULT_OK && data != null && data.getData() != null){
            audioUri = data.getData();
            textViewImage.setText(getFileName(audioUri));
            try {
                metadataRetriever.setDataSource(requireContext(), audioUri);
                art = metadataRetriever.getEmbeddedPicture();
                artist.setText(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                title.setText(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
                category.setText(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
                if(art!= null){
                    bitmap = BitmapFactory.decodeByteArray(art,0,art.length);
                    imageView.setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                Log.e("SettingsFragment", "Error setting data source: " + e.getMessage());
                Toast.makeText(getContext(), "Không thể đọc thông tin tệp âm thanh", Toast.LENGTH_SHORT).show();
                artist.setText("");
                title.setText(getFileName(audioUri));
                category.setText("");
                imageView.setImageResource(R.drawable.logo); // Sử dụng logo làm ảnh mặc định
            }
            ToggleShowHideUI.toggleShowUI(true, rlUploadingSong);
        }
    }

    @SuppressLint("Range")
    private String getFileName(Uri uri){
        String result = null;
        if(uri.getScheme().equals("content")){
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if(result == null){
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if(cut != -1) result = result.substring(cut +1);
        }
        return result;
    }

    public void uploadFileToFirebase(View v ){
        if(mUploadsTask != null && mUploadsTask.isInProgress()){
            Toast.makeText(getContext(), "Đang tải lên...", Toast.LENGTH_SHORT).show();
        }else {
            uploadFiles();
        }
    }

    private void uploadFiles() {
        title1 = title.getText().toString().trim();
        artist1 = artist.getText().toString().trim();
        category1 = category.getText().toString().trim();

        if(audioUri != null && !title1.isEmpty()){
            ToggleShowHideUI.toggleShowUI(true, progressBar);
            StorageReference storageReference = mStorageref.child(System.currentTimeMillis() + "." + getFileExtension(audioUri));
            mUploadsTask = storageReference.putFile(audioUri).addOnSuccessListener(taskSnapshot -> {
                storageReference.getDownloadUrl().addOnSuccessListener(uri -> {
                    String base64Image = "";
                    if(bitmap != null){
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                        base64Image = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    }
                    String songId = uuid.createTransactionID();
                    Songs uploadSong = new Songs(0, songId, title1, artist1, category1, base64Image, uri.toString(), userID);
                    
                    // SỬA TẠI ĐÂY: Bắt lỗi ghi vào Realtime Database
                    referenceSongs.push().setValue(uploadSong)
                        .addOnSuccessListener(aVoid -> {
                            ToggleShowHideUI.toggleShowUI(false, progressBar);
                            ToggleShowHideUI.toggleShowUI(false, rlUploadingSong);
                            Toast.makeText(getContext(), "Tải lên thành công!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            ToggleShowHideUI.toggleShowUI(false, progressBar);
                            Log.e("FirebaseEmulator", "Database error: " + e.getMessage());
                            Toast.makeText(getContext(), "Lỗi lưu Database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                }).addOnFailureListener(e -> {
                    ToggleShowHideUI.toggleShowUI(false, progressBar);
                    Toast.makeText(getContext(), "Lỗi lấy URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }).addOnFailureListener(e -> {
                ToggleShowHideUI.toggleShowUI(false, progressBar);
                Toast.makeText(getContext(), "Lỗi tải Storage: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private String getFileExtension(Uri uri){
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(getContext().getContentResolver().getType(uri));
    }

    @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {}
    @Override public void onNothingSelected(AdapterView<?> adapterView) {}
}