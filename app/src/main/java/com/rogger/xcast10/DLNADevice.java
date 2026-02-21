package com.rogger.xcast10;

/**
 * Representa um dispositivo DLNA (Digital Living Network Alliance) encontrado na rede.
 * Armazena informações essenciais como o nome, URL de descrição e URLs de controlo de serviço.
 */
public class DLNADevice {
    private String name;
    private String location; // URL do XML de descrição do dispositivo
    private String serviceUrl; // URL para controle AVTransport
    private String renderingControlUrl; // ← NOVO


    public DLNADevice(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }

    public String getRenderingControlUrl() {
        return renderingControlUrl;
    }

    public void setRenderingControlUrl(String renderingControlUrl) {
        this.renderingControlUrl = renderingControlUrl;
    }


    @Override
    public String toString() { return name; }
}
