package com.rogger.xcast10.viewmodel

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:26
 */

// ALTERADO: removido "import com.rogger.xcast10.util.TimeFormatter" (não é mais usado — seekTo não monta mais o REL_TIME)
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rogger.xcast10.data.VideoRepository
import com.rogger.xcast10.data.model.DLNADevice
import com.rogger.xcast10.data.model.PlaybackState
import com.rogger.xcast10.data.model.VideoItem
import com.rogger.xcast10.network.DLNAControlManager
import com.rogger.xcast10.network.DLNADiscoveryManager
import com.rogger.xcast10.service.StreamingManager
import com.rogger.xcast10.service.StreamingResult
import com.rogger.xcast10.util.PreferencesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Estado observável de toda a UI (Home + Descoberta + Galeria + Player).
 */
data class CastUiState(
    val isDiscovering: Boolean = false,
    val discoveredDevices: List<DLNADevice> = emptyList(),
    val selectedDevice: DLNADevice? = null,
    val discoveryMessage: String? = null,
    val lastVideoTitle: String? = null,
    val isLoadingVideos: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val isStartingStream: Boolean = false,
    val streamError: String? = null,
    val playback: PlaybackState = PlaybackState()
)

/** Eventos únicos (não fazem parte do estado persistente) consumidos pela navegação. */
sealed interface CastEvent {
    data object NavigateToPlayer : CastEvent
    data object StreamingStopped : CastEvent
}

/**
 * ViewModel único que serve de fonte de verdade para todos os ecrãs (Home, Descoberta,
 * Galeria e Player), seguindo a mesma arquitetura MVVM + StateFlow usada no projeto
 * Gravador_de_audio.
 */
class CastViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(CastUiState())
    val uiState: StateFlow<CastUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CastEvent>()
    val events: SharedFlow<CastEvent> = _events.asSharedFlow()

    private var progressJob: Job? = null
    private var currentPlayingItem: VideoItem? = null
    private var seekJob: Job? = null // NOVO: referência ao seek em andamento, para cancelar tentativas obsoletas

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lastVideoTitle = prefs.lastVideoTitle.first())
        }
    }

    // =========================================================
    // DESCOBERTA DE DISPOSITIVOS
    // =========================================================

    fun discoverDevices() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            isDiscovering = true,
            discoveredDevices = emptyList(),
            discoveryMessage = null
        )

        viewModelScope.launch {
            DLNADiscoveryManager.discoverDevices(context).collect { event ->
                when (event) {
                    is DLNADiscoveryManager.DiscoveryEvent.DeviceFound -> {
                        val current = _uiState.value.discoveredDevices
                        if (current.none { it.serviceUrl == event.device.serviceUrl }) {
                            _uiState.value = _uiState.value.copy(
                                discoveredDevices = current + event.device
                            )
                        }
                    }

                    is DLNADiscoveryManager.DiscoveryEvent.Finished -> {
                        _uiState.value = _uiState.value.copy(
                            isDiscovering = false,
                            discoveryMessage = if (_uiState.value.discoveredDevices.isEmpty()) event.message else null
                        )
                    }
                }
            }
        }
    }

    fun selectDevice(device: DLNADevice) {
        _uiState.value = _uiState.value.copy(selectedDevice = device)
    }

    fun disconnectDevice() {
        _uiState.value = _uiState.value.copy(selectedDevice = null)
    }

    fun consumeDiscoveryMessage() {
        _uiState.value = _uiState.value.copy(discoveryMessage = null)
    }

    // =========================================================
    // GALERIA DE VÍDEOS
    // =========================================================

    fun loadVideos() {
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(isLoadingVideos = true)
        viewModelScope.launch {
            val list = VideoRepository.getVideoList(context)
            _uiState.value = _uiState.value.copy(isLoadingVideos = false, videos = list)
        }
    }

    fun playVideo(item: VideoItem) {
        val context = getApplication<Application>()
        val device = _uiState.value.selectedDevice

        if (device == null) {
            _uiState.value = _uiState.value.copy(streamError = "Por favor, selecione um dispositivo primeiro")
            return
        }

        _uiState.value = _uiState.value.copy(isStartingStream = true)

        viewModelScope.launch {
            // NOVO: revalida o dispositivo antes de transmitir — a porta de controlo (controlURL)
            // pode ter mudado desde a descoberta (ex: TV reiniciou o serviço DLNA), o que antes
            // causava "Failed to connect" ao tentar SetAVTransportURI/Play com a porta antiga.
            val freshDevice = DLNADiscoveryManager.refreshDevice(device)
            if (freshDevice == null) {
                _uiState.value = _uiState.value.copy(
                    isStartingStream = false,
                    streamError = "Não foi possível conectar à TV. Procure o dispositivo novamente."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(selectedDevice = freshDevice) // NOVO: atualiza com a URL atual

            when (val result = StreamingManager.startStreaming(context, item, freshDevice)) {
                is StreamingResult.Success -> {
                    currentPlayingItem = item
                    prefs.setLastVideoTitle(item.title)
                    _uiState.value = _uiState.value.copy(
                        isStartingStream = false,
                        lastVideoTitle = item.title,
                        playback = PlaybackState(
                            deviceUrl = result.deviceUrl,
                            renderingControlUrl = result.renderingControlUrl,
                            videoTitle = item.title,
                            durationMs = item.duration,
                            positionMs = 0L,
                            isPlaying = true,
                            volume = 10
                        )
                    )
                    startProgressLoop()
                    _events.emit(CastEvent.NavigateToPlayer)
                }

                is StreamingResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isStartingStream = false,
                        streamError = result.message
                    )
                }
            }
        }
    }

    fun consumeStreamError() {
        _uiState.value = _uiState.value.copy(streamError = null)
    }

    // =========================================================
    // CONTROLO DE REPRODUÇÃO
    // =========================================================

    fun togglePlayPause() {
        val playback = _uiState.value.playback
        val deviceUrl = playback.deviceUrl ?: return

        val nowPlaying = !playback.isPlaying
        _uiState.value = _uiState.value.copy(playback = playback.copy(isPlaying = nowPlaying))

        if (nowPlaying) DLNAControlManager.play(deviceUrl) else DLNAControlManager.pause(deviceUrl)
    }

    fun volumeUp() = updateVolume((_uiState.value.playback.volume + 1).coerceAtMost(100))

    fun volumeDown() = updateVolume((_uiState.value.playback.volume - 1).coerceAtLeast(0))

    private fun updateVolume(newVolume: Int) {
        val playback = _uiState.value.playback
        _uiState.value = _uiState.value.copy(playback = playback.copy(volume = newVolume))

        val targetUrl = playback.renderingControlUrl ?: playback.deviceUrl ?: return
        val args = "<Channel>Master</Channel><DesiredVolume>$newVolume</DesiredVolume>"
        DLNAControlManager.sendRenderingCommand(targetUrl, "SetVolume", args)
    }

    /** Chamado quando o utilizador larga a SeekBar; [positionSeconds] é a posição em segundos. */
    fun seekTo(positionSeconds: Int) {
        val playback = _uiState.value.playback
        val device = _uiState.value.selectedDevice
        val item = currentPlayingItem
        val positionMs = positionSeconds * 1000L

        if (device == null || item == null) return

        // Atualiza a UI otimisticamente enquanto a transmissão é reiniciada no ponto certo
        _uiState.value = _uiState.value.copy(
            playback = playback.copy(positionMs = positionMs, isPlaying = true)
        )

        // NOVO: cancela um seek anterior que ainda esteja em andamento — sem isso, arrastar a
        // barra várias vezes seguidas enfileirava vários seeks obsoletos, um atrás do outro,
        // deixando a TV "atrasada" processando pontos que o utilizador já não quer mais.
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            when (val result = StreamingManager.seekByRestarting(
                getApplication(), item, device, positionMs
            )) {
                is StreamingResult.Success -> {
                    // stream reiniciado com sucesso a partir do novo ponto
                }
                is StreamingResult.Error -> {
                    _uiState.value = _uiState.value.copy(streamError = result.message)
                }
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val playback = _uiState.value.playback
                if (!playback.isActive) break
                if (playback.isPlaying && playback.positionMs < playback.durationMs) {
                    _uiState.value = _uiState.value.copy(
                        playback = playback.copy(positionMs = playback.positionMs + 1000)
                    )
                }
            }
        }
    }

    fun cancelStreaming() {
        val deviceUrl = _uiState.value.playback.deviceUrl
        viewModelScope.launch {
            try {
                if (deviceUrl != null) DLNAControlManager.stop(deviceUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parar transmissão", e)
            } finally {
                StreamingManager.stopServer()
                progressJob?.cancel()
                _uiState.value = _uiState.value.copy(playback = PlaybackState())
                _events.emit(CastEvent.StreamingStopped)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        seekJob?.cancel() // NOVO
    }

    private companion object {
        const val TAG = "CastViewModel"
    }
}
