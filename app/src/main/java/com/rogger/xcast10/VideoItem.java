package com.rogger.xcast10;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * Representa um vídeo local com título, miniatura, URI e duração.
 */
public class VideoItem {
    private final String title;
    private final Uri uri;
    private final Bitmap thumbnail;
    private final String duration;
    private final long durationMs;

    public VideoItem(String title, Uri uri, Bitmap thumbnail, String duration, long durationMs) {
        this.title = title;
        this.uri = uri;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.durationMs = durationMs;
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

    public String getDuration() {
        return duration;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
