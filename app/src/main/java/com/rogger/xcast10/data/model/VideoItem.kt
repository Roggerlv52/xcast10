package com.rogger.xcast10.data.model

import android.graphics.Bitmap
import android.net.Uri
import java.util.Locale

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:09
 */
data class VideoItem(
    val title: String,
    val uri: Uri,
    val thumbnail: Bitmap?,
    val duration: Long, // em milissegundos
    val path: String?

){
    /** Duração formatada, ex: "12:03" ou "01:12:03". */
    fun formattedDuration(): String {
        val seconds = duration / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

}
