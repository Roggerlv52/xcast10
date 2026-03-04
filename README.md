# Android Video Cast App (Java)

Este é um aplicativo Android nativo desenvolvido em Java para transmitir vídeos locais do seu smartphone para  Smart TV compatível com DLNA/UPnP na mesma rede WiFi.
"Atualmete so foi testado em tv LG"

## Funcionalidades
- **Descoberta de Dispositivos**: Busca automática de Smart TVs na rede via protocolo SSDP.
- **Seleção de Vídeo**: Interface para escolher vídeos salvos no dispositivo.
- **Servidor Local**: Hospeda o vídeo selecionado em um servidor HTTP temporário para que a TV possa acessá-lo.
- **Controle de Reprodução**:
    - Play / Pause.
    - Controle de Volume (+ / -).
    - Barra de progresso (SeekBar) para avançar ou retroceder o vídeo.
    - Cancelar transmissão.

## Compatibilidade
- Desenvolvido para **Android 11 (API 30)** até **Android 14 (API 34)** ou superior.
- Requer permissões de rede e acesso a mídias (tratadas dinamicamente).

## Como usar
1. Abra o projeto no **Android Studio**.
2. Certifique-se de que o celular e a Smart TV estão na **mesma rede WiFi**.
3. Clique em "Buscar Dispositivos" e selecione sua TV na lista.
4. Clique em "Selecionar Vídeo" e escolha o arquivo desejado.
5. O controle de reprodução abrirá automaticamente assim que a transmissão iniciar.

## Bibliotecas Utilizadas
- `androidx.mediarouter`: Para gerenciamento de rotas de mídia.
- `org.nanohttpd`: Para criar o servidor HTTP que serve o arquivo de vídeo para a TV.
- `Google Material Design`: Para a interface do usuário.
