package com.rogger.xcast10.data.model

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:13
 */
data class DLNADevice(
    val name: String,
    val location: String,
    val serviceUrl: String? = null,
    val renderingControlUrl: String? = null
)
