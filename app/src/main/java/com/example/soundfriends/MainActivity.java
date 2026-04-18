package com.example.soundfriends;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.viewpager2.widget.ViewPager2;

import com.example.soundfriends.adapter.ViewPagerAdapter;
import com.example.soundfriends.services.MusicService;
import com.example.soundfriends.auth.Login;
import com.example.soundfriends.fragments.HomeFragment;
import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.fragments.SearchFragment;
import com.example.soundfriends.utils.ZoomOutPageTransformer;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

public class MainActivity extends AppCompatActivity {
    ViewPager2 viewPager;
    BottomNavigationView bottomNavigationView;
    AutoCompleteTextView searchBar;
    ImageButton btnClearSearch;
    private Timer timer;
    
    // Mini Player components
    private View miniPlayerCard;
    private ImageView miniPlayerImg;
    private TextView miniPlayerTitle, miniPlayerArtist;
    private ImageButton miniPlayerPlayPause, miniPlayerClose;
    private ProgressBar miniPlayerProgress;
    private boolean isMiniPlayerDismissed = false;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private final Handler progressHandler = new Handler();
    private String currentPlayingSongId = "";

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaController != null && mediaController.isPlaying()) {
                long position = mediaController.getCurrentPosition();
                long duration = mediaController.getDuration();
                if (duration > 0) {
                    miniPlayerProgress.setProgress((int) (position * 100 / duration));
                }
            }
            progressHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = (ViewPager2) findViewById(R.id.view_pager);
        viewPager.setUserInputEnabled(false);
        // Tối ưu 1: Giữ các Fragment trong bộ nhớ để tránh khởi tạo lại gây lag/crash
        viewPager.setOffscreenPageLimit(2);

        bottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_nav);
        searchBar = findViewById(R.id.search_bar);
        btnClearSearch = findViewById(R.id.btnSearch);

        syncUserToDatabase();

        initMiniPlayer();
        setupMiniPlayerController();

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position){
                    case 0:
                        bottomNavigationView.getMenu().findItem(R.id.menu_home).setChecked(true);
                        break;
                    case 1:
                        bottomNavigationView.getMenu().findItem(R.id.menu_search).setChecked(true);
                        break;
                    case 2:
                        bottomNavigationView.getMenu().findItem(R.id.menu_settings).setChecked(true);
                        break;
                }
                updateMiniPlayerVisibility(); // Cập nhật visibility khi chuyển tab
            }
        });
        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(item.getItemId() == R.id.menu_home){
                    viewPager.setCurrentItem(0);
                } else if (item.getItemId() == R.id.menu_search) {
                    viewPager.setCurrentItem(1);
                } else viewPager.setCurrentItem(2);

                return true;
            }
        });

        searchBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                viewPager.setCurrentItem(1);
            }
        });

        searchBar.setOnClickListener(v -> {
            viewPager.setCurrentItem(1);
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && viewPager.getCurrentItem() != 1) {
                    viewPager.setCurrentItem(1);
                }
                
                // Tối ưu 2: Debounce Search - Chỉ gọi search sau khi người dùng ngừng gõ 500ms
                if (timer != null) {
                    timer.cancel();
                }
                
                if (s.length() > 0) {
                    btnClearSearch.setVisibility(View.VISIBLE);
                } else {
                    btnClearSearch.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> performSearch(s.toString()));
                    }
                }, 500);
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            searchBar.setText("");
        });
        
        btnClearSearch.setVisibility(View.GONE);
    }

    private void syncUserToDatabase() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            String uid = firebaseUser.getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        String name = firebaseUser.getDisplayName();
                        if (name == null || name.isEmpty()) {
                            String suffix = uid.length() > 4 ? uid.substring(uid.length() - 4) : uid;
                            name = "user_" + suffix;
                        }
                        String avatar = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "";
                        User newUser = new User(uid, name, firebaseUser.getEmail(), avatar);
                        userRef.setValue(newUser);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    public void searchInFragment(String query) {
        if (searchBar != null) {
            searchBar.setText(query);
        }
        if (viewPager != null) {
            viewPager.setCurrentItem(1);
        }
    }

    private void performSearch(String query) {
        // Tối ưu 3: Kiểm tra an toàn trước khi gọi fragment
        if (isFinishing() || isDestroyed()) return;

        SearchFragment searchFragment = (SearchFragment) getSupportFragmentManager().findFragmentByTag("f1");
        if (searchFragment != null && searchFragment.isAdded()) {
            searchFragment.searchSongs(query);
        }

        HomeFragment homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if (homeFragment != null && homeFragment.isAdded()) {
            homeFragment.searchSongs(query);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        HomeFragment homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if (homeFragment != null && homeFragment.isAdded()) {
            homeFragment.handleActivityResult(requestCode, resultCode, data);
        }
    }
    
    private void initMiniPlayer() {
        miniPlayerCard = findViewById(R.id.miniPlayerCard);
        miniPlayerImg = findViewById(R.id.miniPlayerImg);
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle);
        miniPlayerArtist = findViewById(R.id.miniPlayerArtist);
        miniPlayerPlayPause = findViewById(R.id.miniPlayerPlayPause);
        miniPlayerProgress = findViewById(R.id.miniPlayerProgress);
        miniPlayerClose = findViewById(R.id.miniPlayerClose);

        miniPlayerClose.setOnClickListener(v -> {
            isMiniPlayerDismissed = true;
            if (mediaController != null) {
                mediaController.pause();
            }
            miniPlayerCard.setVisibility(View.GONE);
        });

        miniPlayerPlayPause.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                } else {
                    isMiniPlayerDismissed = false; // Reset khi người dùng chủ động nhấn Play
                    mediaController.play();
                    updateMiniPlayerVisibility();
                }
            }
        });

        miniPlayerCard.setOnClickListener(v -> {
            if (currentPlayingSongId != null && !currentPlayingSongId.isEmpty()) {
                Intent intent = new Intent(this, Song.class);
                intent.putExtra("songId", currentPlayingSongId);
                startActivity(intent);
            }
        });
    }

    private void setupMiniPlayerController() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        if (mediaItem != null) {
                            currentPlayingSongId = mediaItem.mediaId;
                        }
                        updateMiniPlayerUI(mediaItem);
                        updateMiniPlayerVisibility();
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        miniPlayerPlayPause.setImageResource(isPlaying ? R.drawable.pause : R.drawable.play);
                        if (isPlaying) {
                            isMiniPlayerDismissed = false; // Reset trạng thái ẩn khi nhạc bắt đầu phát
                            updateMiniPlayerVisibility();
                            progressHandler.post(updateProgressRunnable);
                        } else {
                            progressHandler.removeCallbacks(updateProgressRunnable);
                        }
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                            updateMiniPlayerVisibility();
                            updateMiniPlayerUI(mediaController.getCurrentMediaItem());
                        } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                            if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.GONE);
                        }
                    }
                });

                if (mediaController.getPlaybackState() != Player.STATE_IDLE) {
                    MediaItem item = mediaController.getCurrentMediaItem();
                    if (item != null) currentPlayingSongId = item.mediaId;
                    updateMiniPlayerVisibility();
                    updateMiniPlayerUI(item);
                    miniPlayerPlayPause.setImageResource(mediaController.isPlaying() ? R.drawable.pause : R.drawable.play);
                }

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void updateMiniPlayerUI(MediaItem mediaItem) {
        if (mediaItem != null && mediaItem.mediaMetadata != null) {
            // Khi có bài hát mới hoặc phát lại bài cũ, reset trạng thái dismissed
            if (isMiniPlayerDismissed) {
                isMiniPlayerDismissed = false;
            }

            miniPlayerTitle.setText(mediaItem.mediaMetadata.title);
            miniPlayerArtist.setText(mediaItem.mediaMetadata.artist);
            
            if (mediaItem.mediaMetadata.artworkData != null) {
                byte[] data = mediaItem.mediaMetadata.artworkData;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                miniPlayerImg.setImageBitmap(bitmap);
            } else {
                miniPlayerImg.setImageResource(R.drawable.logo);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMiniPlayerVisibility();
    }

    public void updateMiniPlayerVisibility() {
        if (isMiniPlayerDismissed) {
            if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.GONE);
            return;
        }
        if (mediaController == null || mediaController.getPlaybackState() == Player.STATE_IDLE || mediaController.getPlaybackState() == Player.STATE_ENDED) {
            if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.GONE);
            return;
        }

        if (viewPager.getCurrentItem() != 0) {
            if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.VISIBLE);
            return;
        }

        HomeFragment homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("f0");
        if (homeFragment != null && homeFragment.isAdded()) {
            boolean isVisibleOnFeed = homeFragment.isSongVisible(currentPlayingSongId);
            if (miniPlayerCard != null) {
                miniPlayerCard.setVisibility(isVisibleOnFeed ? View.GONE : View.VISIBLE);
            }
        } else {
            if (miniPlayerCard != null) miniPlayerCard.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        if (timer != null) {
            timer.cancel();
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        progressHandler.removeCallbacks(updateProgressRunnable);
        super.onDestroy();
    }
}