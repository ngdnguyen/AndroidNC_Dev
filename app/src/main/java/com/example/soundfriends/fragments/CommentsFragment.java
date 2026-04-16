package com.example.soundfriends.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.soundfriends.R;
import com.example.soundfriends.adapter.CommentAdapter;
import com.example.soundfriends.fragments.Model.Comment;
import com.example.soundfriends.fragments.Model.User;
import com.example.soundfriends.utils.WrapContentLinearLayoutManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class CommentsFragment extends Fragment {
    ImageView currentAvatarComment;
    TextInputLayout commentInputLayout;
    TextInputEditText edtComment;
    ImageButton btnSubmitComment;
    RecyclerView rcvComment;
    CommentAdapter commentAdapter;
    FirebaseAuth auth;
    DatabaseReference commentReferences;
    List<Comment> comments = new ArrayList<>();
    String currentSongId;
    String replyingToCommentId = null;

    public CommentsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comments, container, false);

        if (getArguments() != null) {
            currentSongId = getArguments().getString("key_song_id");
        }

        rcvComment = view.findViewById(R.id.rcvComments);
        currentAvatarComment = view.findViewById(R.id.currentAvatarComment);
        commentInputLayout = view.findViewById(R.id.edtComment);
        edtComment = view.findViewById(R.id.edtCommentBody);
        btnSubmitComment = view.findViewById(R.id.submitCommentButton);

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            loadCurrentUserDisplay(user);
        } else {
            commentInputLayout.setHint("Đăng nhập để bình luận");
            edtComment.setEnabled(false);
            btnSubmitComment.setEnabled(false);
        }

        rcvComment.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        commentReferences = FirebaseDatabase.getInstance().getReference("comments");

        commentAdapter = new CommentAdapter(getContext(), comments);
        rcvComment.setAdapter(commentAdapter);

        getComments();

        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(replyCommentBtnClickReceiver, new IntentFilter(CommentAdapter.ACTION_REPLY_BUTTON_CLICK));

        btnSubmitComment.setOnClickListener(v -> postComment());

        return view;
    }

    private void loadCurrentUserDisplay(FirebaseUser user) {
        FirebaseDatabase.getInstance().getReference("users").child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        User userModel = snapshot.getValue(User.class);
                        String displayName = (userModel != null) ? userModel.getName() : user.getEmail();
                        commentInputLayout.setHint("Bình luận với tư cách " + displayName);
                        if (userModel != null && userModel.getAvatar() != null && !userModel.getAvatar().isEmpty()) {
                            Glide.with(CommentsFragment.this).load(userModel.getAvatar()).into(currentAvatarComment);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void postComment() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        String commentBody = edtComment.getText().toString().trim();
        if (commentBody.isEmpty()) return;

        FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        String name = (user != null) ? user.getName() : currentUser.getEmail();
                        String avatar = (user != null) ? user.getAvatar() : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
                        
                        String commentId = commentReferences.push().getKey();
                        String timestamp = Calendar.getInstance().getTime().toString();
                        
                        Comment comment = new Comment(commentId, commentBody, currentUser.getUid(), 0, 
                                timestamp, currentSongId, avatar, name, replyingToCommentId);
                        
                        if (commentId != null) {
                            commentReferences.child(commentId).setValue(comment).addOnSuccessListener(aVoid -> {
                                edtComment.setText("");
                                replyingToCommentId = null;
                                commentInputLayout.setHint("Bình luận với tư cách " + name);
                            });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void getComments() {
        if (currentSongId == null) return;
        Query query = commentReferences.orderByChild("songId").equalTo(currentSongId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> allComments = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Comment c = ds.getValue(Comment.class);
                    if (c != null) allComments.add(c);
                }
                organizeComments(allComments);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void organizeComments(List<Comment> allComments) {
        List<Comment> organized = new ArrayList<>();
        List<Comment> rootComments = new ArrayList<>();
        List<Comment> replies = new ArrayList<>();

        for (Comment c : allComments) {
            if (c.getParentId() == null) rootComments.add(c);
            else replies.add(c);
        }

        Collections.reverse(rootComments); 

        for (Comment root : rootComments) {
            organized.add(root);
            addReplies(root.getCommentId(), replies, organized);
        }

        comments.clear();
        comments.addAll(organized);
        commentAdapter.notifyDataSetChanged();
    }

    private void addReplies(String parentId, List<Comment> allReplies, List<Comment> resultList) {
        for (Comment reply : allReplies) {
            if (parentId.equals(reply.getParentId())) {
                resultList.add(reply);
                addReplies(reply.getCommentId(), allReplies, resultList);
            }
        }
    }

    private BroadcastReceiver replyCommentBtnClickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String userName = intent.getStringExtra("username");
            replyingToCommentId = intent.getStringExtra("commentId");
            
            edtComment.setText("@" + userName + " ");
            edtComment.setSelection(edtComment.getText().length());
            edtComment.requestFocus();
            commentInputLayout.setHint("Đang trả lời " + userName);
        }
    };
}
