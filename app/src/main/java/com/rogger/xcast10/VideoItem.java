package com.rogger.xcast10;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * Representa um vídeo local com título, miniatura e URI.
 */
public class VideoItem {
    private final String title;
    private final Uri uri;
    private final Bitmap thumbnail;
    private final String duration;

    public VideoItem(String title, Uri uri, Bitmap thumbnail, String duration) {
        this.title = title;
        this.uri = uri;
        this.thumbnail = thumbnail;
        this.duration = duration;
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
}
