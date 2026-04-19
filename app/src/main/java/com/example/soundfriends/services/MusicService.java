package com.example.soundfriends.services;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.example.soundfriends.MusicWidget;
import com.example.soundfriends.Song;

public class MusicService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        
        // Khi nhấn vào thông báo, quay lại Activity đang phát nhạc
        Intent intent = new Intent(this, Song.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                updateWidget();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateWidget();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateWidget();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case "ACTION_PLAY_PAUSE":
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    break;
                case "ACTION_PREV":
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem();
                    } else {
                        // Nếu không có bài trước đó trong playlist của player, thử lùi về 0
                        player.seekTo(0);
                    }
                    break;
                case "ACTION_NEXT":
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem();
                    } else {
                        // Nếu hết danh sách, có thể phát lại bài hiện tại hoặc dừng
                        player.seekTo(0);
                        player.pause();
                    }
                    break;
                case "ACTION_UPDATE_WIDGET":
                    break;
            }
            updateWidget();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void updateWidget() {
        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem != null) {
            String title = currentItem.mediaMetadata.title != null ? currentItem.mediaMetadata.title.toString() : "Unknown";
            String artist = currentItem.mediaMetadata.artist != null ? currentItem.mediaMetadata.artist.toString() : "Unknown";
            boolean isPlaying = player.isPlaying();
            byte[] artworkData = currentItem.mediaMetadata.artworkData;
            
            MusicWidget.updateAllWidgets(this, title, artist, isPlaying, artworkData);
        } else {
            MusicWidget.updateAllWidgets(this, null, null, false, null);
        }
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            mediaSession.release();
            player = null;
            mediaSession = null;
        }
        super.onDestroy();
    }
}
