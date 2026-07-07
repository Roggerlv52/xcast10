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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

    // NOVO: garante que só existe UMA operação de start/seek em andamento por vez.
    // Antes, se o utilizador arrastasse a barra de novo antes do seek anterior terminar,
    // o servidor antigo era derrubado no meio de uma transferência (causando "Broken pipe"
    // no NanoHTTPD) e a TV recebia comandos SOAP sobrepostos, respondendo HTTP 500.
    // Com o Mutex, uma segunda chamada só começa DEPOIS que a anterior terminar por completo.
    private val mutex = Mutex()

    suspend fun startStreaming(
        context: Context,
        item: VideoItem,
        selectedDevice: DLNADevice?
    ): StreamingResult {
        val device = selectedDevice
            ?: return StreamingResult.Error("Por favor, selecione um dispositivo primeiro")

        val deviceServiceUrl = device.serviceUrl
            ?: return StreamingResult.Error("Dispositivo selecionado é inválido")

        // ALTERADO: todo o corpo agora roda dentro de mutex.withLock (antes não havia trava nenhuma)
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val result: StreamingResult = try {
                    server?.stop()
                    val newServer = LocalHttpServer(context, PORT).apply {
                        setVideoUri(item.uri)
                        start()
                    }
                    server = newServer

                    val ip = DLNADiscoveryManager.getLocalIpAddress()
                    val videoUrl = "http://$ip:$PORT/video.mp4"
                    Log.d(TAG, "URL: $videoUrl")

                    val conn = URL(videoUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 3000

                    if (conn.responseCode != 200) {
                        throw IllegalStateException("Servidor HTTP não respondeu corretamente")
                    }

                    DLNAControlManager.setAVTransportURI(deviceServiceUrl, videoUrl)

                    // ALTERADO: era "delay(2000)" fixo — agora aguarda ativamente (polling) a TV
                    // confirmar que carregou a mídia (TrackDuration > 0) antes de mandar o Play.
                    // Uma pequena espera inicial dá tempo do SetAVTransportURI ser processado
                    // antes da primeira consulta.
                    delay(6000)
                    DLNAControlManager.waitForMediaReady(deviceServiceUrl)

                    DLNAControlManager.play(deviceServiceUrl)

                    StreamingResult.Success(item, deviceServiceUrl, device.renderingControlUrl)
                } catch (e: Exception) {
                    StreamingResult.Error("Erro ao iniciar streaming: ${e.message}")
                }
                result
            }
        }
    }

    /**
     * Fallback confiável para "seek": em vez de depender do comando Seek nativo do DLNA
     * (que muitas Smart TVs aceitam mas não implementam de facto), reinicia a transmissão
     * servindo o ficheiro a partir de um offset de bytes proporcional a [positionMs].
     * A TV recebe uma "nova" URL e começa a reproduzir a partir do ponto desejado.
     *
     * ALTERADO: agora protegido pelo mesmo [mutex] de startStreaming — se já houver um
     * seek em andamento, esta chamada aguarda ele terminar antes de derrubar o servidor,
     * em vez de derrubá-lo no meio de uma transferência ativa com a TV.
     */
    suspend fun seekByRestarting(
        context: Context,
        item: VideoItem,
        selectedDevice: DLNADevice?,
        positionMs: Long
    ): StreamingResult {
        val device = selectedDevice
            ?: return StreamingResult.Error("Por favor, selecione um dispositivo primeiro")

        val deviceServiceUrl = device.serviceUrl
            ?: return StreamingResult.Error("Dispositivo selecionado é inválido")

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val result: StreamingResult = try {
                    server?.stop()
                    val newServer = LocalHttpServer(context, PORT).apply {
                        setVideoUri(item.uri, startPositionMs = positionMs, totalDurationMs = item.duration)
                        start()
                    }
                    server = newServer

                    val ip = DLNADiscoveryManager.getLocalIpAddress()
                    // ALTERADO: era "?seek=timestamp" (query string) — trocado para um token no
                    // PATH, pois alguns players de TV (ex: webOS) ignoram query string para fins
                    // de cache mas nunca ignoram o path. O NanoHTTPD nesta classe ignora o path
                    // recebido (serve() sempre serve o videoUri atual), então isso é seguro.
                    val videoUrl = "http://$ip:$PORT/${System.currentTimeMillis()}.mp4"
                    Log.d(TAG, "Seek (restart) URL: $videoUrl, offset ms: $positionMs")

                    val conn = URL(videoUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 3000

                    if (conn.responseCode != 200) {
                        throw IllegalStateException("Servidor HTTP não respondeu corretamente")
                    }
                    DLNAControlManager.setAVTransportURI(deviceServiceUrl, videoUrl)

                    // ALTERADO: era "delay(1200)" fixo — agora aguarda ativamente (polling) a TV
                    // confirmar a nova duração. Timeout menor que no startStreaming, pois a TV
                    // já está "quente" (já tinha um vídeo a tocar antes deste seek).
                    delay(6000)
                    DLNAControlManager.waitForMediaReady(deviceServiceUrl, timeoutMs = 8_000, pollIntervalMs = 500)

                    DLNAControlManager.play(deviceServiceUrl)

                    StreamingResult.Success(item, deviceServiceUrl, device.renderingControlUrl)
                } catch (e: Exception) {
                    StreamingResult.Error("Erro ao avançar o vídeo: ${e.message}")
                }
                result
            }
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
    }

    private const val TAG = "StreamingManager"
}
