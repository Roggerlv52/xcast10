package com.rogger.xcast10.data

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:08
 */
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.rogger.xcast10.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositório responsável por obter a lista de vídeos do dispositivo através do MediaStore,
 * incluindo título, duração e miniatura (thumbnail).
 */
object VideoRepository {

    private const val TAG = "VideoRepository"

    suspend fun getVideoList(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION
        )

        try {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val pathCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    var duration = cursor.getLong(durationCol)
                    val path = if (pathCol != -1) cursor.getString(pathCol) else null
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    if (duration <= 0) {
                        duration = extractDuration(context, path, contentUri, name)
                    }

                    val thumb = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            resolver.loadThumbnail(contentUri, Size(320, 180), null)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Video.Thumbnails.getThumbnail(
                                resolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro thumb: $name")
                        null
                    }

                    list.add(VideoItem(name ?: "Vídeo", contentUri, thumb, duration, path))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao listar vídeos", e)
        }

        Log.d(TAG, "Total encontrados: ${list.size}")
        list
    }

    private fun extractDuration(
        context: Context,
        path: String?,
        contentUri: android.net.Uri,
        name: String?
    ): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            if (path != null) retriever.setDataSource(path)
            else retriever.setDataSource(context, contentUri)

            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter duração: $name")
            0L
        }
    }
}
