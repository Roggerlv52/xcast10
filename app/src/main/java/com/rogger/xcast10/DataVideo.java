package com.rogger.xcast10;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.List;
/**
 * Obtém a lista de vídeos do MediaStore com metadados (título, duração, miniatura).
 */

public class DataVideo {
    public static List<VideoItem> getVideoList(Context context) {

        List<VideoItem> list = new ArrayList<>();

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,        // pode vir null Android 10+
                MediaStore.Video.Media.DURATION
        };

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
        )) {

            if (cursor != null && cursor.moveToFirst()) {

                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int pathCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA);

                do {

                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long duration = cursor.getLong(durationCol);

                    String path = null;
                    if (pathCol != -1)
                        path = cursor.getString(pathCol);

                    Uri contentUri = ContentUris.withAppendedId(collection, id);
                    // -------------------------
                    // CORRIGIR DURAÇÃO ZERO
                    // -------------------------
                    if (duration <= 0) {
                        try {
                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

                            if (path != null)
                                retriever.setDataSource(path);
                            else
                                retriever.setDataSource(context, contentUri);

                            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if (time != null)
                                duration = Long.parseLong(time);

                            retriever.release();

                        } catch (Exception e) {
                            Log.e("VideoList", "Erro ao obter duração: " + name);
                        }
                    }
                    // -------------------------
                    // THUMBNAIL
                    // -------------------------
                    Bitmap thumb = null;
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            thumb = resolver.loadThumbnail(contentUri, new Size(320, 180), null);
                        } else {
                            thumb = MediaStore.Video.Thumbnails.getThumbnail(
                                    resolver,
                                    id,
                                    MediaStore.Video.Thumbnails.MINI_KIND,
                                    null
                            );
                        }
                    } catch (Exception e) {
                        Log.e("VideoList", "Erro thumb: " + name);
                    }
                    // -------------------------
                    // ADD NA LISTA
                    // -------------------------
                    list.add(new VideoItem(
                            name,
                            contentUri,
                            thumb,
                            duration,
                            path
                    ));

                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e("VideoList", "Erro ao listar vídeos", e);
        }

        Log.d("VIDEOS", "Total encontrados: " + list.size());
        return list;
    }
}
