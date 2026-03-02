package com.rogger.xcast10;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Collections;

/**
 * Gestor responsável pela descoberta de dispositivos DLNA na rede local via protocolo SSDP.
 * Gere a lista de dispositivos disponíveis e as interações de rede para controlo de media via SOAP.
 */
public class DLNAManager {

    private static final String TAG = "DLNAManager";
    private static final String SSDP_IP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;

    // Campo estático para persistir o dispositivo selecionado durante a sessão do app
    private static DLNADevice selectedDevice;

    public static DLNADevice getSelectedDevice() {
        return selectedDevice;
    }

    public static void setSelectedDevice(DLNADevice device) {
        selectedDevice = device;
    }

    // =========================================================
    // CALLBACK
    // =========================================================

    public interface DiscoveryCallback {
        void onDeviceFound(DLNADevice device);
        void onFinished(String msg);
    }

    // =========================================================
    // DESCOBERTA SSDP
    // =========================================================

    public static void discoverDevices(Context context, DiscoveryCallback callback) {

        new Thread(() -> {
            try {

                WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                WifiManager.MulticastLock lock = wifi.createMulticastLock("ssdp");
                lock.setReferenceCounted(true);
                lock.acquire();

                DatagramSocket socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(1901));
                socket.setSoTimeout(3000);

                String query = "M-SEARCH * HTTP/1.1\r\n" + "HOST: 239.255.255.250:1900\r\n"
                        + "MAN: \"ssdp:discover\"\r\n" + "MX: 3\r\n"
                        + "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n";

                DatagramPacket packet = new DatagramPacket(query.getBytes(), query.length(),
                        InetAddress.getByName("239.255.255.250"), 1900);

                socket.send(packet);

                byte[] buf = new byte[2048];

                while (true) {
                    try {
                        DatagramPacket resp = new DatagramPacket(buf, buf.length);
                        socket.receive(resp);

                        String response = new String(resp.getData(), 0, resp.getLength());
                        Log.d("SSDP", response);

                        String location = parseHeader(response, "LOCATION");
                        if (location != null)
                            fetchDeviceDetails(location, callback);
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }

                socket.close();
                lock.release();

                callback.onFinished("Nenhuma Smart TV encontrada na rede");

            } catch (Exception e) {
                Log.e(TAG, "Erro SSDP", e);
                callback.onFinished("Nenhuma Smart TV encontrada na rede");
            }
        }).start();
    }

    // =========================================================
    // DETALHES DO DEVICE
    // =========================================================

