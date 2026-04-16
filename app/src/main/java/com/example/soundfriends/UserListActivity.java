package com.example.soundfriends;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.utils.ImageUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class UserListActivity extends AppCompatActivity {

    private RecyclerView rcvUserList;
    private UserAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private String userID, type;
    private TextView tvToolbarTitle;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        userID = getIntent().getStringExtra("userID");
        type = getIntent().getStringExtra("type");

        if (type == null) type = "Following";

        rcvUserList = findViewById(R.id.rcvUserList);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        btnBack = findViewById(R.id.btnBack);

        tvToolbarTitle.setText(type.equals("Followers") ? "Người theo dõi" : "Đang theo dõi");
        btnBack.setOnClickListener(v -> finish());

        rcvUserList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(userList);
        rcvUserList.setAdapter(adapter);

        loadUsers();
    }

    private void loadUsers() {
        if (userID == null) return;
        
        String path = type.equals("Followers") ? "followers" : "following";
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path).child(userID);
        
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userList.clear();
                adapter.notifyDataSetChanged();
                if (!snapshot.exists()) return;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String otherUserId = ds.getKey();
                    if (otherUserId != null) {
                        fetchUserDetails(otherUserId);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchUserDetails(String uid) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    if (user.getUserID() == null || user.getUserID().isEmpty()) {
                        user.setUserID(snapshot.getKey());
                    }
                    
                    // Kiểm tra tránh trùng lặp
                    boolean exists = false;
                    for (User u : userList) {
                        if (u.getUserID() != null && u.getUserID().equals(user.getUserID())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        userList.add(user);
                        adapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
        private List<User> users;

        public UserAdapter(List<User> users) { this.users = users; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = users.get(position);
            holder.tvName.setText(user.getName());
            holder.tvBio.setText(user.getBio() != null && !user.getBio().isEmpty() ? user.getBio() : "Chưa có tiểu sử");
            ImageUtils.loadAvatar(UserListActivity.this, user.getAvatar(), holder.ivAvatar);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(UserListActivity.this, ProfileActivity.class);
                intent.putExtra("userID", user.getUserID());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return users.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvBio;
            ImageView ivAvatar;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvUserName);
                tvBio = itemView.findViewById(R.id.tvUserBio);
                ivAvatar = itemView.findViewById(R.id.ivUserAvatar);
            }
        }
    }
}
