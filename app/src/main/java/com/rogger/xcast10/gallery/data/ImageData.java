package com.rogger.xcast10.gallery.data;

import android.net.Uri;

public class ImageData {
    public String name;
    public Uri uri;
    public long size;

    public ImageData(String name, Uri uri, long size) {
        this.name = name;
        this.uri = uri;
        this.size = size;

    }
}
