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
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.HttpURLConnection
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
        var lock: WifiManager.MulticastLock? = null
        var socket: DatagramSocket? = null

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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
            lock?.let { if (it.isHeld) it.release() }
            close()
        }

        awaitClose {
            socket?.close()
            lock?.let { if (it.isHeld) it.release() }
        }
    }

    /**
     * Revalida um dispositivo já conhecido, buscando de novo a sua descrição XML
     * (a mesma [DLNADevice.location] usada na descoberta original) para obter o
     * `controlURL` (AVTransport/RenderingControl) ATUAL.
     *
     * Isto é necessário porque muitas Smart TVs geram uma porta UPnP diferente a cada
     * reinício/standby do serviço DLNA — se usarmos o serviceUrl "antigo" guardado na
     * descoberta, o comando SOAP falha com ConnectException (porta já não existe).
     *
     * Devolve `null` se a TV não estiver mais acessível na rede.
     */
    suspend fun refreshDevice(device: DLNADevice): DLNADevice? = withContext(Dispatchers.IO) {
        fetchDeviceDetails(device.location)
    }

    private fun fetchDeviceDetails(location: String): DLNADevice? {
        return try {
            val url = URL(location)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
            }

            val content = BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                reader.readText()
            }

            val name = extract(content, "<friendlyName>", "</friendlyName>") ?: "Smart TV"
            var avTransport = extractServiceUrl(content, "AVTransport:1")
            var rendering = extractServiceUrl(content, "RenderingControl:1")

            Log.d("DLNA_XML", "Location: $location")

            if (avTransport == null || !content.contains("AVTransport:1")) {
                return null
            }

            val base = url.protocol + "://" + url.host + (if (url.port != -1) ":${url.port}" else "")

            if (!avTransport.startsWith("/")) avTransport = "/$avTransport"
            if (rendering != null && !rendering.startsWith("/")) rendering = "/$rendering"

            DLNADevice(
                name = name,
                location = location,
                serviceUrl = base + avTransport,
                renderingControlUrl = rendering?.let { base + it }
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
        } catch (ignored: Exception) {
        }
        return "127.0.0.1"
    }
}

