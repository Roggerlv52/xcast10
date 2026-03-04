package com.rogger.xcast10.gallery;

import android.net.Uri;

import com.rogger.xcast10.gallery.data.VideoItem;

public interface OnItemClickListener {
    void onVideoSelected(VideoItem items);
    //void onImgSelected(Uri img);
}
