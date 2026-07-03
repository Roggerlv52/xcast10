package com.rogger.xcast10.network

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:14
 */
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Gestor responsável por enviar comandos SOAP UPnP (AVTransport / RenderingControl)
 * para controlar a reprodução de vídeo numa Smart TV (play, pause, stop, seek, volume).
 */
object DLNAControlManager {

    private const val TAG = "DLNAControlManager"
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    @Volatile
    private var isSeeking = false

    fun setAVTransportURI(url: String, videoUrl: String) {
        val meta = "<CurrentURI>$videoUrl</CurrentURI>" +
                "<CurrentURIMetaData>" +
                "&lt;DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
                "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" " +
                "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"&gt;" +
                "&lt;item id=\"0\" parentID=\"0\" restricted=\"0\"&gt;" +
                "&lt;dc:title&gt;Video&lt;/dc:title&gt;" +
                "&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;" +
                "&lt;res protocolInfo=\"http-get:*:video/mp4:*\"&gt;$videoUrl&lt;/res&gt;" +
                "&lt;/item&gt;" +
                "&lt;/DIDL-Lite&gt;" +
                "</CurrentURIMetaData>"

        sendCommandAsync(url, "SetAVTransportURI", meta)
    }

    fun play(url: String) = sendCommandAsync(url, "Play", "<Speed>1</Speed>")

    fun pause(url: String) = sendCommandAsync(url, "Pause", "")

    fun stop(url: String) = sendCommandAsync(url, "Stop", "")

    fun sendRenderingCommand(url: String, action: String, args: String) =
        sendSoapAsync(url, RENDERING_CONTROL, action, args)

    /**
     * Envia o comando Seek para a TV, garantindo primeiro que está a reproduzir (algumas TVs
     * exigem que o vídeo esteja em Play antes de aceitar o Seek).
     */
    fun seek(url: String, time: String) {
        if (isSeeking) {
            Log.d(TAG, "Seek já em execução, ignorando...")
            return
        }
        isSeeking = true

        Thread {
            try {
                play(url)
                Thread.sleep(3500)
                val ok = sendSeekSync(url, "REL_TIME", time)
                Log.d(TAG, if (ok) "Seek executado com sucesso" else "Seek falhou")
                Thread.sleep(2000)
            } catch (e: Exception) {
                Log.e(TAG, "Erro no seek", e)
            } finally {
                isSeeking = false
            }
        }.start()
    }

    private fun sendSeekSync(url: String, unit: String, time: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val args = "<Unit>$unit</Unit><Target>$time</Target>"
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#Seek\"")
                doOutput = true
            }

            val xml = buildSoapEnvelope("Seek", AV_TRANSPORT, args)
            conn.outputStream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            Log.d(TAG, "SEEK $unit RESPONSE: $responseCode")
            responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Erro testando $unit", e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun sendCommandAsync(url: String, action: String, args: String) =
        sendSoapAsync(url, AV_TRANSPORT, action, args)

    private fun sendSoapAsync(url: String, service: String, action: String, args: String) {
        Thread { sendSoapSync(url, service, action, args) }.start()
    }

    /** Versão suspensa (coroutine) do envio SOAP, útil para chamadas sequenciais a partir do ViewModel. */
    suspend fun sendCommand(url: String, action: String, args: String) = withContext(Dispatchers.IO) {
        sendSoapSync(url, AV_TRANSPORT, action, args)
    }

    private fun sendSoapSync(url: String, service: String, action: String, args: String) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$service#$action\"")
                doOutput = true
                doInput = true
            }

            val xml = buildSoapEnvelope(action, service, args)
            conn.outputStream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
            conn.connect()

            val responseCode = conn.responseCode
            Log.d(TAG, "$action -> HTTP $responseCode")

            val stream: InputStream? = if (responseCode in 200..399) conn.inputStream else conn.errorStream
            stream?.let {
                BufferedReader(InputStreamReader(it)).use { br -> br.readText() }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Timeout (mas pode ter executado): $action")
        } catch (e: Exception) {
            Log.e(TAG, "Erro SOAP $action", e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildSoapEnvelope(action: String, service: String, args: String): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body>" +
                "<u:$action xmlns:u=\"$service\">" +
                "<InstanceID>0</InstanceID>" +
                args +
                "</u:$action>" +
                "</s:Body>" +
                "</s:Envelope>"
}
