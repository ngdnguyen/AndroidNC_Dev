package com.example.soundfriends.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.soundfriends.R;
import com.example.soundfriends.fragments.Model.Comment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.example.soundfriends.fragments.Model.User;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {
    private List<Comment> comments;
    private Context context;
    public static final String ACTION_REPLY_BUTTON_CLICK = "action_reply_button_click";

    public CommentAdapter(Context context, List<Comment> comments) {
        this.context = context;
        this.comments = comments;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        if (comment == null) return;

        // Handle indentation for replies
        if (comment.getParentId() != null) {
            holder.itemView.setPadding(80, 0, 0, 0); // Indent replies
            holder.itemView.setBackgroundColor(Color.parseColor("#F9F9F9"));
        } else {
            holder.itemView.setPadding(0, 0, 0, 0);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.tvAccount.setText(comment.getUsername());
        holder.tvBody.setText(comment.getBody());
        
        // Format time
        try {
            String commentTime = comment.getTimestamp();
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
            LocalDateTime dateTime = LocalDateTime.parse(commentTime, inputFormatter);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
            holder.tvTime.setText(dateTime.format(outputFormatter));
        } catch (Exception e) {
            holder.tvTime.setText(comment.getTimestamp());
        }

        Glide.with(context).load(comment.getAvatarUrl()).placeholder(R.drawable.empty_avatar).into(holder.avatarComment);
        holder.tvTextLike.setText(String.valueOf(comment.getLikeCount()));

        // Like logic
        checkIfLiked(comment.getCommentId(), holder.btnLikeComment);

        holder.btnLikeComment.setOnClickListener(v -> handleLike(comment));

        holder.btnReplyComment.setOnClickListener(v -> {
            Intent replyIntent = new Intent(ACTION_REPLY_BUTTON_CLICK);
            replyIntent.putExtra("username", comment.getUsername());
            replyIntent.putExtra("commentId", comment.getCommentId());
            LocalBroadcastManager.getInstance(context).sendBroadcast(replyIntent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getUid().equals(comment.getUserId())) {
                showEditDeleteDialog(comment);
            }
            return true;
        });
    }

    private void showEditDeleteDialog(Comment comment) {
        String[] options = {"Chỉnh sửa", "Xóa"};
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Tùy chọn bình luận");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showEditDialog(comment);
            } else {
                showDeleteConfirmDialog(comment);
            }
        });
        builder.show();
    }

    private void showEditDialog(Comment comment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Chỉnh sửa bình luận");

        final EditText input = new EditText(context);
        input.setText(comment.getBody());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        builder.setView(input);

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String newBody = input.getText().toString().trim();
            if (!newBody.isEmpty()) {
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("comments").child(comment.getCommentId());
                ref.child("body").setValue(newBody).addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Đã cập nhật bình luận", Toast.LENGTH_SHORT).show();
                });
            }
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteConfirmDialog(Comment comment) {
        new AlertDialog.Builder(context)
                .setTitle("Xóa bình luận")
                .setMessage("Bạn có chắc chắn muốn xóa bình luận này?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("comments").child(comment.getCommentId());
                    ref.removeValue().addOnSuccessListener(aVoid -> {
                        Toast.makeText(context, "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void checkIfLiked(String commentId, ImageView btnLike) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || commentId == null) return;

        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("comment_likes")
                .child(commentId).child(user.getUid());
        
        likeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    btnLike.setImageResource(R.drawable.ic_like_selected);
                } else {
                    btnLike.setImageResource(R.drawable.ic_like_unselected);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void handleLike(Comment comment) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || comment.getCommentId() == null) return;

        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("comment_likes")
                .child(comment.getCommentId()).child(user.getUid());

        likeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    likeRef.removeValue().addOnSuccessListener(aVoid -> updateLikeCount(comment.getCommentId(), -1));
                } else {
                    likeRef.setValue(true).addOnSuccessListener(aVoid -> {
                        updateLikeCount(comment.getCommentId(), 1);
                        sendCommentLikeNotification(comment);
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendCommentLikeNotification(Comment comment) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || comment.getUserId() == null || comment.getUserId().equals(currentUser.getUid())) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                String name = (user != null) ? user.getName() : "Ai đó";
                String avatar = (user != null) ? user.getAvatar() : "";

                DatabaseReference notificationRef = FirebaseDatabase.getInstance().getReference("notifications").child(comment.getUserId());
                String notificationId = notificationRef.push().getKey();

                Map<String, Object> notificationData = new HashMap<>();
                notificationData.put("id", notificationId);
                notificationData.put("fromUserId", currentUser.getUid());
                notificationData.put("fromUserName", name);
                notificationData.put("fromUserAvatar", avatar);
                notificationData.put("type", "like");
                notificationData.put("message", "đã thích bình luận của bạn: " + comment.getBody());
                notificationData.put("songId", comment.getSongId());
                notificationData.put("timestamp", System.currentTimeMillis());
                notificationData.put("isRead", false);

                if (notificationId != null) {
                    notificationRef.child(notificationId).setValue(notificationData);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateLikeCount(String commentId, int delta) {
        DatabaseReference commentRef = FirebaseDatabase.getInstance().getReference("comments").child(commentId);
        commentRef.child("likeCount").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer value = currentData.getValue(Integer.class);
                if (value == null) value = 0;
                currentData.setValue(Math.max(0, value + delta));
                return Transaction.success(currentData);
            }
            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    @Override
    public int getItemCount() {
        return comments != null ? comments.size() : 0;
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        public ImageView avatarComment, btnLikeComment;
        public TextView tvAccount, tvTime, tvBody, tvTextLike;
        public Button btnReplyComment;

        public CommentViewHolder(View itemView) {
            super(itemView);
            avatarComment = itemView.findViewById(R.id.avatarComment);
            tvAccount = itemView.findViewById(R.id.accountComment);
            tvTime = itemView.findViewById(R.id.timeComment);
            tvBody = itemView.findViewById(R.id.bodyComment);
            tvTextLike = itemView.findViewById(R.id.textLikeComment);
            btnLikeComment = itemView.findViewById(R.id.likeComment);
            btnReplyComment = itemView.findViewById(R.id.replyComment);
        }
    }
}
