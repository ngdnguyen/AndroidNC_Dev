package com.example.soundfriends.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.soundfriends.ProfileActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.UploadActivity;
import com.example.soundfriends.adapter.HomeFeedAdapter;
import com.example.soundfriends.adapter.Main_BestCategoriesAdapter;
import com.example.soundfriends.adapter.Main_BestSingersAdapter;
import com.example.soundfriends.adapter.Main_BestSongsAdapter;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.utils.WrapContentLinearLayoutManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;

public class HomeFragment extends Fragment {
    RecyclerView rcvBestSongs, rcvBestSingers, rcvBestCategories, rcvHomeFeed;
    Main_BestSongsAdapter bestSongsAdapter;
    Main_BestSingersAdapter bestSingersAdapter;
    Main_BestCategoriesAdapter bestCategoriesAdapter;
    HomeFeedAdapter homeFeedAdapter;
    SwipeRefreshLayout swipeRefreshLayout;

    DatabaseReference databaseReference;
    List<Songs> songs = new ArrayList<>();
    List<Songs> feedSongs = new ArrayList<>();
    
    List<Songs> allSongs = new ArrayList<>();
    List<Songs> allFeedSongs = new ArrayList<>();

    CircleImageView ivUserAvatarHeader;
    TextView tvUserNameHeader, tvShareMusicPrompt;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initViews(view);
        setupUserData();
        setupRecyclerViews();
        getSongsData();

        tvShareMusicPrompt.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UploadActivity.class);
            startActivity(intent);
        });

        ivUserAvatarHeader.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                Intent intent = new Intent(getActivity(), ProfileActivity.class);
                intent.putExtra("userID", user.getUid());
                startActivity(intent);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(this::getSongsData);

        return view;
    }

    private void initViews(View view) {
        rcvBestSongs = view.findViewById(R.id.rcv_best_songs);
        rcvBestSingers = view.findViewById(R.id.rcv_best_singer);
        rcvBestCategories = view.findViewById(R.id.rcv_best_categories);
        rcvHomeFeed = view.findViewById(R.id.rcv_home_feed);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        ivUserAvatarHeader = view.findViewById(R.id.ivUserAvatarHeader);
        tvUserNameHeader = view.findViewById(R.id.tvUserNameHeader);
        tvShareMusicPrompt = view.findViewById(R.id.tvShareMusicPrompt);
    }

    private void setupUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Hiển thị mặc định từ Auth
            tvUserNameHeader.setText(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).into(ivUserAvatarHeader);
            }

            // Đồng bộ từ database để lấy thông tin cá nhân hóa mới nhất
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            userRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String name = snapshot.child("name").getValue(String.class);
                        String avatar = snapshot.child("avatar").getValue(String.class);
                        if (name != null) tvUserNameHeader.setText(name);
                        if (avatar != null && !avatar.isEmpty()) {
                            Glide.with(HomeFragment.this).load(avatar).placeholder(R.drawable.empty_avatar).into(ivUserAvatarHeader);
                        }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void setupRecyclerViews() {
        rcvBestSongs.setLayoutManager(new WrapContentLinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rcvBestSingers.setLayoutManager(new WrapContentLinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rcvBestCategories.setLayoutManager(new WrapContentLinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        
        rcvHomeFeed.setLayoutManager(new LinearLayoutManager(getContext()));
        rcvHomeFeed.setNestedScrollingEnabled(false);

        bestSongsAdapter = new Main_BestSongsAdapter(getContext(), songs);
        rcvBestSongs.setAdapter(bestSongsAdapter);

        bestSingersAdapter = new Main_BestSingersAdapter(getContext(), songs);
        rcvBestSingers.setAdapter(bestSingersAdapter);

        bestCategoriesAdapter = new Main_BestCategoriesAdapter(getContext(), songs);
        rcvBestCategories.setAdapter(bestCategoriesAdapter);

        homeFeedAdapter = new HomeFeedAdapter(getContext(), feedSongs);
        rcvHomeFeed.setAdapter(homeFeedAdapter);
    }

    private void getSongsData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(true);

        // Lấy danh sách đang follow để ưu tiên bài đăng
        DatabaseReference followingRef = FirebaseDatabase.getInstance().getReference("following").child(currentUser.getUid());
        followingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot followingSnapshot) {
                Set<String> followingIds = new HashSet<>();
                for (DataSnapshot ds : followingSnapshot.getChildren()) {
                    followingIds.add(ds.getKey());
                }
                loadSongsFromFirebase(followingIds);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadSongsFromFirebase(new HashSet<>());
            }
        });
    }

    private void loadSongsFromFirebase(Set<String> followingIds) {
        databaseReference = FirebaseDatabase.getInstance().getReference("songs");
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allSongs.clear();
                List<Songs> followedSongs = new ArrayList<>();
                List<Songs> otherSongs = new ArrayList<>();

                for (DataSnapshot dataSnapshotItem : snapshot.getChildren()) {
                    Songs song = dataSnapshotItem.getValue(Songs.class);
                    if (song != null) {
                        if (song.getId() == null) song.setId(dataSnapshotItem.getKey());
                        allSongs.add(song);
                        
                        if (followingIds.contains(song.getUserID())) {
                            followedSongs.add(song);
                        } else {
                            otherSongs.add(song);
                        }
                    }
                }
                
                // Sắp xếp theo thời gian mới nhất (timestamp giảm dần)
                Collections.sort(followedSongs, (s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
                Collections.sort(otherSongs, (s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
                
                allFeedSongs.clear();
                allFeedSongs.addAll(followedSongs);
                allFeedSongs.addAll(otherSongs);
                
                songs.clear();
                songs.addAll(allSongs);
                // Sắp xếp danh sách chung theo thời gian
                Collections.sort(songs, (s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));

                feedSongs.clear();
                feedSongs.addAll(allFeedSongs);
                
                notifyAdapters();
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    public void searchSongs(String query) {
        if (query == null || query.isEmpty()) {
            songs.clear();
            songs.addAll(allSongs);
            // Sắp xếp lại khi xóa search
            Collections.sort(songs, (s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
            
            feedSongs.clear();
            feedSongs.addAll(allFeedSongs);
        } else {
            String lowerQuery = query.toLowerCase();
            songs.clear();
            for (Songs s : allSongs) {
                if ((s.getTitle() != null && s.getTitle().toLowerCase().contains(lowerQuery)) ||
                    (s.getArtist() != null && s.getArtist().toLowerCase().contains(lowerQuery))) {
                    songs.add(s);
                }
            }
            // Sắp xếp kết quả search theo thời gian
            Collections.sort(songs, (s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));

            feedSongs.clear();
            for (Songs s : allFeedSongs) {
                if ((s.getTitle() != null && s.getTitle().toLowerCase().contains(lowerQuery)) ||
                    (s.getPostContent() != null && s.getPostContent().toLowerCase().contains(lowerQuery))) {
                    feedSongs.add(s);
                }
            }
        }
        notifyAdapters();
    }

    private void notifyAdapters() {
        if (bestSongsAdapter != null) bestSongsAdapter.notifyDataSetChanged();
        if (bestSingersAdapter != null) bestSingersAdapter.notifyDataSetChanged();
        if (bestCategoriesAdapter != null) bestCategoriesAdapter.notifyDataSetChanged();
        if (homeFeedAdapter != null) homeFeedAdapter.notifyDataSetChanged();
    }
}