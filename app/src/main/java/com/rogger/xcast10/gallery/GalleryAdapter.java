package com.rogger.xcast10.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.LruCache;
import androidx.recyclerview.widget.RecyclerView;

import com.rogger.xcast10.R;
import com.rogger.xcast10.gallery.data.VideoItem;

import java.util.List;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {
    private Context context;
    private List<VideoItem> items;
    private OnItemClickListener listener;
    private LruCache<String, Bitmap> cache;

    public GalleryAdapter(Context context, List<VideoItem> images, OnItemClickListener listener,
                          LruCache<String, Bitmap> cache) {
        this.context = context;
        this.items = images;
        this.listener = listener;
        this.cache = cache;
    }

    @NonNull
    @Override
    public GalleryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.video_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryAdapter.ViewHolder holder, int position) {
        VideoItem videoItem = items.get(position);
        if (videoItem != null) {
            if (videoItem.getThumbnail() != null) {
                holder.imgThumb.setImageBitmap(videoItem.getThumbnail());
            } else {
                holder.imgThumb.setImageResource(android.R.drawable.ic_menu_slideshow);
            }
            holder.title.setText(videoItem.getTitle());
            holder.duration.setText(videoItem.formated());
        }
        holder.itemView.setOnClickListener(view->{
            listener.onVideoSelected(videoItem);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        TextView title;
        TextView duration;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView.findViewById(R.id.ivThumbnail);
            title = itemView.findViewById(R.id.tvTitle);
            duration = itemView.findViewById(R.id.tvDuration);
        }
    }
}

