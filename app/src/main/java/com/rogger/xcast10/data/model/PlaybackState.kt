package com.rogger.xcast10.data.model

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:12
 */
data class PlaybackState(
    val deviceUrl: String? = null,
    val renderingControlUrl: String? = null,
    val videoTitle: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = true,
    val volume: Int = 10
) {
    val isActive: Boolean get() = deviceUrl != null
}

