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
     * (que a LG webOS aceita mas não implementa de facto — retorna 200 ou ignora), gera um
     * NOVO arquivo MP4 válido a partir do ponto pedido (via [SeekMp4Generator]/FFmpeg,
     * começando num keyframe real) e reinicia a transmissão servindo esse arquivo.
     *
     * ALTERADO POR COMPLETO: a versão anterior fazia FileInputStream.skip() com um offset de
     * bytes proporcional ao tempo — isso quebrava a estrutura do MP4 (sem ftyp/moov, sem
     * alinhamento a keyframe) e a LG webOS respondia com HTTP 500 ao SetAVTransportURI.
     * Agora o arquivo servido é sempre um MP4 estruturalmente correto.
     *
     * Continua protegido pelo mesmo [mutex] de startStreaming: uma segunda chamada (o
     * utilizador arrastando a barra de novo) só começa depois que a anterior — geração do
     * arquivo incluída — tiver terminado por completo. Isso também garante, por construção,
     * que nunca existem duas gerações de arquivo temporário simultâneas.
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
                    // Gera (via FFmpeg) um MP4 novo, válido, começando em positionMs.
                    // O próprio SeekMp4Generator apaga o temporário da chamada anterior antes
                    // de criar o novo, então nunca há dois arquivos "vivos" ao mesmo tempo.
                    when (val genResult = SeekMp4Generator.generate(context, item.uri, positionMs)) {
                        is SeekFileResult.Error -> throw IllegalStateException(genResult.message)

                        is SeekFileResult.Success -> {
                            server?.stop()
                            val newServer = LocalHttpServer(context, PORT).apply {
                                setVideoFile(genResult.file)
                                start()
                            }
                            server = newServer

                            val ip = DLNADiscoveryManager.getLocalIpAddress()
                            // Token no PATH (não query string): alguns players de TV, incluindo
                            // a webOS, ignoram query string para fins de cache mas nunca
                            // ignoram o path. O NanoHTTPD aqui ignora o path recebido (serve()
                            // sempre serve o arquivo atual), então isso é seguro.
                            val videoUrl = "http://$ip:$PORT/${System.currentTimeMillis()}.mp4"
                            Log.d(TAG, "Seek (novo MP4 via FFmpeg) URL: $videoUrl, positionMs: $positionMs")

                            val conn = URL(videoUrl).openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.connectTimeout = 3000

                            if (conn.responseCode != 200) {
                                throw IllegalStateException("Servidor HTTP não respondeu corretamente")
                            }

                            DLNAControlManager.setAVTransportURI(deviceServiceUrl, videoUrl)

                            // ALTERADO: removido o delay(6000) fixo que existia aqui — a
                            // confirmação de que a TV carregou a mídia é feita inteiramente
                            // por polling via waitForMediaReady (GetPositionInfo/TrackDuration).
                            val ready = DLNAControlManager.waitForMediaReady(
                                deviceServiceUrl,
                                timeoutMs = 12_000,
                                pollIntervalMs = 500
                            )
                            if (!ready) {
                                Log.w(TAG, "TV não confirmou TrackDuration a tempo; enviando Play mesmo assim")
                            }

                            DLNAControlManager.play(deviceServiceUrl)

                            StreamingResult.Success(item, deviceServiceUrl, device.renderingControlUrl)
                        }
                    }
                } catch (e: Exception) {
                    StreamingResult.Error("Erro ao avançar o vídeo: ${e.message}")
                }
                result
            }
        }
    }

    fun stopServer(context: Context? = null) {
        server?.stop()
        server = null
        // Limpa o temporário de seek ao encerrar a transmissão, já que ele deixa de ter uso.
        context?.let { SeekMp4Generator.deleteTemp(it) }
    }

    private const val TAG = "StreamingManager"
}

