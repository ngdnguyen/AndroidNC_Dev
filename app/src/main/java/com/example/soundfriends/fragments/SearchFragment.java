package com.example.soundfriends.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.example.soundfriends.R;
import com.example.soundfriends.adapter.SearchSongAdapter;
import com.example.soundfriends.fragments.Model.Songs;
import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.utils.WrapContentLinearLayoutManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment implements AdapterView.OnItemSelectedListener {

    RecyclerView recyclerView;
    SearchSongAdapter searchSongAdapter;
    List<Songs> allSongs = new ArrayList<>();
    List<User> allUsers = new ArrayList<>();
    List<Object> displayedItems = new ArrayList<>();
    
    DatabaseReference songsRef, usersRef;

    public SearchFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        recyclerView = view.findViewById(R.id.rcvlist_search);
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));

        searchSongAdapter = new SearchSongAdapter(requireContext(), displayedItems);
        recyclerView.setAdapter(searchSongAdapter);

        fetchData();

        return view;
    }

    private void fetchData() {
        songsRef = FirebaseDatabase.getInstance().getReference().child("songs");
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");

        // Fetch Songs
        songsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allSongs.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Songs song = ds.getValue(Songs.class);
                    if (song != null) {
                        if (song.getId() == null) song.setId(ds.getKey());
                        allSongs.add(song);
                    }
                }
                updateDisplayedItems("");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Fetch Users
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    if (user != null) {
                        if (user.getUserID() == null) user.setUserID(ds.getKey());
                        allUsers.add(user);
                    }
                }
                updateDisplayedItems("");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    public void searchSongs(String query) {
        updateDisplayedItems(query);
    }

    private void updateDisplayedItems(String query) {
        displayedItems.clear();
        String lowerQuery = query != null ? query.toLowerCase() : "";

        if (lowerQuery.isEmpty()) {
            displayedItems.addAll(allSongs);
            displayedItems.addAll(allUsers);
        } else {
            // Filter Users first
            for (User user : allUsers) {
                if (user.getName() != null && user.getName().toLowerCase().contains(lowerQuery)) {
                    displayedItems.add(user);
                }
            }
            // Then filter Songs
            for (Songs song : allSongs) {
                if ((song.getTitle() != null && song.getTitle().toLowerCase().contains(lowerQuery)) ||
                    (song.getArtist() != null && song.getArtist().toLowerCase().contains(lowerQuery)) ||
                    (song.getCategory() != null && song.getCategory().toLowerCase().contains(lowerQuery)) ||
                    (song.getPostContent() != null && song.getPostContent().toLowerCase().contains(lowerQuery))) {
                    displayedItems.add(song);
                }
            }
        }
        if (searchSongAdapter != null) {
            searchSongAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {}

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}