    private static void fetchDeviceDetails(String location, DiscoveryCallback callback) {

        new Thread(() -> {
            try {
                URL url = new URL(location);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                StringBuilder xml = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                    xml.append(line);

                reader.close();

                String content = xml.toString();

                String name = extract(content, "<friendlyName>", "</friendlyName>");
                if (name == null)
                    name = "Smart TV";

                String avTransport = extractServiceUrl(content, "AVTransport:1");
                String rendering = extractServiceUrl(content, "RenderingControl:1");
                Log.d("DLNA_XML", "Location: " + location + " Content: " + content);
                if (avTransport != null) {

                    String base = url.getProtocol() + "://" + url.getHost()
                            + (url.getPort() != -1 ? ":" + url.getPort() : "");

                    if (!avTransport.startsWith("/"))
                        avTransport = "/" + avTransport;

                    if (rendering != null && !rendering.startsWith("/"))
                        rendering = "/" + rendering;

                    DLNADevice d = new DLNADevice(name, location);

                    d.setServiceUrl(base + avTransport);

                    if (rendering != null)
                        d.setRenderingControlUrl(base + rendering);

                    if (!content.contains("AVTransport:1")) {
                        Log.d(TAG, "Dispositivo ignorado: não é MediaRenderer");
                        return;
                    }
                    callback.onDeviceFound(d);
                    Log.d(TAG, "Device encontrado: " + name);
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro lendo device", e);
            }
        }).start();
    }

    // =========================================================
    // PARSERS
    // =========================================================

    private static String extract(String xml, String start, String end) {
        if (!xml.contains(start))
            return null;
        return xml.substring(xml.indexOf(start) + start.length(), xml.indexOf(end));
    }

    private static String extractServiceUrl(String xml, String service) {
        if (!xml.contains(service))
            return null;
        String sub = xml.substring(xml.indexOf(service));
        return extract(sub, "<controlURL>", "</controlURL>");
    }

    private static String parseHeader(String response, String header) {
        for (String line : response.split("\r\n")) {
            if (line.toLowerCase().startsWith(header.toLowerCase() + ":"))
                return line.substring(header.length() + 1).trim();
        }
        return null;
    }

    // =========================================================
    // COMANDOS AVTRANSPORT PRONTOS
    // =========================================================

    public static void setAVTransportURI(String url, String videoUrl) {

        String meta = "<CurrentURI>" + videoUrl + "</CurrentURI>" + "<CurrentURIMetaData>"
                + "&lt;DIDL-Lite xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" "
                + "xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"&gt;" +

                "&lt;item id=\"0\" parentID=\"0\" restricted=\"0\"&gt;" + "&lt;dc:title&gt;Video&lt;/dc:title&gt;"
                + "&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;"
                + "&lt;res protocolInfo=\"http-get:*:video/mp4:*\"&gt;" + videoUrl + "&lt;/res&gt;" + "&lt;/item&gt;" +

                "&lt;/DIDL-Lite&gt;" + "</CurrentURIMetaData>";

        sendCommand(url, "SetAVTransportURI", meta);
    }

    public static void play(String url) {

        sendCommand(url, "Play", "<Speed>1</Speed>");
    }

    public static void pause(String url) {
        sendCommand(url, "Pause", "");
    }

    public static void stop(String url) {
        sendCommand(url, "Stop", "");
    }

    /**
     * Envia o comando Seek para a TV. 
     * Para TVs LG, é crucial garantir que o InstanceID seja 0 e o Unit seja REL_TIME.
     * Algumas TVs LG respondem com erro 500 se o comando for enviado muito rápido após a pausa.
     */
    public static void seek(String url, String time) {
        new Thread(() -> {
            try {
                // 1. Pausa o vídeo
                pause(url);
                Thread.sleep(600); // Aumentado para 600ms para TVs LG

                // 2. Envia o comando Seek com formato estrito
                String args = "<InstanceID>0</InstanceID>" +
                             "<Unit>REL_TIME</Unit>" +
                             "<Target>" + time + "</Target>";
                
                // Nota: O sendSoapSync já adiciona InstanceID no corpo, 
                // mas para o Seek vamos garantir que está no lugar certo.
                sendSoapSync(url, "urn:schemas-upnp-org:service:AVTransport:1", "Seek", args, true);
                
                Thread.sleep(600); // Aguarda a TV processar o posicionamento

                // 3. Retoma a reprodução
                play(url);
            } catch (Exception e) {
                Log.e(TAG, "Erro no processo de Seek", e);
            }
        }).start();
    }

    // =========================================================
    // COMANDOS GENÉRICOS
    // =========================================================

    public static void sendCommand(String url, String action, String args) {
        sendSoap(url, "urn:schemas-upnp-org:service:AVTransport:1", action, args);
    }

    public static void sendRenderingCommand(String url, String action, String args) {
        sendSoap(url, "urn:schemas-upnp-org:service:RenderingControl:1", action, args);
    }

    // =========================================================
    // SOAP CORE
    // =========================================================

    private static void sendSoap(String url, String service, String action, String args) {
        new Thread(() -> sendSoapSync(url, service, action, args, false)).start();
    }

    private static void sendSoapSync(String url, String service, String action, String args, boolean isSeek) {
        try {
            Log.d(TAG, "SOAP " + action + " -> " + url);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000); // Aumentado para 5s
            conn.setReadTimeout(5000);

            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPACTION", "\"" + service + "#" + action + "\"");
            conn.setDoOutput(true);

            // Se for Seek, o args já contém o InstanceID para garantir a ordem correta exigida por algumas TVs
            String bodyContent = isSeek ? args : "<InstanceID>0</InstanceID>" + args;

            String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" + "<s:Body>" + "<u:" + action
                    + " xmlns:u=\"" + service + "\">" + bodyContent + "</u:" + action + ">"
                    + "</s:Body>" + "</s:Envelope>";

            OutputStream os = conn.getOutputStream();
            os.write(xml.getBytes());
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d(TAG, "Resposta " + action + " = " + code);

            if (code != 200) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) errorResponse.append(line);
                Log.e(TAG, "Erro na TV (" + code + "): " + errorResponse.toString());
            }

            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "Erro SOAP " + action, e);
        }
    }

    // =========================================================
    // IP LOCAL
    // =========================================================

    public static String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
