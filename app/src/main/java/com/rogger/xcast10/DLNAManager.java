package com.rogger.xcast10;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
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
    private static boolean isSeeking = false;
    private static final String TAG = "DLNAManager";
    private static final String SSDP_IP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
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
                        InetAddress.getByName(SSDP_IP), SSDP_PORT);

                socket.send(packet);

                byte[] buf = new byte[2048];

                while (true) {
                    try {
                        DatagramPacket resp = new DatagramPacket(buf, buf.length);
                        socket.receive(resp);

                        String response = new String(resp.getData(), 0, resp.getLength());
                       // Log.d("SSDP", response);

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
                //Log.e(TAG, "Erro SSDP", e);
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
                    //Log.d(TAG, "Device encontrado: " + name);
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro lendo device", e);
            }
        }).start();
    }

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
        Log.d(TAG, "url -->" + url);
        Log.d(TAG, "videoUrl -->" +videoUrl);
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
     * Algumas TVs exigem que o vídeo seja pausado antes de realizar o Seek para evitar o erro "função não disponível".
     */
    public static void seek(String url, String time) {
        if (isSeeking) {
            Log.d(TAG, "⛔ SEEK já em execução, ignorando...");
            return;
        }
            isSeeking = true;

            new Thread(() -> {
                try {

                    Log.d(TAG, "🔥 SEEK CONTROLADO INICIADO");

                    // Garantir que está tocando
                    play(url);

                    // Esperar estabilizar
                    Thread.sleep(3500);

                    boolean ok = sendSeek(url, "REL_TIME", time);

                    if (ok) {
                        Log.d(TAG, "✅ SEEK executado com sucesso!");
                    } else {
                        Log.e(TAG, "❌ SEEK falhou!");
                    }

                    // Esperar estabilizar antes de liberar novo seek
                    Thread.sleep(2000);

                } catch (Exception e) {
                    Log.e(TAG, "Erro no seekProper", e);
                } finally {
                    isSeeking = false;
                    Log.d(TAG, "🔓 SEEK liberado novamente");
                }
        }).start();
    }
    private static boolean sendSeek(String url, String unit, String time) {
        try {

            String args =
                    "<Unit>" + unit + "</Unit>" +
                            "<Target>" + time + "</Target>";

            HttpURLConnection conn =
                    (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty(
                    "SOAPACTION",
                    "\"urn:schemas-upnp-org:service:AVTransport:1#Seek\""
            );

            conn.setDoOutput(true);

            String xml =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                            + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                            + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                            + "<s:Body>"
                            + "<u:Seek xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"
                            + "<InstanceID>0</InstanceID>"
                            + args
                            + "</u:Seek>"
                            + "</s:Body>"
                            + "</s:Envelope>";

            OutputStream os = conn.getOutputStream();
            os.write(xml.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();

            Log.d(TAG, "SEEK " + unit + " RESPONSE: " + responseCode);

            if (responseCode == 200) {
                Log.d(TAG, "✅ SUPORTA " + unit);
                conn.disconnect();
                return true;
            }

            InputStream error = conn.getErrorStream();
            if (error != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(error));
                String line;
                while ((line = br.readLine()) != null) {
                    Log.d(TAG, "ERRO SOAP: " + line);
                }
                br.close();
            }

            conn.disconnect();
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Erro testando " + unit, e);
            return false;
        }
    }
    public static void getPositionInfo(String url) {
        sendSoapSync(
                url,
                "urn:schemas-upnp-org:service:AVTransport:1",
                "GetPositionInfo",
                ""
        );
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
    private static void sendSoap(String url, String service, String action, String args) {
        new Thread(() -> sendSoapSync(url, service, action, args)).start();
    }
    /**
     * Versão síncrona do envio SOAP para permitir sequenciamento de comandos (como no Seek).
     */
    private static void sendSoapSync(String url, String service, String action, String args) {
        HttpURLConnection conn = null;

        try {
            Log.d(TAG, "===============================");
            Log.d(TAG, "SOAP ACTION: " + action);
            Log.d(TAG, "URL: " + url);
            Log.d(TAG, "ARGS: " + args);

            conn = (HttpURLConnection) new URL(url).openConnection();

            conn.setRequestMethod("POST");

            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);

            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPACTION", "\"" + service + "#" + action + "\"");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            String xml =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                            + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                            + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                            + "<s:Body>"
                            + "<u:" + action + " xmlns:u=\"" + service + "\">"
                            + "<InstanceID>0</InstanceID>"
                            + args
                            + "</u:" + action + ">"
                            + "</s:Body>"
                            + "</s:Envelope>";

            Log.d(TAG, "SOAP XML:\n" + xml);

            OutputStream os = conn.getOutputStream();
            os.write(xml.getBytes("UTF-8"));
            os.flush();
            os.close();

            // 🔥 IMPORTANTE: forçar envio antes de ler resposta
            conn.connect();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "HTTP RESPONSE: " + responseCode);

            // 🔍 Ler resposta se existir
            InputStream is;

            if (responseCode >= 200 && responseCode < 400) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                Log.d(TAG, "SOAP RESPONSE: " + response.toString());
            }

        } catch (SocketTimeoutException e) {
            Log.w(TAG, "⚠ Timeout (mas pode ter executado): " + action);
        } catch (Exception e) {
            Log.e(TAG, "Erro SOAP " + action, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

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