package com.rogger.xcast10.ui.screens

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:54
 */
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rogger.xcast10.ui.components.ConfirmDialog
import com.rogger.xcast10.ui.components.PlayPauseButton
import com.rogger.xcast10.ui.components.PlaybackProgressRow
import com.rogger.xcast10.ui.components.VolumeControlRow
import com.rogger.xcast10.viewmodel.CastUiState

@Composable
fun PlayerScreen(
    uiState: CastUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onCancelStreaming: () -> Unit
) {
    val playback = uiState.playback
    var showExitDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler { showExitDialog = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "A transmitir agora",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playback.videoTitle,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        PlaybackProgressRow(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            onSeek = onSeek
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            PlayPauseButton(isPlaying = playback.isPlaying, onToggle = onTogglePlayPause)
        }

        VolumeControlRow(
            volume = playback.volume,
            onVolumeDown = onVolumeDown,
            onVolumeUp = onVolumeUp
        )

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = { showCancelDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Spacer(Modifier.padding(start = 8.dp))
            Text("Cancelar transmissão")
        }
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = "Sair",
            message = "Deseja voltar? A transmissão será interrompida.",
            onConfirm = {
                showExitDialog = false
                onCancelStreaming()
            },
            onDismiss = { showExitDialog = false }
        )
    }

    if (showCancelDialog) {
        ConfirmDialog(
            title = "Cancelar transmissão",
            message = "Tem certeza que deseja cancelar a transmissão?",
            onConfirm = {
                showCancelDialog = false
                onCancelStreaming()
            },
            onDismiss = { showCancelDialog = false }
        )
    }
}
