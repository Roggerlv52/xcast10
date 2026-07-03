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
import java.io.FileInputStream

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

    private var videoUri: Uri? = null
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

    fun setVideoUri(uri: Uri) {
        this.videoUri = uri
    }

    override fun serve(session: IHTTPSession): Response {
        try {
            val uri = videoUri
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No video")

            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")

            val fileSize = pfd.statSize
            val fis = FileInputStream(pfd.fileDescriptor)
            val mime = getMimeType(uri.toString())

            val range = session.headers["range"]

            var start = 0L
            var end = fileSize - 1

            if (range != null && range.startsWith("bytes=")) {
                try {
                    val parts = range.substring("bytes=".length).split("-")
                    start = parts[0].toLong()
                    if (parts.size > 1 && parts[1].isNotEmpty()) end = parts[1].toLong()
                    if (end >= fileSize) end = fileSize - 1

                    val contentLength = end - start + 1
                    fis.skip(start)

                    val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, contentLength)
                    res.addHeader("Accept-Ranges", "bytes")
                    res.addHeader("Content-Range", "bytes $start-$end/$fileSize")
                    res.addHeader("Content-Length", contentLength.toString())
                    res.addHeader("Connection", "keep-alive")
                    res.addHeader("Cache-Control", "no-cache")
                    return res
                } catch (e: Exception) {
                    Log.e(TAG, "Erro Range parse", e)
                }
            }

            val res = newFixedLengthResponse(Response.Status.OK, mime, fis, fileSize)
            res.addHeader("Accept-Ranges", "bytes")
            res.addHeader("Content-Length", fileSize.toString())
            res.addHeader("Connection", "keep-alive")
            res.addHeader("Cache-Control", "no-cache")
            return res

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao servir vídeo", e)
        }

        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server error")
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
