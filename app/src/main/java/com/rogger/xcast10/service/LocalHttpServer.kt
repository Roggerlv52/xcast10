package com.rogger.xcast10.service

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:17
 */

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Servidor HTTP local baseado no NanoHTTPD.
 * Serve o ficheiro de vídeo do telemóvel para que a Smart TV o possa descarregar e reproduzir via rede.
 * Suporta pedidos "Range" para permitir avançar/retroceder o vídeo na TV.
 * Mantém WakeLock e WifiLock para evitar interrupções quando o ecrã se apaga.
 */
class LocalHttpServer(
    private val context: Context,
    port: Int
) : NanoHTTPD(port) {

    // ALTERADO: era Uri + startPositionMs + totalDurationMs. Agora o servidor serve OU o Uri
    // original (setVideoUri) OU um File já pronto (setVideoFile) — nunca os dois ao mesmo tempo.
    private var videoUri: Uri? = null
    private var videoFile: File? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Xcast10:StreamingWakeLock")

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Xcast10:StreamingWifiLock")
    }

    override fun start() {
        super.start()
        acquireLocks()
    }

    override fun stop() {
        super.stop()
        releaseLocks()
    }

    private fun acquireLocks() {
        wakeLock?.let { if (!it.isHeld) it.acquire() }
        wifiLock?.let { if (!it.isHeld) it.acquire() }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    /** Serve o vídeo original (MediaStore/SAF). Usado no play inicial (startStreaming). */
    fun setVideoUri(uri: Uri) {
        this.videoUri = uri
        this.videoFile = null
    }

    /**
     * Serve um arquivo já pronto no disco — usado para o fallback de seek, com o arquivo
     * gerado pelo [SeekMp4Generator] (já recortado corretamente, começando num keyframe).
     */
    fun setVideoFile(file: File) {
        this.videoFile = file
        this.videoUri = null
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val (stream, fileSize, mimeSource) = openSource()
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No video")

            val mime = getMimeType(mimeSource)
            val range = session.headers["range"]

            if (range != null && range.startsWith("bytes=")) {
                try {
                    val parts = range.substring("bytes=".length).split("-")
                    val start = parts[0].toLongOrNull() ?: 0L
                    var end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toLong() else fileSize - 1
                    if (end >= fileSize) end = fileSize - 1
                    val contentLength = (end - start + 1).coerceAtLeast(0)

                    if (start > 0) stream.skip(start)

                    val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, contentLength)
                    res.addHeader("Content-Type", mime)
                    res.addHeader("Accept-Ranges", "bytes")
                    res.addHeader("Content-Range", "bytes $start-$end/$fileSize")
                    res.addHeader("Connection", "keep-alive")
                    res.addHeader("Content-Length", contentLength.toString())
                    return res
                } catch (e: Exception) {
                    Log.e(TAG, "Erro Range parse", e)
                }
            }

            val res = newFixedLengthResponse(Response.Status.OK, mime, stream, fileSize)
            res.addHeader("Content-Type", mime)
            res.addHeader("Accept-Ranges", "bytes")
            res.addHeader("Connection", "keep-alive")
            res.addHeader("Content-Length", fileSize.toString())
            res

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao servir vídeo", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server error")
        }
    }

    /**
     * Abre o stream de leitura correto (arquivo recortado tem prioridade sobre o Uri original,
     * já que só um dos dois é definido de cada vez por setVideoUri/setVideoFile) e retorna
     * junto o tamanho total e a "fonte" usada só para deduzir o MIME type.
     */
    private fun openSource(): Triple<InputStream, Long, String>? {
        videoFile?.let { file ->
            if (!file.exists()) {
                Log.e(TAG, "Arquivo de vídeo recortado não existe mais: ${file.absolutePath}")
                return null
            }
            return Triple(FileInputStream(file), file.length(), file.name)
        }

        videoUri?.let { uri ->
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return null
            return Triple(FileInputStream(pfd.fileDescriptor), pfd.statSize, uri.toString())
        }

        return null
    }

    private fun getMimeType(url: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(url)
        if (ext != null) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            if (mime != null) return mime
        }
        return "video/mp4"
    }

    companion object {
        private const val TAG = "LocalHttpServer"
    }
}
