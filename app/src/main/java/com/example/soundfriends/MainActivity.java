package com.example.soundfriends;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.soundfriends.adapter.ViewPagerAdapter;
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

public class MainActivity extends AppCompatActivity {
    ViewPager2 viewPager;
    BottomNavigationView bottomNavigationView;
    AutoCompleteTextView searchBar;
    ImageButton btnClearSearch;
    private Timer timer;

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
    
    @Override
    protected void onDestroy() {
        if (timer != null) {
            timer.cancel();
        }
        super.onDestroy();
    }
}