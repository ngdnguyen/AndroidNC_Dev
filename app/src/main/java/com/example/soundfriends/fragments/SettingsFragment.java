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

import java.util.UUID;

import java.io.ByteArrayOutputStream;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SettingsFragment extends Fragment  implements AdapterView.OnItemSelectedListener {

    TextView textViewImage;
    ProgressBar progressBar, rcvProgressBar;
    RelativeLayout rlUploadingSong;
    Uri audioUri ;
    StorageReference mStorageref;
    StorageTask mUploadsTask ;
    DatabaseReference referenceSongs ;
    MediaMetadataRetriever metadataRetriever;
    byte [] art ;
    String title1, artist1, imageView1 = "", category1, userID;
    EditText title,artist,category;
    ImageView imageView ;
    String TAG = "huhu";
    UploadSongs uploadSongs;
    RecyclerView rcvlist_song_uploaded;

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    FirebaseAuth auth;
    FirebaseUser user;
    TextView textView;
    ImageView settingsAvatar;
    Button btnLogout, btnUpload, buttonUpload;
    Bitmap bitmap;
    int songIndex;



    public SettingsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment SettingsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static SettingsFragment newInstance(String param1, String param2) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        //get Firebase user
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
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
        rcvProgressBar = view.findViewById(R.id.rcvProgressBar);

//        rcvlist_song_uploaded.setLayoutManager(new LinearLayoutManager(requireContext()));
        rcvlist_song_uploaded.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));


        if(user == null) {
            goAuthActivity();
            Toast.makeText(getContext(), "Vui lòng đăng nhập để sử dụng tính năng này", Toast.LENGTH_LONG).show();
        }else {
            userID = user.getUid();
            String info = user.getEmail() != null ? user.getEmail() : user.getDisplayName();
            textView.setText("Xin chào " + info);

            if(user.getPhotoUrl() != null) {
                String url = user.getPhotoUrl().toString();
                Glide.with(this).load(Uri.parse(url)).into(settingsAvatar);
            }
        }
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                auth.signOut();
                goAuthActivity();
            }
        });

        //Nếu UserID lưu trong songs trùng với UserIDLogin thì hiện danh sách bài hát mà 2 user đấy trùng nhau
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference songsRef = database.getReference("songs");

        Query query = songsRef.orderByChild("userID").equalTo(userID);

        FirebaseRecyclerOptions<Songs> options = new FirebaseRecyclerOptions.Builder<Songs>()
                .setQuery(query, Songs.class)
                .build();

        uploadSongs = new UploadSongs(options);
        rcvlist_song_uploaded.setAdapter(uploadSongs);
        ToggleShowHideUI.toggleShowUI(false, rcvProgressBar);

        metadataRetriever = new MediaMetadataRetriever();
        referenceSongs = FirebaseDatabase.getInstance().getReference().child("songs");
        mStorageref = FirebaseStorage.getInstance().getReference().child("songs");

        buttonUpload = view.findViewById(R.id.buttonUplaod);
        btnUpload = view.findViewById(R.id.bt_upload);


        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAudioFiles(view);
            }
        });

        buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadFileToFirebase(view);
            }
        });

        // Truy vấn dữ liệu từ Firebase để tìm giá trị indexSong lớn hơn 0 mà đang còn trống
        songsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int maxSongIndex = 0; // Khởi tạo một giá trị mặc định

                for (DataSnapshot songSnapshot : dataSnapshot.getChildren()) {
                    Integer currentIndex = songSnapshot.child("indexSong").getValue(Integer.class);
                    if (currentIndex != null) {
                        if (currentIndex > maxSongIndex) {
                            maxSongIndex = currentIndex;
                        }
                    }
                }

                // Tìm index trống lớn hơn 0 và cập nhật songIndex
                for (int i = 1; i <= maxSongIndex + 1; i++) {
                    if (isIndexAvailable(i, dataSnapshot)) {
                        songIndex = i;
                        break;
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Xử lý lỗi nếu cần
            }
        });

        return view;
    }

    private void goAuthActivity(){
        Intent unauthIntent = new Intent(getContext(), Login.class);
        startActivity(unauthIntent);
        getActivity().finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        uploadSongs.startListening();
    }

    @Override
    public void onStop() {
        super.onStop();
        uploadSongs.stopListening();

    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void openAudioFiles(View v ){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        startActivityForResult(i,101);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 101 && resultCode  == Activity.RESULT_OK && data.getData() != null){
            audioUri = data.getData();
            if(audioUri == null) {
                Log.d(TAG, "onActivityResult: OK");

            }

            String fileNames = getFileName(audioUri);
            textViewImage.setText(fileNames);

            Log.d("kk", "onActivityResult: OK" + fileNames);

            metadataRetriever.setDataSource(requireContext(), audioUri);
            art = metadataRetriever.getEmbeddedPicture();

            artist.setText(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            title.setText(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            category.setText(metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));

            artist1 = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            title1 = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            category1 = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);


            if(art!= null){
                bitmap = BitmapFactory.decodeByteArray(art,0,art.length);
                imageView.setImageBitmap(bitmap);
                Log.d(TAG, "onActivityResult: " + bitmap);

            }else {
                Log.d(TAG, "onActivityResult: null");
            }
            ToggleShowHideUI.toggleShowUI(true, rlUploadingSong);
        }
        else {
            Log.d(TAG, "onActivityResult: NOt OK");
            ToggleShowHideUI.toggleShowUI(false, rlUploadingSong);
        }


    }

    @SuppressLint("Range")
    private  String getFileName(Uri uri){

        String result = null;
        Context context = requireContext();
        if(uri.getScheme().equals("content")){

            Cursor cursor = context.getContentResolver().query(uri, null,null,null,null);
            try {
                if (cursor != null && cursor.moveToFirst()) {

                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                }
            }
            finally {
                cursor.close();
            }
        }

        if(result == null){
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if(cut != -1){
                result = result.substring(cut +1);

            }
        }
        return  result;
    }

    public  void  uploadFileToFirebase (View v ){
        if(textViewImage.equals("Chưa chọn file audio")){
            Toast.makeText(getContext(), "Vui lòng chọn ảnh!", Toast.LENGTH_SHORT).show();
        }
        else{
            if(mUploadsTask != null && mUploadsTask.isInProgress()){
                Toast.makeText(getContext(), "Bài hát đang được tải lên!", Toast.LENGTH_SHORT).show();
                ToggleShowHideUI.toggleShowUI(true, progressBar);
            }else {
                uploadFiles();
            }
        }

    }

    private void uploadFiles() {

        title1 = title.getText().toString().trim();
        artist1 = artist.getText().toString().trim();
        category1 = category.getText().toString().trim();

        if(audioUri != null && !title1.isEmpty() && !artist1.isEmpty() && !category1.isEmpty()){
            Toast.makeText(getContext(), "Đang tải lên, vui lòng chờ!", Toast.LENGTH_SHORT).show();
            ToggleShowHideUI.toggleShowUI(true, progressBar);
            final  StorageReference storageReference = mStorageref.child(System.currentTimeMillis()+"."+ getFileExtension(audioUri));
            mUploadsTask = storageReference.putFile(audioUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                    storageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            FirebaseDatabase database = FirebaseDatabase.getInstance();
                            DatabaseReference songsRef = database.getReference("songs");

                            //process image
                            String base64Image = "";
                            if(bitmap != null){
                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                                byte[] imageBytes = byteArrayOutputStream.toByteArray();
                                base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                            }

                            String songId = uuid.createTransactionID();
                            Songs uploadSong = new Songs(songIndex, songId, title1, artist1, category1, base64Image, uri.toString(), userID);
                            String uploadId = referenceSongs.push().getKey();
                            referenceSongs.child(uploadId).setValue(uploadSong);

                            //change UI
                            ToggleShowHideUI.toggleShowUI(false, progressBar);
                            Toast.makeText(getContext(), "Tải lên thành công!", Toast.LENGTH_SHORT).show();
                            ToggleShowHideUI.toggleShowUI(false, rlUploadingSong);

                        }
                    });

                }
            }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {

                    double progess = (100.0* taskSnapshot.getBytesTransferred()/taskSnapshot.getTotalByteCount());
                    progressBar.setProgress((int) progess);

                }
            });

        }else {
            Toast.makeText(getContext(), "File tải lên chưa đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
        }
    }

    private  String getFileExtension(Uri audioUri){

        ContentResolver contentResolver = getContext().getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();

        return  mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(audioUri));

    }


    public class YourFragment extends Fragment {
        // ... Các phần khác của mã của Fragment ...
    }

    private boolean isIndexAvailable(int index, DataSnapshot dataSnapshot) {
        for (DataSnapshot songSnapshot : dataSnapshot.getChildren()) {
            Integer currentIndex = songSnapshot.child("indexSong").getValue(Integer.class);
            if (currentIndex != null && currentIndex == index) {
                return false; // Index đã tồn tại, không khả dụng
            }
        }
        return true; // Index trống, khả dụng
    }
}