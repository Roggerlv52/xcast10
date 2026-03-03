package com.rogger.xcast10;

import android.graphics.Bitmap;
import android.net.Uri;

import java.util.Locale;

/**
 * Representa um vídeo local com título, miniatura e URI.
 */
public class VideoItem {

    private final String title;
    private final Uri uri;
    private final Bitmap thumbnail;
    private final long duration;
    private final String path; // ← novo campo

    public VideoItem(String title, Uri uri, Bitmap thumbnail, long duration, String path) {
        this.title = title;
        this.uri = uri;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.path = path;
    }

    public String getTitle() {
        return title;
    }

    public Uri getUri() {
        return uri;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public long getDuration() {
        return duration;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return title;
    }
    public String formated(){
        return formatDuration(duration);
    }
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", m, s);
        }
    }

}
