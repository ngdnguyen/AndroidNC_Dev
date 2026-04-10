package com.example.soundfriends.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.soundfriends.R;
import com.example.soundfriends.Song;
import com.example.soundfriends.adapter.CommentAdapter;
import com.example.soundfriends.fragments.Model.Comment;
import com.example.soundfriends.utils.WrapContentLinearLayoutManager;
import com.example.soundfriends.utils.uuid;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseUserMetadata;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CommentsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
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
    Song songActivity;
    Callable<Void> onLikeButtonClicked = new Callable<Void>() {
        @Override
        public Void call() throws Exception {
            // Your logic for like button click
            return null;
        }
    };


    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public CommentsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment CommentsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static CommentsFragment newInstance(String param1, String param2) {
        CommentsFragment fragment = new CommentsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        //Initial Firebase Auth
        auth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_comments, container, false);
        songActivity = (Song) getActivity();
        currentSongId = getArguments().getString("key_song_id");

        rcvComment = view.findViewById(R.id.rcvComments);
        currentAvatarComment = view.findViewById(R.id.currentAvatarComment);
        commentInputLayout = (TextInputLayout) view.findViewById(R.id.edtComment);
        edtComment = (TextInputEditText) view.findViewById(R.id.edtCommentBody);
        btnSubmitComment = view.findViewById(R.id.submitCommentButton);

        //load current user information to comment input set
        FirebaseUser user = auth.getCurrentUser();
        Glide.with(this).load(user.getPhotoUrl()).into(currentAvatarComment);
        String username = user.getEmail() != null ? user.getEmail() : user.getDisplayName();
        commentInputLayout.setHint("Bình luận với tư cách "+ username);

        //initial recycler view
        rcvComment.setLayoutManager(new WrapContentLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
//        rcvComment.setLayoutManager(new LinearLayoutManager(getContext()));
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
        rcvComment.addItemDecoration(dividerItemDecoration);

        //initial realtime DB
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        commentReferences = database.getReference("comments");

        commentAdapter = new CommentAdapter(getContext(), comments, commentReferences);
        rcvComment.setAdapter(commentAdapter);

        //LOAD COMMENT DATA
        getComments();

        //Register BroadcastReceiver to listen Reply comment event
        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(replyCommentBtnClickReceiver, new IntentFilter(CommentAdapter.ACTION_REPLY_BUTTON_CLICK));

        btnSubmitComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String commentBody = edtComment.getText().toString().trim();
                String commentId = uuid.createTransactionID();
                String userId = auth.getCurrentUser().getUid();
                String timestamp = Calendar.getInstance().getTime().toString();
                int likeCount = 0;
                String avatarUrl = auth.getCurrentUser().getPhotoUrl() != null ? String.valueOf(auth.getCurrentUser().getPhotoUrl()): "";
                String username = auth.getCurrentUser().getEmail() != null ? auth.getCurrentUser().getEmail() : auth.getCurrentUser().getDisplayName();
                boolean isLiked = false;  // Set the initial value for isLiked
                Comment comment = new Comment(commentId,commentBody,userId,likeCount,timestamp, currentSongId, avatarUrl, username, isLiked);
                String uploadId = commentReferences.push().getKey();
                comment.setCommentId(uploadId); // Gán key vừa được tạo vào Comment object
                commentReferences.child(uploadId).setValue(comment);


                edtComment.setText("");
            }
        });

        return view;
    }

    private void getComments(){
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        commentReferences = database.getReference("comments");
        Query query = commentReferences.orderByChild("songId").equalTo(currentSongId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                comments.clear();
                for (DataSnapshot dataSnapshotItem: snapshot.getChildren()){
                    Comment comment = dataSnapshotItem.getValue(Comment.class);
                    comments.add(0, comment);
                }
                commentAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
    private BroadcastReceiver replyCommentBtnClickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String userBeReplied = intent.getStringExtra("data");
            edtComment.setText("@"+ userBeReplied + " ");
            edtComment.setSelection(edtComment.getText().length());
            edtComment.requestFocus();
        }
    };
}