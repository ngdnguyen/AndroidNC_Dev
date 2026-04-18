package com.example.soundfriends;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.soundfriends.adapter.NotificationAdapter;
import com.example.soundfriends.fragments.Model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {
    private RecyclerView rcvNotifications;
    private NotificationAdapter adapter;
    private List<Notification> notificationList;
    private ImageView ivBack, ivMarkAllRead, ivDeleteAllNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        rcvNotifications = findViewById(R.id.rcvNotifications);
        ivBack = findViewById(R.id.ivBackNotifications);
        ivMarkAllRead = findViewById(R.id.ivMarkAllRead);
        ivDeleteAllNotifications = findViewById(R.id.ivDeleteAllNotifications);

        ivBack.setOnClickListener(v -> finish());
        
        ivMarkAllRead.setOnClickListener(v -> markAllAsRead());
        ivDeleteAllNotifications.setOnClickListener(v -> showDeleteAllDialog());

        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(this, notificationList);
        rcvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rcvNotifications.setAdapter(adapter);

        setupSwipeToDelete();
        loadNotifications();
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                deleteNotification(position);
            }
        };
        new ItemTouchHelper(touchHelperCallback).attachToRecyclerView(rcvNotifications);
    }

    private void deleteNotification(int position) {
        Notification notification = notificationList.get(position);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notifications")
                .child(currentUserId).child(notification.getId());
        
        ref.removeValue().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Đã xóa thông báo", Toast.LENGTH_SHORT).show();
        });
    }

    private void markAllAsRead() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId);
        
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    ds.getRef().child("isRead").setValue(true);
                }
                Toast.makeText(NotificationActivity.this, "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showDeleteAllDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa tất cả")
                .setMessage("Bạn có chắc chắn muốn xóa tất cả thông báo không?")
                .setPositiveButton("Xóa hết", (dialog, which) -> deleteAllNotifications())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteAllNotifications() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId);
        ref.removeValue().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Đã xóa toàn bộ thông báo", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadNotifications() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notifications").child(currentUserId);

        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Notification notification = ds.getValue(Notification.class);
                    if (notification != null) {
                        notification.setId(ds.getKey());
                        notificationList.add(notification);
                    }
                }
                Collections.sort(notificationList, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}