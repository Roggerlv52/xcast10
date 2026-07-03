package com.rogger.xcast10.service

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:19
 */
import android.content.Context
import android.util.Log
import com.rogger.xcast10.data.model.DLNADevice
import com.rogger.xcast10.data.model.VideoItem
import com.rogger.xcast10.network.DLNAControlManager
import com.rogger.xcast10.network.DLNADiscoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Resultado do arranque de uma sessão de streaming.
 */
sealed interface StreamingResult {
    data class Success(
        val item: VideoItem,
        val deviceUrl: String,
        val renderingControlUrl: String?
    ) : StreamingResult

    data class Error(val message: String) : StreamingResult
}

/**
 * Orquestra o início da transmissão: sobe um [LocalHttpServer] a servir o vídeo escolhido
 * e instrui o dispositivo DLNA selecionado a reproduzir a partir dessa URL.
 */
object StreamingManager {

    private const val PORT = 8080
    private var server: LocalHttpServer? = null

    suspend fun startStreaming(
        context: Context,
        item: VideoItem,
        selectedDevice: DLNADevice?
    ): StreamingResult = withContext(Dispatchers.IO) {

        val device = selectedDevice
            ?: return@withContext StreamingResult.Error("Por favor, selecione um dispositivo primeiro")

        val deviceServiceUrl = device.serviceUrl
            ?: return@withContext StreamingResult.Error("Dispositivo selecionado é inválido")

        return@withContext try {
            server?.stop()
            val newServer = LocalHttpServer(context, PORT).apply {
                setVideoUri(item.uri)
                start()
            }
            server = newServer

            val ip = DLNADiscoveryManager.getLocalIpAddress()
            val videoUrl = "http://$ip:$PORT/video.mp4"
            Log.d(TAG, "URL: $videoUrl")

            DLNAControlManager.setAVTransportURI(deviceServiceUrl, videoUrl)

            delay(2000)

            DLNAControlManager.play(deviceServiceUrl)

            StreamingResult.Success(item, deviceServiceUrl, device.renderingControlUrl)
        } catch (e: Exception) {
            StreamingResult.Error("Erro ao iniciar streaming: ${e.message}")
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
    }

    private const val TAG = "StreamingManager"
}
