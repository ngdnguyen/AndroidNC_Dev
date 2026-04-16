package com.example.soundfriends;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.soundfriends.adapter.HomeFeedAdapter;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.fragments.Model.User;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends AppCompatActivity {

    private ImageButton btnBack, btnEditAvatar;
    private CircleImageView ivUserAvatar;
    private TextView tvUserName, tvUserBio, tvFollowersCount, tvFollowingCount, tvInfoBio;
    private Button btnAction, btnEditProfile;
    private RecyclerView rcvUserPosts;
    private ProgressBar progressBar;
    private TabLayout tabLayout;
    private LinearLayout layoutInfo;
    private View llFollowers, llFollowing;

    private String profileUserID;
    private String currentUserID;
    private boolean isMyProfile = false;
    private boolean isFollowing = false;

    private HomeFeedAdapter adapter;
    private List<Songs> userSongs = new ArrayList<>();
    private DatabaseReference databaseReference;

    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        profileUserID = getIntent().getStringExtra("userID");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserID = currentUser.getUid();
            isMyProfile = profileUserID.equals(currentUserID);
        }

        initViews();
        setupRecyclerView();
        loadUserProfile();
        loadUserPosts();
        checkFollowStatus();
        setupFollowStats();
        setupTabs();

        btnBack.setOnClickListener(v -> finish());

        if (isMyProfile) {
            btnAction.setVisibility(View.GONE);
            btnEditProfile.setVisibility(View.VISIBLE);
            btnEditAvatar.setVisibility(View.VISIBLE);
            
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
            btnEditAvatar.setOnClickListener(v -> chooseImage());
        } else {
            btnAction.setOnClickListener(v -> toggleFollow());
        }

        llFollowers.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserListActivity.class);
            intent.putExtra("userID", profileUserID);
            intent.putExtra("type", "Followers");
            startActivity(intent);
        });
        llFollowing.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserListActivity.class);
            intent.putExtra("userID", profileUserID);
            intent.putExtra("type", "Following");
            startActivity(intent);
        });
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        btnEditAvatar = findViewById(R.id.btnEditAvatar);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserBio = findViewById(R.id.tvUserBio);
        tvFollowersCount = findViewById(R.id.tvFollowersCount);
        tvFollowingCount = findViewById(R.id.tvFollowingCount);
        tvInfoBio = findViewById(R.id.tvInfoBio);
        btnAction = findViewById(R.id.btnAction);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        rcvUserPosts = findViewById(R.id.rcvUserPosts);
        progressBar = findViewById(R.id.progressBar);
        tabLayout = findViewById(R.id.tabLayout);
        layoutInfo = findViewById(R.id.layoutInfo);
        
        // Find parent layouts for stats to make them clickable
        llFollowers = (View) tvFollowersCount.getParent();
        llFollowing = (View) tvFollowingCount.getParent();
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    rcvUserPosts.setVisibility(View.VISIBLE);
                    layoutInfo.setVisibility(View.GONE);
                } else {
                    rcvUserPosts.setVisibility(View.GONE);
                    layoutInfo.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new HomeFeedAdapter(this, userSongs);
        rcvUserPosts.setLayoutManager(new LinearLayoutManager(this));
        rcvUserPosts.setAdapter(adapter);
    }

    private void loadUserProfile() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(profileUserID);
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    tvUserName.setText(user.getName());
                    String bio = (user.getBio() != null && !user.getBio().isEmpty()) ? user.getBio() : "Chưa có tiểu sử";
                    tvUserBio.setText(bio);
                    tvInfoBio.setText(bio);

                    if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                        Glide.with(ProfileActivity.this).load(user.getAvatar()).placeholder(R.drawable.empty_avatar).into(ivUserAvatar);
                    }
                } else if (isMyProfile) {
                    FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (firebaseUser != null) {
                        String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : firebaseUser.getEmail();
                        tvUserName.setText(name);
                        if (firebaseUser.getPhotoUrl() != null) {
                            Glide.with(ProfileActivity.this).load(firebaseUser.getPhotoUrl()).into(ivUserAvatar);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadUserPosts() {
        progressBar.setVisibility(View.VISIBLE);
        databaseReference = FirebaseDatabase.getInstance().getReference("songs");
        databaseReference.orderByChild("userID").equalTo(profileUserID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userSongs.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Songs song = ds.getValue(Songs.class);
                    if (song != null) {
                        userSongs.add(song);
                    }
                }
                Collections.reverse(userSongs);
                adapter.notifyDataSetChanged();
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void checkFollowStatus() {
        if (isMyProfile) return;
        DatabaseReference followRef = FirebaseDatabase.getInstance().getReference("following")
                .child(currentUserID).child(profileUserID);
        followRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isFollowing = snapshot.exists();
                if (isFollowing) {
                    btnAction.setText("Đang theo dõi");
                    btnAction.setBackgroundTintList(getColorStateList(R.color.black));
                } else {
                    btnAction.setText("Theo dõi");
                    btnAction.setBackgroundTintList(getColorStateList(R.color.primary_pink));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupFollowStats() {
        DatabaseReference followersRef = FirebaseDatabase.getInstance().getReference("followers").child(profileUserID);
        followersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tvFollowersCount.setText(String.valueOf(snapshot.getChildrenCount()));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        DatabaseReference followingRef = FirebaseDatabase.getInstance().getReference("following").child(profileUserID);
        followingRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tvFollowingCount.setText(String.valueOf(snapshot.getChildrenCount()));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void toggleFollow() {
        if (currentUserID == null) return;
        DatabaseReference followingRef = FirebaseDatabase.getInstance().getReference("following")
                .child(currentUserID).child(profileUserID);
        DatabaseReference followersRef = FirebaseDatabase.getInstance().getReference("followers")
                .child(profileUserID).child(currentUserID);

        if (isFollowing) {
            followingRef.removeValue();
            followersRef.removeValue();
        } else {
            followingRef.setValue(true);
            followersRef.setValue(true);
        }
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        EditText etName = view.findViewById(R.id.etEditName);
        EditText etBio = view.findViewById(R.id.etEditBio);
        Button btnSave = view.findViewById(R.id.btnSaveProfile);

        etName.setText(tvUserName.getText().toString());
        etBio.setText(tvUserBio.getText().toString().equals("Chưa có tiểu sử") ? "" : tvUserBio.getText().toString());

        AlertDialog dialog = builder.setView(view).create();
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String newBio = etBio.getText().toString().trim();
            
            if (newName.isEmpty()) {
                Toast.makeText(this, "Tên không được để trống", Toast.LENGTH_SHORT).show();
                return;
            }

            updateProfileInfo(newName, newBio);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updateProfileInfo(String name, String bio) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserID);
        userRef.child("name").setValue(name);
        userRef.child("bio").setValue(bio);
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build();
            user.updateProfile(profileUpdates);
        }
        
        Toast.makeText(this, "Đã cập nhật thông tin", Toast.LENGTH_SHORT).show();
    }

    private void chooseImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Chọn ảnh đại diện"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            filePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                ivUserAvatar.setImageBitmap(bitmap);
                uploadAvatar(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadAvatar(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserID);
        userRef.child("avatar").setValue("data:image/jpeg;base64," + base64Image);
        
        Toast.makeText(this, "Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
    }
}