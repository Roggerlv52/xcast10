package com.rogger.xcast10.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.rogger.xcast10.DLNADevice;
import com.rogger.xcast10.DLNAManager;
import com.rogger.xcast10.gallery.data.VideoItem;

public class Streaming {
    public interface StreamingCallback {

        void onLoading(boolean loading);
        void onSuccess(VideoItem item, String deviceUrl, String renderingControlUrl);
        void onError(String message);
    }


    public static void startStreaming(
            Context context,
            VideoItem item,
            StreamingCallback callback
    ) {

        DLNADevice selectedDevice = DLNAManager.getSelectedDevice();

        if (selectedDevice == null) {
            callback.onError("Por favor, selecione um dispositivo primeiro");
            return;
        }


        callback.onLoading(true);

        try {

            LocalHttpServer server = new LocalHttpServer(context, 8080);
            server.setVideoUri(item.getUri());
            server.start();

            String ip = DLNAManager.getLocalIpAddress();
            String videoUrl = "http://" + ip + ":8080/video.mp4";

            Log.d("Streaming", "URL: " + videoUrl);

            DLNAManager.setAVTransportURI(
                    selectedDevice.getServiceUrl(),
                    videoUrl
            );

            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                DLNAManager.play(selectedDevice.getServiceUrl());

                callback.onLoading(false);

                callback.onSuccess(
                        item,
                        selectedDevice.getServiceUrl(),
                        selectedDevice.getRenderingControlUrl()
                );

            }, 2000);

        } catch (Exception e) {

            callback.onLoading(false);
            callback.onError("Erro ao iniciar streaming: " + e.getMessage());

        }
    }

}
