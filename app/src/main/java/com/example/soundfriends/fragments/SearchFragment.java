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
    List<Songs> displayedSongs = new ArrayList<>();
    DatabaseReference databaseReference;
    ValueEventListener valueEventListener;

    public SearchFragment() {
        // Required empty public constructor
    }

    public static SearchFragment newInstance(String param1, String param2) {
        SearchFragment fragment = new SearchFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        recyclerView = view.findViewById(R.id.rcvlist_search);
        recyclerView.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));

        searchSongAdapter = new SearchSongAdapter(requireContext(), displayedSongs);
        recyclerView.setAdapter(searchSongAdapter);

        fetchSongs();

        return view;
    }

    private void fetchSongs() {
        databaseReference = FirebaseDatabase.getInstance().getReference().child("songs");
        valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allSongs.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Songs song = dataSnapshot.getValue(Songs.class);
                    if (song != null) {
                        if (song.getId() == null) {
                            song.setId(dataSnapshot.getKey());
                        }
                        allSongs.add(song);
                    }
                }
                updateDisplayedSongs("");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        databaseReference.addValueEventListener(valueEventListener);
    }

    public void searchSongs(String query) {
        updateDisplayedSongs(query);
    }

    private void updateDisplayedSongs(String query) {
        displayedSongs.clear();
        if (query == null || query.isEmpty()) {
            displayedSongs.addAll(allSongs);
        } else {
            String lowerQuery = query.toLowerCase();
            for (Songs song : allSongs) {
                if ((song.getTitle() != null && song.getTitle().toLowerCase().contains(lowerQuery)) ||
                    (song.getArtist() != null && song.getArtist().toLowerCase().contains(lowerQuery)) ||
                    (song.getCategory() != null && song.getCategory().toLowerCase().contains(lowerQuery))) {
                    displayedSongs.add(song);
                }
            }
        }
        if (searchSongAdapter != null) {
            searchSongAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (databaseReference != null && valueEventListener != null) {
            databaseReference.removeEventListener(valueEventListener);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {}

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}