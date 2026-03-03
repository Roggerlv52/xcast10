package com.rogger.xcast10;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Adapter personalizado para exibir a lista de vídeos com miniaturas e títulos.
 */
public class VideoAdapter extends ArrayAdapter<VideoItem> {

    public VideoAdapter(@NonNull Context context, @NonNull List<VideoItem> objects) {
        super(context, 0, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.video_item, parent, false);
        }

        VideoItem video = getItem(position);
        if (video != null) {
            ImageView ivThumbnail = convertView.findViewById(R.id.ivThumbnail);
            TextView tvTitle = convertView.findViewById(R.id.tvTitle);
            TextView tvDuration = convertView.findViewById(R.id.tvDuration);

            if (video.getThumbnail() != null) {
                ivThumbnail.setImageBitmap(video.getThumbnail());
            } else {
                ivThumbnail.setImageResource(android.R.drawable.ic_menu_slideshow);
            }
            tvTitle.setText(video.getTitle());
            tvDuration.setText(video.formated());
        }

        return convertView;
    }
}
