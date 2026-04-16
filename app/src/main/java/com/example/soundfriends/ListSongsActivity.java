package com.example.soundfriends;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soundfriends.adapter.SearchSongAdapter;
import com.example.soundfriends.fragments.Model.Songs;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListSongsActivity extends AppCompatActivity {

    private RecyclerView rcvListSongs;
    private SearchSongAdapter adapter;
    private List<Songs> songsList = new ArrayList<>();
    private String type, value;
    private TextView tvTitle;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_songs);

        type = getIntent().getStringExtra("type"); // "artist" or "category"
        value = getIntent().getStringExtra("value"); // e.g., "Sơn Tùng M-TP" or "Pop"

        rcvListSongs = findViewById(R.id.rcvListSongs);
        tvTitle = findViewById(R.id.tvTitle);
        btnBack = findViewById(R.id.btnBack);

        tvTitle.setText(value);
        btnBack.setOnClickListener(v -> finish());

        rcvListSongs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchSongAdapter(this, songsList);
        rcvListSongs.setAdapter(adapter);

        loadSongs();
    }

    private void loadSongs() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("songs");
        String queryField = type.equals("artist") ? "artist" : "category";
        
        ref.orderByChild(queryField).equalTo(value).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                songsList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Songs song = ds.getValue(Songs.class);
                    if (song != null) {
                        if (song.getId() == null) song.setId(ds.getKey());
                        songsList.add(song);
                    }
                }
                // Sort by likes
                Collections.sort(songsList, (s1, s2) -> Long.compare(s2.getLikes(), s1.getLikes()));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}