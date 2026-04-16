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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    List<Songs> songs = new ArrayList<>(); // Top Songs
    List<Songs> groupedSingers = new ArrayList<>();
    List<Songs> groupedCategories = new ArrayList<>();
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
        tvUserNameHeader = tvUserNameHeader = view.findViewById(R.id.tvUserNameHeader);
        tvShareMusicPrompt = view.findViewById(R.id.tvShareMusicPrompt);
    }

    private void setupUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvUserNameHeader.setText(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).into(ivUserAvatarHeader);
            }

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

        bestSingersAdapter = new Main_BestSingersAdapter(getContext(), groupedSingers);
        rcvBestSingers.setAdapter(bestSingersAdapter);

        bestCategoriesAdapter = new Main_BestCategoriesAdapter(getContext(), groupedCategories);
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
                Map<String, List<Songs>> singerGroups = new HashMap<>();
                Map<String, List<Songs>> categoryGroups = new HashMap<>();

                for (DataSnapshot dataSnapshotItem : snapshot.getChildren()) {
                    Songs song = dataSnapshotItem.getValue(Songs.class);
                    if (song != null) {
                        if (song.getId() == null) song.setId(dataSnapshotItem.getKey());
                        allSongs.add(song);
                        
                        // Group by Singer
                        String artist = song.getArtist() != null ? song.getArtist() : "Nghệ sĩ";
                        if (!singerGroups.containsKey(artist)) singerGroups.put(artist, new ArrayList<>());
                        singerGroups.get(artist).add(song);

                        // Group by Category
                        String category = song.getCategory() != null ? song.getCategory() : "Âm nhạc";
                        if (!categoryGroups.containsKey(category)) categoryGroups.put(category, new ArrayList<>());
                        categoryGroups.get(category).add(song);
                    }
                }
                
                processHomeFeed(followingIds);
                processBestSongs();
                processBestSingers(singerGroups);
                processBestCategories(categoryGroups);
                
                notifyAdapters();
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void processHomeFeed(Set<String> followingIds) {
        List<Songs> followedSongs = new ArrayList<>();
        List<Songs> otherSongs = new ArrayList<>();

        for (Songs song : allSongs) {
            if (followingIds.contains(song.getUserID())) followedSongs.add(song);
            else otherSongs.add(song);
        }

        // Sort: Likes + Following Priority
        Collections.sort(followedSongs, (s1, s2) -> Long.compare(s2.getLikes(), s1.getLikes()));
        Collections.sort(otherSongs, (s1, s2) -> Long.compare(s2.getLikes(), s1.getLikes()));

        allFeedSongs.clear();
        allFeedSongs.addAll(followedSongs);
        allFeedSongs.addAll(otherSongs);

        feedSongs.clear();
        feedSongs.addAll(allFeedSongs);
    }

    private void processBestSongs() {
        songs.clear();
        songs.addAll(allSongs);
        // Sort by Likes
        Collections.sort(songs, (s1, s2) -> Long.compare(s2.getLikes(), s1.getLikes()));
    }

    private void processBestSingers(Map<String, List<Songs>> groups) {
        groupedSingers.clear();
        List<Map.Entry<String, List<Songs>>> sortedEntries = new ArrayList<>(groups.entrySet());
        
        // Sort singers by total likes
        Collections.sort(sortedEntries, (e1, e2) -> {
            long l1 = 0; for(Songs s : e1.getValue()) l1 += s.getLikes();
            long l2 = 0; for(Songs s : e2.getValue()) l2 += s.getLikes();
            return Long.compare(l2, l1);
        });

        for (Map.Entry<String, List<Songs>> entry : sortedEntries) {
            // Represent singer by the first song
            Songs representative = entry.getValue().get(0);
            groupedSingers.add(representative);
        }
    }

    private void processBestCategories(Map<String, List<Songs>> groups) {
        groupedCategories.clear();
        for (Map.Entry<String, List<Songs>> entry : groups.entrySet()) {
            Songs representative = entry.getValue().get(0);
            groupedCategories.add(representative);
        }
    }

    public void searchSongs(String query) {
        if (query == null || query.isEmpty()) {
            feedSongs.clear();
            feedSongs.addAll(allFeedSongs);
            // Re-process groups or use allSongs
            processBestSongs();
        } else {
            String lowerQuery = query.toLowerCase();
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

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (homeFeedAdapter != null) {
            homeFeedAdapter.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void notifyAdapters() {
        if (bestSongsAdapter != null) bestSongsAdapter.notifyDataSetChanged();
        if (bestSingersAdapter != null) bestSingersAdapter.notifyDataSetChanged();
        if (bestCategoriesAdapter != null) bestCategoriesAdapter.notifyDataSetChanged();
        if (homeFeedAdapter != null) homeFeedAdapter.notifyDataSetChanged();
    }
}