package com.rogger.xcast10;

import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

import fi.iki.elonen.NanoHTTPD;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Servidor HTTP local baseado no NanoHTTPD.
 * Serve o ficheiro de vídeo do smartphone para que a Smart TV o possa descarregar e reproduzir via rede.
 * Suporta pedidos de "Range" para permitir avançar/retroceder o vídeo na TV.
 * Implementa WakeLock e WifiLock para evitar interrupções quando o ecrã se apaga.
 */
public class LocalHttpServer extends NanoHTTPD {

    private static final String TAG = "LocalHttpService";

    private Context context;
    private Uri videoUri;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public LocalHttpServer(Context context, int port) {
        super(port);
        this.context = context;
        initLocks();
    }

    private void initLocks() {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Xcast10:StreamingWakeLock");
        }

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Xcast10:StreamingWifiLock");
        }
    }

    @Override
    public void start() throws java.io.IOException {
        super.start();
        acquireLocks();
    }

    @Override
    public void stop() {
        super.stop();
        releaseLocks();
    }

    private void acquireLocks() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
            Log.d(TAG, "WakeLock adquirido");
        }
        if (wifiLock != null && !wifiLock.isHeld()) {
            wifiLock.acquire();
            Log.d(TAG, "WifiLock adquirido");
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock libertado");
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            Log.d(TAG, "WifiLock libertado");
        }
    }

    public void setVideoUri(Uri uri) {
        this.videoUri = uri;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {

            if (videoUri == null)
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No video");

            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(videoUri, "r");

            if (pfd == null)
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found");

            long fileSize = pfd.getStatSize();
            FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());

            String mime = getMimeType(videoUri.toString());

            Map<String, String> headers = session.getHeaders();
            String range = headers.get("range");

            long start = 0;
            long end = fileSize - 1;

            if (range != null && range.startsWith("bytes=")) {
                try {
                    range = range.substring("bytes=".length());
                    String[] parts = range.split("-");

                    start = Long.parseLong(parts[0]);

                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }

                    if (end >= fileSize)
                        end = fileSize - 1;

                    long contentLength = end - start + 1;

                    fis.skip(start);

                    Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, contentLength);

                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                    res.addHeader("Content-Length", String.valueOf(contentLength));
                    res.addHeader("Connection", "keep-alive");
                    res.addHeader("Cache-Control", "no-cache");

                    return res;

                } catch (Exception e) {
                    Log.e(TAG, "Erro Range parse", e);
                }
            }

            // resposta completa (sem range)
            Response res = newFixedLengthResponse(Response.Status.OK, mime, fis, fileSize);
            res.addHeader("Accept-Ranges", "bytes");
            res.addHeader("Content-Length", String.valueOf(fileSize));
            res.addHeader("Connection", "keep-alive");
            res.addHeader("Cache-Control", "no-cache");

            return res;

        } catch (Exception e) {
            if (e instanceof java.net.SocketException) {
                e.getMessage();
            }
            Log.e(TAG, "Erro ao servir vídeo", e);
        }

        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server error");
    }

    private String getMimeType(String url) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        if (ext != null) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
            if (mime != null)
                return mime;
        }
        return "video/mp4"; // fallback seguro
    }
}
