package com.rogger.xcast10.network

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:15
 */

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.rogger.xcast10.data.model.DLNADevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Collections

/**
 * Gestor responsável pela descoberta de dispositivos DLNA (Smart TVs / MediaRenderers)
 * na rede local através do protocolo SSDP (Simple Service Discovery Protocol).
 */
object DLNADiscoveryManager {

    private const val TAG = "DLNADiscoveryManager"
    private const val SSDP_IP = "239.255.255.250"
    private const val SSDP_PORT = 1900

    sealed interface DiscoveryEvent {
        data class DeviceFound(val device: DLNADevice) : DiscoveryEvent
        data class Finished(val message: String) : DiscoveryEvent
    }

    /**
     * Inicia uma descoberta SSDP e emite os dispositivos encontrados através de um [Flow].
     * O flow termina automaticamente após o timeout da busca (ver [DiscoveryEvent.Finished]).
     */
    fun discoverDevices(context: Context): Flow<DiscoveryEvent> = callbackFlow {

        val job = launch(Dispatchers.IO) {

            var lock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null

            try {

                val wifi = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager

                lock = wifi.createMulticastLock("ssdp").apply {
                    setReferenceCounted(true)
                    acquire()
                }

                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(1901))
                    soTimeout = 3000
                }
                val query = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

                val packet = DatagramPacket(
                    query.toByteArray(), query.length,
                    InetAddress.getByName(SSDP_IP), SSDP_PORT
                )
                socket.send(packet)
                val buf = ByteArray(2048)
                val seenLocations = mutableSetOf<String>()

                while (true) {
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val response = String(resp.data, 0, resp.length)

                        val location = parseHeader(response, "LOCATION")
                        if (location != null && seenLocations.add(location)) {
                            fetchDeviceDetails(location)?.let { device ->
                                trySend(DiscoveryEvent.DeviceFound(device))
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                }
                trySend(DiscoveryEvent.Finished("Nenhuma Smart TV encontrada na rede"))
            } catch (e: Exception) {
                Log.e(TAG, "Erro SSDP", e)
                trySend(DiscoveryEvent.Finished("Nenhuma Smart TV encontrada na rede"))

            } finally {
                socket?.close()
                lock?.release()
                close()
            }
        }

        awaitClose {
            job.cancel()
        }
    }

    suspend fun refreshDevice(device: DLNADevice): DLNADevice? = withContext(Dispatchers.IO) {
        fetchDeviceDetails(device.location)
    }

    private fun fetchDeviceDetails(location: String): DLNADevice? {
        return try {

            val url = URL(location)

            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }

            val content = conn.inputStream.bufferedReader().use {
                it.readText()
            }

            Log.d(TAG, "===== DEVICE XML =====")
            Log.d(TAG, content)

            val name =
                extract(content, "<friendlyName>", "</friendlyName>")
                    ?: "Smart TV"

            val avTransport =
                extractServiceUrl(content, "AVTransport:1")
                    ?: extractServiceUrl(content, "AVTransport:2")
                    ?: extractServiceUrl(content, "AVTransport:3")

            val rendering =
                extractServiceUrl(content, "RenderingControl:1")
                    ?: extractServiceUrl(content, "RenderingControl:2")

            if (avTransport == null) {
                Log.d(TAG, "AVTransport não encontrado.")
                return null
            }

            val base =
                "${url.protocol}://${url.host}" +
                        if (url.port != -1) ":${url.port}" else ""

            val avUrl =
                if (avTransport.startsWith("/"))
                    base + avTransport
                else
                    "$base/$avTransport"

            val renderingUrl =
                rendering?.let {
                    if (it.startsWith("/"))
                        base + it
                    else
                        "$base/$it"
                }

            Log.d(TAG, "TV encontrada: $name")
            Log.d(TAG, "AVTransport=$avUrl")
            Log.d(TAG, "Rendering=$renderingUrl")

            DLNADevice(
                name = name,
                location = location,
                serviceUrl = avUrl,
                renderingControlUrl = renderingUrl
            )

        } catch (e: Exception) {
            Log.e(TAG, "Erro lendo device", e)
            null
        }
    }

    private fun extract(xml: String, start: String, end: String): String? {
        if (!xml.contains(start)) return null
        return xml.substring(xml.indexOf(start) + start.length, xml.indexOf(end))
    }

    private fun extractServiceUrl(xml: String, service: String): String? {
        if (!xml.contains(service)) return null
        val sub = xml.substring(xml.indexOf(service))
        return extract(sub, "<controlURL>", "</controlURL>")
    }

    private fun parseHeader(response: String, header: String): String? {
        for (line in response.split("\r\n")) {
            if (line.lowercase().startsWith("${header.lowercase()}:")) {
                return line.substring(header.length + 1).trim()
            }
        }
        return null
    }

    /** Devolve o endereço IPv4 local do dispositivo (usado para montar a URL do servidor HTTP). */
    fun getLocalIpAddress(): String {
        try {
            for (intf in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ignored : Exception) {
        }
        return "127.0.0.1"
    }
}

