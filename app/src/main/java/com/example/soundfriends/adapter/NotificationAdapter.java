package com.example.soundfriends.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.soundfriends.ProfileActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.fragments.Model.Notification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private Context context;
    private List<Notification> notificationList;

    public NotificationAdapter(Context context, List<Notification> notificationList) {
        this.context = context;
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = notificationList.get(position);
        
        String messagePrefix = "";
        int iconRes = 0;
        
        if (notification.getType() != null) {
            switch (notification.getType()) {
                case "like":
                    messagePrefix = (notification.getMessage() != null && !notification.getMessage().isEmpty()) 
                            ? notification.getMessage() 
                            : "đã thích bài hát của bạn";
                    iconRes = R.drawable.ic_notif_like;
                    break;
                case "comment":
                    messagePrefix = (notification.getMessage() != null && !notification.getMessage().isEmpty()) 
                            ? notification.getMessage() 
                            : "đã bình luận về bài hát của bạn";
                    iconRes = R.drawable.ic_notif_comment;
                    break;
                case "follow":
                    messagePrefix = "đã bắt đầu theo dõi bạn";
                    iconRes = R.drawable.ic_notif_follow;
                    break;
                case "new_post":
                    messagePrefix = notification.getMessage();
                    iconRes = R.drawable.ic_notif_new_post;
                    break;
                case "delete_post":
                    messagePrefix = notification.getMessage();
                    iconRes = R.drawable.ic_notif_report;
                    break;
                case "report":
                    messagePrefix = notification.getMessage();
                    iconRes = R.drawable.ic_notif_report;
                    break;
                case "share":
                    messagePrefix = "đã chia sẻ bài hát của bạn";
                    iconRes = R.drawable.ic_share_feed;
                    break;
                default:
                    messagePrefix = notification.getMessage();
                    break;
            }
        }
        
        String senderName = (notification.getFromUserName() != null && !notification.getFromUserName().isEmpty()) 
                ? notification.getFromUserName() 
                : "Người dùng";
        holder.tvMessage.setText(senderName + " " + messagePrefix);
        
        if (iconRes != 0) {
            holder.ivType.setImageResource(iconRes);
            holder.ivType.setVisibility(View.VISIBLE);
        } else {
            holder.ivType.setVisibility(View.GONE);
        }
        
        long now = System.currentTimeMillis();
        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(notification.getTimestamp(), now, DateUtils.MINUTE_IN_MILLIS);
        holder.tvTime.setText(relativeTime);

        Glide.with(context).load(notification.getFromUserAvatar())
                .placeholder(R.drawable.empty_avatar)
                .into(holder.ivSender);

        holder.itemView.setOnClickListener(v -> {
            if (!notification.isRead()) {
                String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notifications")
                        .child(currentUserId).child(notification.getId());
                ref.child("isRead").setValue(true);
                notification.setRead(true);
                notifyItemChanged(position);
            }
            
            // Navigate based on type
            if ("follow".equals(notification.getType())) {
                Intent intent = new Intent(context, ProfileActivity.class);
                intent.putExtra("userID", notification.getFromUserId());
                context.startActivity(intent);
            } else if (notification.getSongId() != null && !notification.getSongId().isEmpty()) {
                Intent intent = new Intent(context, com.example.soundfriends.Song.class);
                intent.putExtra("SONG_ID", notification.getSongId());
                context.startActivity(intent);
            }
        });

        if (notification.isRead()) {
            holder.viewUnread.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.white));
        } else {
            holder.viewUnread.setVisibility(View.VISIBLE);
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.primary_pink_light));
        }
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivSender;
        ImageView ivType;
        TextView tvMessage, tvTime;
        View viewUnread;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSender = itemView.findViewById(R.id.ivNotificationSender);
            ivType = itemView.findViewById(R.id.ivNotificationType);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            viewUnread = itemView.findViewById(R.id.viewUnreadStatus);
        }
    }
}