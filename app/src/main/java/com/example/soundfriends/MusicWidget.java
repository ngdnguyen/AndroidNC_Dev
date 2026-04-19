package com.example.soundfriends;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.RemoteViews;

import com.example.soundfriends.services.MusicService;

public class MusicWidget extends AppWidgetProvider {

    public static void updateAllWidgets(Context context, String title, String artist, boolean isPlaying, byte[] artworkData) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, MusicWidget.class));
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, title, artist, isPlaying, artworkData);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Request update from service
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction("ACTION_UPDATE_WIDGET");
        try {
            context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, null, null, false, null);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, 
                                String title, String artist, boolean isPlaying, byte[] artworkData) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.music_widget);

        if (title != null) {
            views.setTextViewText(R.id.widget_title, title);
            views.setTextViewText(R.id.widget_artist, artist);

            if (artworkData != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.length);
                // Nén ảnh để tránh lỗi TransactionTooLargeException
                if (bitmap != null) {
                    float aspectRatio = (float) bitmap.getHeight() / (float) bitmap.getWidth();
                    int width = 150;
                    int height = Math.round(width * aspectRatio);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                    views.setImageViewBitmap(R.id.widget_album_art, scaledBitmap);
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.logo);
                }
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.logo);
            }

            views.setImageViewResource(R.id.widget_play_pause, isPlaying ? R.drawable.pause : R.drawable.play);
        } else {
            views.setTextViewText(R.id.widget_title, "SoundFriends");
            views.setTextViewText(R.id.widget_artist, "No song playing");
            views.setImageViewResource(R.id.widget_album_art, R.drawable.logo);
            views.setImageViewResource(R.id.widget_play_pause, R.drawable.play);
        }

        // PendingIntents for controls - now targeting the Service directly
        views.setOnClickPendingIntent(R.id.widget_play_pause, getPendingServiceIntent(context, "ACTION_PLAY_PAUSE"));
        views.setOnClickPendingIntent(R.id.widget_prev, getPendingServiceIntent(context, "ACTION_PREV"));
        views.setOnClickPendingIntent(R.id.widget_next, getPendingServiceIntent(context, "ACTION_NEXT"));

        // Open App when clicking widget
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_album_art, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent getPendingServiceIntent(Context context, String action) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }
}
